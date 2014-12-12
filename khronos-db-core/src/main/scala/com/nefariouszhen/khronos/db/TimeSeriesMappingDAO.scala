package com.nefariouszhen.khronos.db

import java.io.Closeable

import com.nefariouszhen.khronos.ExactTag

case class TimeSeriesMapping(id: Int, tags: Seq[ExactTag]) extends Ordered[TimeSeriesMapping] {
  override def compare(that: TimeSeriesMapping): Int = id.compare(that.id)
}

trait TimeSeriesMappingDAO {
  def isConnected: Boolean

  /**
   * For a given set of key-value pairs fetch the timeseries id.
   * @param tags the exact tags that represent the timeseries in question.
   * @return the time series id
   */
  def getIdOrCreate(tags: Seq[ExactTag]): Int

  /**
   * For a given id fetch the key-value pairs that represent the time series in question.
   * @param id the time series id
   * @return the key-value pairs that represent the time series in question.
   */
  def getKeys(id: Int): Seq[ExactTag]

  /**
   * Fetch all known time series.
   * @return an iterable of all known time series.
   */
  def timeSeries(): Iterable[TimeSeriesMapping]

  /**
   * Subscribe to all time series.
   * @return a closeable reference to this subscription
   */
  def subscribe(callback: TimeSeriesMapping => Unit): Closeable
}
