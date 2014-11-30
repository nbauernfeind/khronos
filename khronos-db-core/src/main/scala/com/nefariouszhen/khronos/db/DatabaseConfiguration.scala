package com.nefariouszhen.khronos.db

import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.dropwizard.jackson.Discoverable

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
trait DatabaseConfiguration extends Discoverable {
  def buildModule(): DatabaseModule
}
