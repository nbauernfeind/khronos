package com.nefariouszhen.khronos.db.ram

import com.google.inject.Inject
import com.nefariouszhen.khronos.db.TimeSeriesDatabaseDAO
import com.nefariouszhen.khronos.{Time, TimeSeriesPoint}

import scala.collection.mutable

class InMemoryTimeSeriesDatabaseDAO @Inject()(config: InMemoryTSDBConfiguration) extends TimeSeriesDatabaseDAO {
  def isConnected: Boolean = true

  def write(id: Int, tm: Time, value: Double): Unit = {
    getTimeSeries(id, createIfMissing = true).foreach(_.write(tm, value))
  }

  def read(id: Int, fromTm: Time): Iterator[TimeSeriesPoint] = {
    getTimeSeries(id, createIfMissing = false).iterator.flatMap(_.read(fromTm))
  }

  private[this] def getTimeSeries(id: Int, createIfMissing: Boolean): Option[InMemoryStorage] = {
    this.synchronized {
      if (createIfMissing) {
        Some(tsIdx.getOrElseUpdate(id, new InMemoryStorage(config.numPointsPerSeries)))
      } else {
        tsIdx.get(id)
      }
    }
  }

  private[this] val tsIdx = mutable.HashMap[Int, InMemoryStorage]()
}
