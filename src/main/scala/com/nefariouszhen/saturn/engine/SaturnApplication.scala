package com.nefariouszhen.saturn.engine

import com.bazaarvoice.dropwizard.assets.{AssetsBundleConfiguration, AssetsConfiguration, ConfiguredAssetsBundle}
import com.bazaarvoice.dropwizard.redirect.{RedirectBundle, UriRedirect}
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.inject.Guice
import com.massrelevance.dropwizard.ScalaApplication
import com.massrelevance.dropwizard.bundles.ScalaBundle
import com.nefariouszhen.saturn.db.cassandra.CassandraDatabaseModule
import com.nefariouszhen.saturn.util.DropwizardModule
import io.dropwizard.Configuration
import io.dropwizard.setup.{Bootstrap, Environment}

class SaturnConfiguration extends Configuration with AssetsBundleConfiguration {
  @JsonProperty
  val assets = new AssetsConfiguration

  override def getAssetsConfiguration = assets
}

abstract class SaturnApplicationBase[T <: SaturnConfiguration] extends ScalaApplication[T] {
  def createModules(configuration: T): Seq[DropwizardModule[_]]

  def initialize(bootstrap: Bootstrap[T]): Unit = {
    bootstrap.addBundle(new ScalaBundle)
    bootstrap.addBundle(new ConfiguredAssetsBundle("/assets/", "/dashboard/"))

    bootstrap.addBundle(new RedirectBundle(
      new UriRedirect("/", "/dashboard/"),
      new UriRedirect("/index.htm", "/dashboard/"),
      new UriRedirect("/index.html", "/dashboard/")
    ))
  }

  def run(configuration: T, environment: Environment): Unit = {
    val modules = createModules(configuration)
    Guice.createInjector(modules: _*)
    modules.foreach(_.install(environment))
  }
}

object SaturnApplication extends SaturnApplicationBase[SaturnConfiguration] {
  def createModules(configuration: SaturnConfiguration): Seq[DropwizardModule[_]] = Seq(
    new CassandraDatabaseModule
  )
}
