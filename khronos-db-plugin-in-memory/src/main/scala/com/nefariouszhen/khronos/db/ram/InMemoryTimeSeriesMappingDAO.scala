package com.nefariouszhen.khronos.db.ram

import java.io.Closeable
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import com.nefariouszhen.khronos.ExactTag
import com.nefariouszhen.khronos.db.{TimeSeriesMapping, TimeSeriesMappingDAO}

import scala.collection.mutable

object InMemoryTimeSeriesMappingDAO {
  trait ListenerCallback extends Closeable {
    def isClosed: Boolean
    def callback: TimeSeriesMapping => Unit
  }
}
class InMemoryTimeSeriesMappingDAO extends TimeSeriesMappingDAO {
  import com.nefariouszhen.khronos.db.ram.InMemoryTimeSeriesMappingDAO._

  def isConnected: Boolean = true

  def timeSeries(): Iterable[TimeSeriesMapping] = idMap.values

  def getIdOrCreate(keys: Seq[ExactTag]): Int = this.synchronized {
    idMap.getOrElse(keys, fetchNextMapping(keys)).id
  }

  def getKeys(id: Int): Seq[ExactTag] = this.synchronized {
    keyMap.getOrElse(id, throw new NoSuchElementException("ID does not exist: " + id)).tags
  }

  def subscribe(listener: (TimeSeriesMapping) => Unit): Closeable = this.synchronized {
    val callback = new ListenerCallback {
      private[this] val closed = new AtomicBoolean(false)

      val callback = listener

      def isClosed: Boolean = closed.get()

      def close(): Unit = {
        closed.set(true)
      }
    }

    listeners ::= callback

    callback
  }

  private[this] def fetchNextMapping(keys: Seq[ExactTag]): TimeSeriesMapping = this.synchronized {
    listeners = listeners.filterNot(_.isClosed)

    val mapping = TimeSeriesMapping(nextId.getAndIncrement, keys)
    idMap.put(keys, mapping)
    keyMap.put(mapping.id, mapping)

    listeners.foreach(_.callback(mapping))
    mapping
  }

  private[this] var listeners: List[ListenerCallback] = Nil
  private[this] val nextId = new AtomicInteger()
  private[this] val idMap = mutable.HashMap[Seq[ExactTag], TimeSeriesMapping]()
  private[this] val keyMap = mutable.HashMap[Int, TimeSeriesMapping]()
}
