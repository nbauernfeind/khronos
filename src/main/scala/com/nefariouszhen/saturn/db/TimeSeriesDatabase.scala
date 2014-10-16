package com.nefariouszhen.saturn.db

object TimeSeriesDatabase {
  trait Status {
    def isConnected: Boolean
  }
}

trait TimeSeriesDatabase {
  def status: TimeSeriesDatabase.Status
}
