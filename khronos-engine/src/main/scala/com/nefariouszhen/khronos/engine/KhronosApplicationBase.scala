package com.nefariouszhen.khronos.engine

import com.bazaarvoice.dropwizard.assets.{AssetsBundleConfiguration, AssetsConfiguration, ConfiguredAssetsBundle}
import com.bazaarvoice.dropwizard.redirect.{RedirectBundle, UriRedirect}
import com.bazaarvoice.dropwizard.webjars.WebJarBundle
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.inject.Guice
import com.massrelevance.dropwizard.ScalaApplication
import com.massrelevance.dropwizard.bundles.ScalaBundle
import com.nefariouszhen.khronos.KhronosExtensionConfiguration
import com.nefariouszhen.khronos.db.DatabaseConfiguration
import com.nefariouszhen.khronos.util.DropwizardModule
import io.dropwizard.Configuration
import io.dropwizard.setup.{Bootstrap, Environment}

class KhronosConfiguration extends Configuration with AssetsBundleConfiguration {
  @JsonProperty
  val assets = new AssetsConfiguration
  override def getAssetsConfiguration = assets

  @JsonProperty
  var db: DatabaseConfiguration = null

  @JsonProperty
  val extensions = Array[KhronosExtensionConfiguration]()
}

abstract class KhronosApplicationBase[T <: KhronosConfiguration] extends ScalaApplication[T] {
  def createModules(configuration: T, envrionment: Environment): Seq[DropwizardModule[_]]

  def initialize(bootstrap: Bootstrap[T]): Unit = {
    bootstrap.addBundle(new ScalaBundle)
    bootstrap.addBundle(new ConfiguredAssetsBundle("/assets/", "/khronos/"))

    bootstrap.addBundle(new RedirectBundle(
      new UriRedirect("/", "/khronos/"),
      new UriRedirect("/favicon.ico", "/khronos/favicon.ico"),
      new UriRedirect("/index.htm", "/khronos/"),
      new UriRedirect("/index.html", "/khronos/"),
      new UriRedirect("/khronos/index.htm", "/khronos/"),
      new UriRedirect("/khronos/index.html", "/khronos/")
    ))

    bootstrap.addBundle(new WebJarBundle())
  }

  def run(configuration: T, environment: Environment): Unit = {
    val modules = createModules(configuration, environment)
    Guice.createInjector(modules: _*)
    modules.foreach(_.install(environment))
  }
}
