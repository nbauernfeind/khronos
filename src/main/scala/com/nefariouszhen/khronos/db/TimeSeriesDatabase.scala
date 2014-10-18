package com.nefariouszhen.khronos.db

object TimeSeriesDatabase {
  trait Status {
    def isConnected: Boolean
    def numSeriesActive: Long
    def numStreamsActive: Long
    def numInPointsLastHour: Long
    def numOutPointsLastHour: Long
  }
}

trait TimeSeriesDatabase {
  def status: TimeSeriesDatabase.Status
}
