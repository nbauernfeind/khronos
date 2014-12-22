package com.nefariouszhen.khronos.ui

import com.nefariouszhen.khronos.ui.home.HomeWidgetResource
import com.nefariouszhen.khronos.util.DropwizardPrivateModule
import io.dropwizard.setup.Environment

class UiModule extends DropwizardPrivateModule {
  def doConfigure(): Unit = {
    bind[HomeWidgetResource].asEagerSingleton()
    bind[WidgetRegistry].asEagerSingleton()

    expose[WidgetRegistry]
  }

  def install(env: Environment): Unit = {
    env.jersey().register(instance[HomeWidgetResource])
  }
}
