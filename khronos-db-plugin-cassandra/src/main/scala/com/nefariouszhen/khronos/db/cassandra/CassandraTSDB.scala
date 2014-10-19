package com.nefariouszhen.khronos.db.cassandra

import com.nefariouszhen.khronos.db.TimeSeriesDatabase

import scala.util.Random

class CassandraTSDB extends TimeSeriesDatabase {
  override def status: TimeSeriesDatabase.Status = new Status

  private[this] class Status extends TimeSeriesDatabase.Status {
    val isConnected = false
    val numSeriesActive: Long = Random.nextInt(100000)
    val numStreamsActive: Long = Random.nextInt(1000)
    val numInPointsLastHour: Long = Math.abs(Random.nextLong()) % 10000000000L
    val numOutPointsLastHour: Long = Random.nextInt(100000)
  }
}
