package com.nefariouszhen.saturn.db

import com.bazaarvoice.ostrich.dropwizard.healthcheck.CachingHealthCheck
import com.nefariouszhen.saturn.util.DropwizardPrivateModule
import io.dropwizard.setup.Environment
import net.codingwell.scalaguice.InjectorExtensions._

abstract class DatabaseModule extends DropwizardPrivateModule {
  def doConfigure(): Unit = {
    bind[DatabaseResource].asEagerSingleton()
    bind[DatabaseHealthCheck].asEagerSingleton()
    expose[TimeSeriesDatabase]
  }

  def install(env: Environment): Unit = {
    env.jersey().register(injector.instance[DatabaseResource])
    env.healthChecks().register("tsdb", new CachingHealthCheck(injector.instance[DatabaseHealthCheck]))
  }
}


