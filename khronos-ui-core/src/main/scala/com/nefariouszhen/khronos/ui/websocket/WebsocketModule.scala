package com.nefariouszhen.khronos.ui.websocket

import javax.ws.rs.Path

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.nefariouszhen.khronos.util.DropwizardPrivateModule
import io.dropwizard.setup.Environment
import org.atmosphere.cache.UUIDBroadcasterCache
import org.atmosphere.client.TrackMessageSizeInterceptor
import org.atmosphere.config.service.AtmosphereHandlerService
import org.atmosphere.cpr.{ApplicationConfig, AtmosphereResponse, AtmosphereServlet}
import org.atmosphere.handler.OnMessage
import org.atmosphere.interceptor.{AtmosphereResourceLifecycleInterceptor, BroadcastOnPostAtmosphereInterceptor, HeartbeatInterceptor}

case class Message(message: String, author: String, time: Long)

@Path("/")
@AtmosphereHandlerService(path = "/chat",
  broadcasterCache = classOf[UUIDBroadcasterCache],
  interceptors = Array(classOf[AtmosphereResourceLifecycleInterceptor],
    classOf[BroadcastOnPostAtmosphereInterceptor],
    classOf[TrackMessageSizeInterceptor],
    classOf[HeartbeatInterceptor]
  ))
class ChatService extends OnMessage[String] {
  private val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  override def onMessage(response: AtmosphereResponse, message: String): Unit = {
    response.write(mapper.writeValueAsString(mapper.readValue[Message](message)))
  }
}

class WebsocketModule extends DropwizardPrivateModule {
  override def doConfigure(): Unit = {
  }

  override def install(env: Environment): Unit = {
    val servlet = new AtmosphereServlet()
    servlet.framework().addInitParameter("com.sun.jersey.config.property.packages", "com.nefariouszhen.khronos.ui.websocket")
    servlet.framework().addInitParameter(ApplicationConfig.WEBSOCKET_CONTENT_TYPE, "application/json")
    servlet.framework().addInitParameter(ApplicationConfig.WEBSOCKET_SUPPORT, "true")

    val servletHolder = env.servlets().addServlet("Chat", servlet)
    servletHolder.addMapping("/chat/*")
  }
}
