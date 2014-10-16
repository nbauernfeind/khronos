package com.nefariouszhen.saturn.db.cassandra

import com.nefariouszhen.saturn.db.{DatabaseModule, TimeSeriesDatabase}

class CassandraDatabaseModule extends DatabaseModule {
  override def doConfigure(): Unit = {
    super.doConfigure()

    bind[CassandraTSDB].asEagerSingleton()
    bind[TimeSeriesDatabase].to[CassandraTSDB]
  }
}
