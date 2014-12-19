package com.nefariouszhen.khronos.db

import com.nefariouszhen.khronos.{Time, TimeSeriesPoint}

trait TimeSeriesDatabaseDAO {
  def isConnected: Boolean

  /**
   * Write a timeseries data point for stream identified by `keys`.
   *
   * @param id    The id for the time series.
   * @param tm    The time for the data point.
   * @param value The value for the data point.
   */
  def write(id: Int, tm: Time, value: Double): Unit

  /**
   * Obtain an iterator that reads data from timeseries identified by `keys` and earliest datapoint at `fromTime`.
   *
   * @param id     The id of the time series.
   * @param fromTm The earliest data point to retrieve.
   * @param endTm  Optional parameter of when to stop reading.
   * @return       An iterator of data points.
   */
  def read(id: Int, fromTm: Time, endTm: Option[Time] = None): Iterator[TimeSeriesPoint]
}
