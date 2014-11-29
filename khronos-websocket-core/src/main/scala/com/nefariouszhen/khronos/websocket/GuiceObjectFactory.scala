package com.nefariouszhen.khronos.websocket

import com.google.inject.{ConfigurationException, Inject, Injector}
import org.atmosphere.cpr.{AtmosphereFramework, AtmosphereObjectFactory}

class GuiceObjectFactory @Inject()(injector: Injector) extends AtmosphereObjectFactory {
  override def newClassInstance[T, U <: T](framework: AtmosphereFramework, classType: Class[T], typ: Class[U]): T = {
    // Try to pull from Guice; if non-existent, then create directly.
    try {
      injector.getInstance(typ)
    } catch {
      case e: ConfigurationException => typ.newInstance()
    }
  }
}
