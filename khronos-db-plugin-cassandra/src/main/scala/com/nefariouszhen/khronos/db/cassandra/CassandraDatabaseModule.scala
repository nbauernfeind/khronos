package com.nefariouszhen.khronos.db.cassandra

import com.fasterxml.jackson.annotation.JsonTypeName
import com.nefariouszhen.khronos.db.{DatabaseConfiguration, DatabaseModule, TimeSeriesDatabaseDAO}

@JsonTypeName("cassandra")
class CassandraTSDBConfiguration extends DatabaseConfiguration {
  override def buildModule(): DatabaseModule = new CassandraDatabaseModule
}

class CassandraDatabaseModule extends DatabaseModule {
  override def doConfigure(): Unit = {
    super.doConfigure()

    bind[CassandraTimeSeriesDatabaseDAO].asEagerSingleton()
    bind[TimeSeriesDatabaseDAO].to[CassandraTimeSeriesDatabaseDAO]
  }
}
