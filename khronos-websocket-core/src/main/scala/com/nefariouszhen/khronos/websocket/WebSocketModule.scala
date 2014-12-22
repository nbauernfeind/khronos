package com.nefariouszhen.khronos.websocket

import java.util.concurrent.ExecutorService

import com.google.inject.{Provides, Singleton}
import com.nefariouszhen.khronos.util.{ManagedExecutor, Executors, DropwizardPrivateModule}
import io.dropwizard.setup.Environment
import org.atmosphere.cpr.{ApplicationConfig, AtmosphereObjectFactory, AtmosphereServlet, BroadcasterFactory}

class WebSocketModule extends DropwizardPrivateModule {
  override def doConfigure(): Unit = {
    bind[GuiceObjectFactory].asEagerSingleton()
    bind[AtmosphereObjectFactory].to[GuiceObjectFactory]
    bind[ExecutorService].toInstance(Executors.newCachedThreadPool("websocket"))

    bindFactory[WebSocketState, WebSocketState.Factory]()
    bind[WebSocketManager].asEagerSingleton()

    expose[WebSocketManager]
  }

  @Provides
  @Singleton
  def newAtmosphereServlet(oF: AtmosphereObjectFactory): AtmosphereServlet = {
    val servlet = new AtmosphereServlet()
    servlet.framework().addInitParameter(ApplicationConfig.WEBSOCKET_CONTENT_TYPE, "application/json")
    servlet.framework().addInitParameter(ApplicationConfig.WEBSOCKET_SUPPORT, "true")
    servlet.framework().objectFactory(oF)
    servlet
  }

  @Provides
  @Singleton
  def getBroadcasterFactory(atmosphere: AtmosphereServlet): BroadcasterFactory = {
    atmosphere.framework().getBroadcasterFactory
  }

  override def install(env: Environment): Unit = {
    val servletHolder = env.servlets().addServlet("websocket", instance[AtmosphereServlet])
    servletHolder.addMapping("/ws/*")

    env.lifecycle().manage(new ManagedExecutor(instance[ExecutorService]))
  }
}
