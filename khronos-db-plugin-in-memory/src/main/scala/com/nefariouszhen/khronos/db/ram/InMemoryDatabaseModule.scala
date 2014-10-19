package com.nefariouszhen.khronos.db.ram

import com.fasterxml.jackson.annotation.JsonTypeName
import com.nefariouszhen.khronos.db.{DatabaseConfiguration, DatabaseModule, TimeSeriesDatabase}
import com.nefariouszhen.khronos.util.DropwizardModule

@JsonTypeName("in-memory")
class InMemoryTSDBConfiguration extends DatabaseConfiguration {
  override def buildModule(): DropwizardModule[_] = new InMemoryDatabaseModule
}

class InMemoryDatabaseModule extends DatabaseModule {
  override def doConfigure(): Unit = {
    super.doConfigure()

    bind[InMemoryTSDB].asEagerSingleton()
    bind[TimeSeriesDatabase].to[InMemoryTSDB]
  }
}
