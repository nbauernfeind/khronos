package com.nefariouszhen.khronos.db.cassandra

import com.fasterxml.jackson.annotation.JsonTypeName
import com.nefariouszhen.khronos.db.{DatabaseConfiguration, DatabaseModule, TimeSeriesDatabase}
import com.nefariouszhen.khronos.util.DropwizardModule

@JsonTypeName("cassandra")
class CassandraTSDBConfiguration extends DatabaseConfiguration {
  override def buildModule(): DropwizardModule[_] = new CassandraDatabaseModule
}

class CassandraDatabaseModule extends DatabaseModule {
  override def doConfigure(): Unit = {
    super.doConfigure()

    bind[CassandraTSDB].asEagerSingleton()
    bind[TimeSeriesDatabase].to[CassandraTSDB]
  }
}
