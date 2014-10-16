package com.nefariouszhen.saturn.db.cassandra

import com.nefariouszhen.saturn.db.TimeSeriesDatabase

class CassandraTSDB extends TimeSeriesDatabase {
  override def status: TimeSeriesDatabase.Status = new Status

  private[this] class Status extends TimeSeriesDatabase.Status {
    val isConnected = false
  }
}
