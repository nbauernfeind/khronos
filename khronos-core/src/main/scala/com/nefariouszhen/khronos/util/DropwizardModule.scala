package com.nefariouszhen.khronos.util

import com.google.inject.assistedinject.FactoryModuleBuilder
import com.google.inject.{Binder, Injector, Module, PrivateBinder, Provider}
import io.dropwizard.setup.Environment
import net.codingwell.scalaguice.{InternalModule, ScalaModule, ScalaPrivateModule, typeLiteral}

trait DropwizardModule[B <: Binder] extends Module {
  self: InternalModule[B] =>

  private[this] var injectorProvider: Provider[Injector] = null

  final def configure() {
    binderAccess.requireExplicitBindings()
    injectorProvider = getProvider[Injector]
    doConfigure()
  }

  protected[this] def injector = injectorProvider.get()

  def doConfigure()
  def install(env: Environment)

  import net.codingwell.scalaguice.InjectorExtensions._
  def instance[T: Manifest] = injector.instance[T]
}

abstract class DropwizardPublicModule extends ScalaModule with DropwizardModule[Binder] {
  protected[this] def bindFactory[C: Manifest, F: Manifest]() {
    bindFactory[C, C, F]()
  }

  protected[this] def bindFactory[I: Manifest, C <: I : Manifest, F: Manifest]() {
    install(new FactoryModuleBuilder()
      .implement(typeLiteral[I], typeLiteral[C])
      .build(typeLiteral[F]))
  }
}

abstract class DropwizardPrivateModule extends ScalaPrivateModule with DropwizardModule[PrivateBinder] {
  protected[this] def bindFactory[C: Manifest, F: Manifest]() {
    bindFactory[C, C, F]()
  }

  protected[this] def bindFactory[I: Manifest, C <: I : Manifest, F: Manifest]() {
    install(new FactoryModuleBuilder()
      .implement(typeLiteral[I], typeLiteral[C])
      .build(typeLiteral[F]))
  }
}
