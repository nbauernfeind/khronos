package com.nefariouszhen.khronos.websocket

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.{JsonProperty, JsonTypeInfo, JsonTypeName}
import com.fasterxml.jackson.databind.{JsonMappingException, ObjectMapper}
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.google.inject.Inject
import com.google.inject.assistedinject.Assisted
import io.dropwizard.jackson.Discoverable
import org.atmosphere.client.TrackMessageSizeInterceptor
import org.atmosphere.config.service.WebSocketHandlerService
import org.atmosphere.cpr.AtmosphereResource
import org.atmosphere.interceptor.{AtmosphereResourceLifecycleInterceptor, HeartbeatInterceptor, SuspendTrackerInterceptor}
import org.atmosphere.jersey.util.JerseySimpleBroadcaster
import org.atmosphere.websocket.{WebSocket, WebSocketHandlerAdapter}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.collection.mutable
import scala.reflect.ClassTag

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include=As.PROPERTY, property = "type")
trait WebSocketRequest extends Discoverable {
  val callbackId: Int = -1
  @JsonProperty(required = false)
  val recurring: Boolean = false
}

case class WebSocketResponse[T](callbackId: Int, data: T)

@JsonTypeName("cancel")
class CancelRequest extends WebSocketRequest

object WebSocketState {
  trait Factory {
    def newState(r: AtmosphereResource): WebSocketState
  }
}

class WebSocketState @Inject()(@Assisted r: AtmosphereResource, mapper: ObjectMapper) extends Closeable {
  private[this] val closed = new AtomicBoolean(false)
  private[this] val closeables = mutable.HashMap[Int, Closeable]()
  private[this] val scalaMapper = new ObjectMapper with ScalaObjectMapper

  def write[T: Manifest](cid: Int, t: T): Unit = this.synchronized {
    if (!closed.get) {
      val typ = scalaMapper.constructType[WebSocketResponse[T]]
      val typMapper = mapper.writerWithType(typ)
      r.write(typMapper.writeValueAsString(WebSocketResponse(cid, t)))
    }
  }

  def manage(cid: Int, state: Closeable): Unit = this.synchronized {
    if (!closed.get) {
      closeables.put(cid, state).map(_.close())
    } else {
      state.close()
    }
  }

  def close(cid: Int) = this.synchronized {
    closeables.remove(cid).foreach(_.close)
  }

  def close(): Unit = this.synchronized {
    closed.set(true)
    closeables.values.foreach(_.close)
    closeables.clear()
  }

  def newWriter(r: WebSocketRequest): WebSocketWriter = new WebSocketWriter(this, r.callbackId, r.recurring)
}

class WebSocketWriter(private[websocket] val socket: WebSocketState, cid: Int, recurring: Boolean) {
  /**
   * When the WebSocket closes `state` when the WebSocket client either, 1) requests to cancel the request,
   * 2) disconnects, or 3) the request is complete (i.e. non-recurring). It may close before returning.
   *
   * @param state The object to close.
   */
  def manage(state: Closeable): Unit = {
    if (!recurring) state.close() else socket.manage(cid, state)
  }

  /**
   * Write `t` as json to the WebSocket while respecting the callback id.
   */
  def write[T: Manifest](t: T): Unit = {
    socket.write(cid, t)
  }
}

@WebSocketHandlerService(
  path = "/ws",
  broadcaster = classOf[JerseySimpleBroadcaster],
  interceptors = Array(
    classOf[AtmosphereResourceLifecycleInterceptor],
    classOf[TrackMessageSizeInterceptor],
    classOf[SuspendTrackerInterceptor],
    classOf[HeartbeatInterceptor]
  )
)
class WebSocketManager @Inject()(mapper: ObjectMapper, factory: WebSocketState.Factory)
  extends WebSocketHandlerAdapter {
  type Callback[T] = Function2[WebSocketWriter, T, Unit]

  private[this] val LOG = LoggerFactory.getLogger(this.getClass)
  private[this] val callbackMap = mutable.HashMap[Class[_], Callback[_]]()
  private[this] val uuidToState = mutable.HashMap[String, WebSocketState]()

  // A cancel request is just a special callback.
  registerCallback[CancelRequest](cancelRequest)

  /**
   * To add WebSocket based RPC's register your callback with this method. If multiple callbacks apply, the only the
   * most specific is invoked. Interfaces / Mix-Ins are not considered when finding a suitable callback.
   *
   * @param callback A function to be invoked when the UI makes such a request.
   * @tparam T       The type that represents this specific mapping.
   * @throws IllegalArgumentException If a callback has already been registered for this request type.
   */
  def registerCallback[T <: WebSocketRequest : ClassTag](callback: Function2[WebSocketWriter, T, Unit]): Unit = {
    import scala.reflect._
    val preExisting = callbackMap.put(classTag[T].runtimeClass, callback).isDefined
    if (preExisting) {
      LOG.error("Pre-existing callback registered for {}")
      throw new IllegalArgumentException(s"Callback already registered for ${classTag[T].runtimeClass}.")
    }
  }

  override def onOpen(webSocket: WebSocket): Unit = this.synchronized {
    uuidToState.getOrElseUpdate(webSocket.resource.uuid, factory.newState(webSocket.resource))
  }

  override def onClose(webSocket: WebSocket): Unit = this.synchronized {
    uuidToState.remove(webSocket.resource.uuid).foreach(_.close())
  }

  override def onTextMessage(webSocket: WebSocket, message: String): Unit = this.synchronized {
    val state = uuidToState.getOrElse(webSocket.resource.uuid, return)
    try {
      val m = mapper.readValue(message, classOf[WebSocketRequest])

      def invoke[T](callback: Callback[T]): Unit = {
        callback(state.newWriter(m), m.asInstanceOf[T])
      }

      resolve(m.getClass) match {
        case Some(callback) => invoke(callback)
        case None => LOG.warn("No callback found for request with type: {}", m.getClass)
      }
    } catch {
      case e: JsonMappingException => LOG.debug("Could not parse message.", e)
      case e: Exception => LOG.warn("Unexpected error handling client request.", e)
    }
  }

  @tailrec
  private[this] def resolve(cls: Class[_]): Option[Callback[_]] = {
    val cb = callbackMap.get(cls)
    if (cb.isDefined) cb else if (cls != classOf[Object]) resolve(cls.getSuperclass) else None
  }

  private[this] def cancelRequest(writer: WebSocketWriter, cancel: CancelRequest): Unit = {
    writer.socket.close(cancel.callbackId)
  }
}
