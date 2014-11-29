package com.nefariouszhen.khronos

import com.fasterxml.jackson.databind.ObjectMapper
import com.nefariouszhen.khronos.db.ram.InMemoryTSDBConfiguration
import com.nefariouszhen.khronos.engine.{KhronosApplicationBase, KhronosConfiguration}
import com.nefariouszhen.khronos.ui.UiModule
import com.nefariouszhen.khronos.util.{DropwizardModule, DropwizardPublicModule}
import com.nefariouszhen.khronos.websocket.WebSocketModule
import io.dropwizard.setup.Environment

class UtilModule(mapper: ObjectMapper) extends DropwizardPublicModule {
  override def doConfigure(): Unit = {
    bind[ObjectMapper].toInstance(mapper)
  }

  override def install(env: Environment): Unit = {

  }
}

object KhronosApplication extends KhronosApplicationBase[KhronosConfiguration] {
  def createModules(configuration: KhronosConfiguration, environment: Environment): Seq[DropwizardModule[_]] = {
    if (configuration.db == null) {
      configuration.db = new InMemoryTSDBConfiguration
    }

    Seq(
      configuration.db.buildModule(),
      new UiModule,
      new WebSocketModule,
      new UtilModule(environment.getObjectMapper)
    )
  }
}