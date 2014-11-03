package com.nefariouszhen.khronos

import com.nefariouszhen.khronos.db.ram.InMemoryTSDBConfiguration
import com.nefariouszhen.khronos.engine.{KhronosApplicationBase, KhronosConfiguration}
import com.nefariouszhen.khronos.ui.UiModule
import com.nefariouszhen.khronos.ui.websocket.WebsocketModule
import com.nefariouszhen.khronos.util.DropwizardModule

object KhronosApplication extends KhronosApplicationBase[KhronosConfiguration] {
  def createModules(configuration: KhronosConfiguration): Seq[DropwizardModule[_]] = {
    if (configuration.db == null) {
      configuration.db = new InMemoryTSDBConfiguration
    }

    Seq(
      configuration.db.buildModule(),
      new UiModule,
      new WebsocketModule
    )
  }
}