package com.nefariouszhen.khronos.db.ram

import java.util.concurrent.atomic.AtomicInteger

import com.google.inject.Inject
import com.nefariouszhen.khronos.db.TimeSeriesDatabaseDAO
import com.nefariouszhen.khronos.{KeyValuePair, Time, TimeSeriesPoint}

import scala.collection.mutable

class InMemoryTimeSeriesDatabaseDAO @Inject()(config: InMemoryTSDBConfiguration) extends TimeSeriesDatabaseDAO {
  def isConnected: Boolean = true

  def write(keys: Seq[KeyValuePair], tm: Time, value: Double): Unit = {
    getTimeSeries(keys, createIfMissing = true).foreach(_.write(tm, value))
  }

  def read(keys: Seq[KeyValuePair], fromTm: Time): Iterator[TimeSeriesPoint] = {
    getTimeSeries(keys, createIfMissing = false).iterator.flatMap(_.read(fromTm))
  }

  /**
   * List all timeseries.
   */
  override def timeseries(): Iterable[Seq[KeyValuePair]] = idMap.keys

  private[this] def getTimeSeries(keys: Seq[KeyValuePair], createIfMissing: Boolean): Option[InMemoryStorage] = {
    this.synchronized {
      val k = keys.sorted
      val i = if (createIfMissing) Some(idMap.getOrElseUpdate(k, fetchNextId())) else idMap.get(k)
      i.map(i => tsIdx(i))
    }
  }

  private[this] def fetchNextId(): Int = tsIdx.synchronized {
    val idx = nextId.getAndIncrement
    tsIdx += new InMemoryStorage(config.numPointsPerSeries)
    idx
  }

  private[this] val nextId = new AtomicInteger()
  private[this] val idMap = mutable.HashMap[Seq[KeyValuePair], Int]()
  private[this] val tsIdx = mutable.ArrayBuffer[InMemoryStorage]()
}
