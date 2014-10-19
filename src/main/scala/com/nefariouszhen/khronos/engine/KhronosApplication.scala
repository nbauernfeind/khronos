package com.nefariouszhen.khronos.engine

import com.bazaarvoice.dropwizard.assets.{AssetsBundleConfiguration, AssetsConfiguration, ConfiguredAssetsBundle}
import com.bazaarvoice.dropwizard.redirect.{RedirectBundle, UriRedirect}
import com.bazaarvoice.dropwizard.webjars.WebJarBundle
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.inject.Guice
import com.massrelevance.dropwizard.ScalaApplication
import com.massrelevance.dropwizard.bundles.ScalaBundle
import com.nefariouszhen.khronos.db.DatabaseConfiguration
import com.nefariouszhen.khronos.db.cassandra.CassandraDatabaseModule
import com.nefariouszhen.khronos.db.ram.InMemoryTSDBConfiguration
import com.nefariouszhen.khronos.ui.UiModule
import com.nefariouszhen.khronos.util.DropwizardModule
import io.dropwizard.Configuration
import io.dropwizard.setup.{Bootstrap, Environment}

class KhronosConfiguration extends Configuration with AssetsBundleConfiguration {
  @JsonProperty
  var assets = new AssetsConfiguration
  override def getAssetsConfiguration = assets

  @JsonProperty
  var db: DatabaseConfiguration = new InMemoryTSDBConfiguration
}

abstract class KhronosApplicationBase[T <: KhronosConfiguration] extends ScalaApplication[T] {
  def createModules(configuration: T): Seq[DropwizardModule[_]]

  def initialize(bootstrap: Bootstrap[T]): Unit = {
    bootstrap.addBundle(new ScalaBundle)
    bootstrap.addBundle(new ConfiguredAssetsBundle("/assets/", "/dashboard/"))

    bootstrap.addBundle(new RedirectBundle(
      new UriRedirect("/", "/dashboard/"),
      new UriRedirect("/index.htm", "/dashboard/"),
      new UriRedirect("/index.html", "/dashboard/")
    ))

    bootstrap.addBundle(new WebJarBundle())
  }

  def run(configuration: T, environment: Environment): Unit = {
    val modules = createModules(configuration)
    Guice.createInjector(modules: _*)
    modules.foreach(_.install(environment))
  }
}

object KhronosApplication extends KhronosApplicationBase[KhronosConfiguration] {
  def createModules(configuration: KhronosConfiguration): Seq[DropwizardModule[_]] = Seq(
    configuration.db.buildModule(),
    new UiModule
  )
}
