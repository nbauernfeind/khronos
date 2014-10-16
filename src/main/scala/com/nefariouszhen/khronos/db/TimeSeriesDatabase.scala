package com.nefariouszhen.khronos.db

object TimeSeriesDatabase {
  trait Status {
    def isConnected: Boolean
  }
}

trait TimeSeriesDatabase {
  def status: TimeSeriesDatabase.Status
}
