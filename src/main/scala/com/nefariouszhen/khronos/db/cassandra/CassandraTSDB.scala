package com.nefariouszhen.khronos.db.cassandra

import com.nefariouszhen.khronos.db.TimeSeriesDatabase

class CassandraTSDB extends TimeSeriesDatabase {
  override def status: TimeSeriesDatabase.Status = new Status

  private[this] class Status extends TimeSeriesDatabase.Status {
    val isConnected = false
  }
}
