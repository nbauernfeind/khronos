package com.nefariouszhen.khronos.db

import java.io.Closeable
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import com.google.inject.Inject
import com.nefariouszhen.khronos.{TimeSeriesPoint, KeyValuePair, Time}

import scala.collection.mutable
import scala.util.Random

object Multiplexus {
  class Status(m: Multiplexus) {
    val isConnected = m.dao.isConnected
    val numSeriesActive: Long = m.entryPoints.size
    val numStreamsActive: Long = m.streamCount.get
    val numInPointsLastHour: Long = Math.abs(Random.nextLong()) % 10000000000L
    val numOutPointsLastHour: Long = Random.nextInt(100000)
  }

  trait Query

  case class RawQuery(rawKeys: Seq[KeyValuePair])

  trait ListenerCallback extends Closeable {
    def isClosed: Boolean
    def callback: TimeSeriesPoint => Unit
  }

  class ListenerSet {
    private[this] var listeners: List[ListenerCallback] = Nil

    def isEmpty: Boolean = listeners.isEmpty
    def add(callback: ListenerCallback): Unit = this.synchronized {
      listeners = callback :: listeners
    }

    def deliver(tsp: TimeSeriesPoint): Unit = this.synchronized {
      listeners = listeners.filterNot(_.isClosed)
      listeners.foreach(_.callback(tsp))
    }

    def reduce(): Unit = this.synchronized {
      listeners = listeners.filterNot(_.isClosed)
    }
  }
}

class Multiplexus @Inject()(private val dao: TimeSeriesDatabaseDAO) {

  import com.nefariouszhen.khronos.db.Multiplexus._

  def write(rawKeys: Seq[KeyValuePair], tm: Time, value: Double): Unit = {
    val keys = rawKeys.sorted
    dao.write(keys, tm, value)

    val listenerSet = this.synchronized { entryPoints.get(keys) }
    listenerSet.foreach(_.deliver(TimeSeriesPoint(tm, value)))
  }

  def subscribe(query: RawQuery, listener: TimeSeriesPoint => Unit): Closeable = {
    val ret = new ListenerCallback {
      streamCount.incrementAndGet()
      private[this] val closed = new AtomicBoolean(false)

      val callback = listener

      def isClosed = closed.get()

      def close(): Unit = {
        closed.set(true)
        streamCount.decrementAndGet()
      }
    }

    this.synchronized {
      entryPoints.getOrElseUpdate(query.rawKeys.sorted, new ListenerSet()).add(ret)
    }

    ret
  }

  private val streamCount = new AtomicLong()
  private val entryPoints = mutable.HashMap[Seq[KeyValuePair], ListenerSet]()

  def status: Status = new Status(this)
}
