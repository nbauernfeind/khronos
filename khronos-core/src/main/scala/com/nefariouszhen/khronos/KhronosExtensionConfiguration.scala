package com.nefariouszhen.khronos

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.nefariouszhen.khronos.util.DropwizardModule
import io.dropwizard.jackson.Discoverable

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.PROPERTY, property = "type")
trait KhronosExtensionConfiguration extends Discoverable {
  val additionalCSS: Seq[String] = Seq()
  val additionalJavascript: Seq[String] = Seq()
  def buildModule(): DropwizardModule[_]
}