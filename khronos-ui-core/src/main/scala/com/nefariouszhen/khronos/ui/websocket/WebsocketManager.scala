package com.nefariouszhen.khronos.ui.websocket

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

import com.fasterxml.jackson.annotation.{JsonProperty, JsonTypeInfo, JsonTypeName}
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import com.nefariouszhen.khronos.db.Multiplexus
import com.nefariouszhen.khronos.{KeyValuePair, TimeSeriesPoint}
import io.dropwizard.jackson.Discoverable
import org.atmosphere.client.TrackMessageSizeInterceptor
import org.atmosphere.config.service.WebSocketHandlerService
import org.atmosphere.interceptor.{AtmosphereResourceLifecycleInterceptor, HeartbeatInterceptor, SuspendTrackerInterceptor}
import org.atmosphere.jersey.util.JerseySimpleBroadcaster
import org.atmosphere.websocket.{WebSocket, WebSocketHandlerAdapter}

import scala.collection.mutable

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
trait WebSocketRequest extends Discoverable {
  val callbackId: Int = -1
  @JsonProperty(required = false)
  val recurring: Boolean = false
  @JsonProperty(required = false)
  val debug: Boolean = false
}

@JsonTypeName("metric")
case class WSRequestSubscribeTS(query: Seq[KeyValuePair]) extends WebSocketRequest

trait WebSocketResponse[T] {
  def callbackId: Int
  def data: T
}

case class WSResponseTSP(callbackId: Int, data: TimeSeriesPoint) extends WebSocketResponse[TimeSeriesPoint]

class WebSocketState extends Closeable {
  private[this] val closed = new AtomicBoolean(false)
  private[this] val closeables = mutable.ArrayBuffer[Closeable]()

  def add(closeable: Closeable): Unit = this.synchronized {
    if (!closed.get()) {
      closeables += closeable
    } else {
      closeable.close()
    }
  }

  def close(): Unit = this.synchronized {
    closed.set(true)
    closeables.foreach(_.close)
    closeables.clear()
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
// TODO: Make WebSocketRequest plugin-able. Should not inject tsdb here.
class WebsocketManager @Inject()(tsdb: Multiplexus, mapper: ObjectMapper) extends WebSocketHandlerAdapter {
  private[this] val uuidToState = mutable.HashMap[String, WebSocketState]()

  override def onOpen(webSocket: WebSocket): Unit = this.synchronized {
    uuidToState.getOrElseUpdate(webSocket.resource.uuid(), new WebSocketState)
  }

  override def onClose(webSocket: WebSocket): Unit = this.synchronized {
    uuidToState.remove(webSocket.resource.uuid()).foreach(_.close())
  }

  override def onTextMessage(webSocket: WebSocket, message: String): Unit = {
    val r = webSocket.resource()
    val m = mapper.readValue(message, classOf[WebSocketRequest])
    if (m.debug) println(s"Message: ${r.uuid()} $message")

    m match {
      case m: WSRequestSubscribeTS =>
        for (state <- this.synchronized {
          uuidToState.get(r.uuid())
        }) {
          state.add(tsdb.subscribe(Multiplexus.RawQuery(m.query), tsp => {
            if (m.debug) println(s"Writing $tsp to ${r.uuid}")
            r.getResponse.write(mapper.writeValueAsString(WSResponseTSP(m.callbackId, tsp)))
          }))
        }
      case _ => if (m.debug) println(s"Ignoring message: $message")
    }
  }
}
