package com.nefariouszhen.khronos.db.cassandra

import com.nefariouszhen.khronos.db.TimeSeriesDatabaseDAO
import com.nefariouszhen.khronos.{KeyValuePair, Time, TimeSeriesPoint}

class CassandraTimeSeriesDatabaseDAO extends TimeSeriesDatabaseDAO {
  override def isConnected: Boolean = ???

  override def write(id: Int, tm: Time, value: Double): Unit = ???

  override def read(id: Int, fromTm: Time): Iterator[TimeSeriesPoint] = ???
}
