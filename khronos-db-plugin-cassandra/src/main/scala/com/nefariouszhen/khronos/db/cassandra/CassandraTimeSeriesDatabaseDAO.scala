package com.nefariouszhen.khronos.db.cassandra

import com.nefariouszhen.khronos.db.TimeSeriesDatabaseDAO
import com.nefariouszhen.khronos.{KeyValuePair, Time, TimeSeriesPoint}

class CassandraTimeSeriesDatabaseDAO extends TimeSeriesDatabaseDAO {
  override def isConnected: Boolean = ???

  override def write(keys: Seq[KeyValuePair], tm: Time, value: Double): Unit = ???

  override def read(keys: Seq[KeyValuePair], fromTm: Time): Iterator[TimeSeriesPoint] = ???
}
