package com.nefariouszhen.khronos.db

import java.io.Closeable
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}

import com.google.inject.Inject
import com.nefariouszhen.khronos.metrics.MetricsRegistry
import com.nefariouszhen.khronos.util.SafeRunnable
import com.nefariouszhen.khronos.{KeyValuePair, Time, TimeSeriesPoint}
import io.dropwizard.lifecycle.Managed
import org.slf4j.LoggerFactory

import scala.collection.mutable

object Multiplexus {
  val WRITE_DELAY_SECONDS = 30

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

    def deliver(tsp: TimeSeriesPoint): Long = this.synchronized {
      listeners = listeners.filterNot(_.isClosed)
      var cnt = 0
      for (listener <- listeners) {
        cnt += 1
        listener.callback(tsp)
      }
      cnt
    }

    def reduce(): Unit = this.synchronized {
      listeners = listeners.filterNot(_.isClosed)
    }
  }
}

class Multiplexus @Inject()(idMap: TimeSeriesMappingDAO, dao: TimeSeriesDatabaseDAO, registry: MetricsRegistry,
                            executor: ScheduledExecutorService) extends Managed {

  import com.nefariouszhen.khronos.KeyValuePair._
  import com.nefariouszhen.khronos.db.Multiplexus._

  private[this] val log = LoggerFactory.getLogger(this.getClass)
  private[this] val taskFuture = new AtomicReference[Option[ScheduledFuture[_]]](None)

  private[this] val streamCount = new AtomicLong()
  private[this] val entryPoints = mutable.HashMap[Int, ListenerSet]()

  /** Multiplexus Metrics **/
  private[this] val metrics = new {
    private[this] def newKey(typ: String) = List("type" -> typ, "app" -> "khronos", "system" -> "multiplexus")

    val numInPoints = registry.newCounter(newKey("numInPoints"))
    val numOutPoints = registry.newCounter(newKey("numOutPoints"))
    val writeTm = registry.newTimer(newKey("writeTm"))

    registry.newMeter(newKey("numQueriesActive"), entryPoints.size)
    registry.newMeter(newKey("numStreamsActive"), streamCount.get)
  }

  def start(): Unit = {
    val newFuture = executor.scheduleAtFixedRate(
      new SafeRunnable(log, registry.writeMetrics(write)),
      (WRITE_DELAY_SECONDS - (System.currentTimeMillis() / 1e3).toLong) % WRITE_DELAY_SECONDS,
      WRITE_DELAY_SECONDS,
      TimeUnit.SECONDS
    )
    taskFuture.getAndSet(Some(newFuture)).foreach(_.cancel(false))
  }

  def stop(): Unit = {
    taskFuture.getAndSet(None).foreach(_.cancel(false))
  }

  def write(rawKeys: Seq[KeyValuePair], tm: Time, value: Double): Unit = metrics.writeTm.time {
    val id = idMap.getIdOrCreate(rawKeys.sorted)
    dao.write(id, tm, value)
    metrics.numInPoints.increment()

    for (listenerSet <- this.synchronized {
      entryPoints.get(id)
    }) {
      metrics.numOutPoints.increment(listenerSet.deliver(TimeSeriesPoint(tm, value)))
    }
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

    // TODO: This is not what I really want, eventually a query should span multiple time series.
    // (and we shouldn't create an id for an unknown kvp seq)
    val id = idMap.getIdOrCreate(query.rawKeys.sorted)
    this.synchronized {
      entryPoints.getOrElseUpdate(id, new ListenerSet()).add(ret)
    }

    ret
  }

  def timeseries: Iterable[Seq[KeyValuePair]] = idMap.timeSeries().map(_.kvps)

  def status: Status = new Status

  class Status {
    val isConnected = dao.isConnected && idMap.isConnected
  }
}
