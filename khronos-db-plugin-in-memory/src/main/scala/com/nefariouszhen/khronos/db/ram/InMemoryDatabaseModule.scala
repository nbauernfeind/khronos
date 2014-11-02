package com.nefariouszhen.khronos.db.ram

import com.fasterxml.jackson.annotation.{JsonProperty, JsonTypeName}
import com.nefariouszhen.khronos.db.{DatabaseConfiguration, DatabaseModule, TimeSeriesDatabaseDAO}
import com.nefariouszhen.khronos.util.DropwizardModule

@JsonTypeName("in-memory")
class InMemoryTSDBConfiguration extends DatabaseConfiguration {
  override def buildModule(): DropwizardModule[_] = new InMemoryDatabaseModule(this)

  @JsonProperty
  var numPointsPerSeries = Math.ceil((7 * 24 * 60) / 1024).toInt * 1024 // 1 week at 1pt/min.
}

class InMemoryDatabaseModule(config: InMemoryTSDBConfiguration) extends DatabaseModule {
  override def doConfigure(): Unit = {
    super.doConfigure()

    bind[InMemoryTSDBConfiguration].toInstance(config)
    bind[InMemoryTimeSeriesDatabaseDAO].asEagerSingleton()
    bind[TimeSeriesDatabaseDAO].to[InMemoryTimeSeriesDatabaseDAO]
  }
}
