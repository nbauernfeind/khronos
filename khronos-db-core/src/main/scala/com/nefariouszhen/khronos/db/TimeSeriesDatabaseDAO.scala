package com.nefariouszhen.khronos.db

import com.nefariouszhen.khronos.{KeyValuePair, Time, TimeSeriesPoint}

trait TimeSeriesDatabaseDAO {
  def isConnected: Boolean

  /**
   * Write a timeseries data point for stream identified by `keys`.
   *
   * @param keys  A sequence of key value pairs identifying this stream; order matters.
   * @param tm    The time for the data point.
   * @param value The value for the data point.
   */
  def write(keys: Seq[KeyValuePair], tm: Time, value: Double): Unit

  /**
   * Obtain an iterator that reads data from timeseries identified by `keys` and earliest datapoint at `fromTime`.
   *
   * @param keys   A sequence of key value pairs identifying this stream; order matters.
   * @param fromTm The earliest data point to retrieve.
   * @return       An iterator of data points.
   */
  def read(keys: Seq[KeyValuePair], fromTm: Time): Iterator[TimeSeriesPoint]
}
