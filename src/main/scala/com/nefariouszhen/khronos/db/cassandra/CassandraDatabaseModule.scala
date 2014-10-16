package com.nefariouszhen.khronos.db.cassandra

import com.nefariouszhen.khronos.db.{DatabaseModule, TimeSeriesDatabase}

class CassandraDatabaseModule extends DatabaseModule {
  override def doConfigure(): Unit = {
    super.doConfigure()

    bind[CassandraTSDB].asEagerSingleton()
    bind[TimeSeriesDatabase].to[CassandraTSDB]
  }
}
