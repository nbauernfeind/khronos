package com.nefariouszhen.khronos.db

import java.io.Closeable
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}

import com.google.inject.Inject
import com.nefariouszhen.khronos.db.index.Mustang
import com.nefariouszhen.khronos.db.index.Mustang.TSID
import com.nefariouszhen.khronos.metrics.MetricsRegistry
import com.nefariouszhen.khronos.util.{PeekIterator, SafeRunnable}
import com.nefariouszhen.khronos.websocket.{WebSocketManager, WebSocketWriter}
import com.nefariouszhen.khronos.{ContentTag, ExactTag, Time, TimeSeriesPoint}
import io.dropwizard.lifecycle.Managed
import org.slf4j.LoggerFactory

import scala.collection.mutable

object Multiplexus {
  val WRITE_DELAY_SECONDS = 1

  trait ListenerCallback extends Closeable {
    def isClosed: Boolean

    def callback(id: Mustang.TSID, tsp: TimeSeriesPoint): Unit
  }

  class ListenerSet(id: Mustang.TSID) {
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
        listener.callback(id, tsp)
      }
      cnt
    }

    def reduce(): Unit = this.synchronized {
      listeners = listeners.filterNot(_.isClosed)
    }
  }
}

class Multiplexus @Inject()(idMap: TimeSeriesMappingDAO, dao: TimeSeriesDatabaseDAO, registry: MetricsRegistry,
                            mustang: Mustang, executor: ScheduledExecutorService) extends Managed {

  import com.nefariouszhen.khronos.db.Multiplexus._
  import com.nefariouszhen.khronos.db.websocket._

  private[this] val log = LoggerFactory.getLogger(this.getClass)
  private[this] val taskFuture = new AtomicReference[Option[ScheduledFuture[_]]](None)

  private[this] val streamCount = new AtomicLong()
  private[this] val entryPoints = mutable.HashMap[Int, ListenerSet]()

  /** Multiplexus Metrics **/
  private[this] val metrics = new {

    import com.nefariouszhen.khronos.ContentTag._

    private[this] def newKey(typ: String) = List("type" -> typ, "app" -> "khronos", "system" -> "multiplexus")

    val numInPoints = registry.newCounter(newKey("numInPoints"))
    val numOutPoints = registry.newCounter(newKey("numOutPoints"))
    val writeTm = registry.newTimer(newKey("writeTm"))

    registry.newMeter(newKey("numQueriesActive"), entryPoints.size)
    registry.newMeter(newKey("numStreamsActive"), streamCount.get)
  }

  @Inject
  private[this] def registerWebHooks(ws: WebSocketManager): Unit = {
    ws.registerCallback(onSubscribeRequest)
  }

  private[this] def onSubscribeRequest(writer: WebSocketWriter, request: MetricSubscribe): Unit = {
    writer.manage(subscribe(request, (r: MetricResponse) => writer.write(r)))
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

  def write(rawKeys: Seq[ExactTag], tm: Time, value: Double): Unit = metrics.writeTm.time {
    val id = idMap.getIdOrCreate(rawKeys.sorted)
    dao.write(id, tm, value)
    metrics.numInPoints.increment()

    for (listenerSet <- this.synchronized {
      entryPoints.get(id)
    }) {
      metrics.numOutPoints.increment(listenerSet.deliver(TimeSeriesPoint(tm, value)))
    }
  }

  def subscribe(query: MetricSubscribe, callback: MetricResponse => Unit): Closeable = {
    val tags = query.tags.map(ContentTag.apply)
    val active = mustang.query(tags)
    if (active.isEmpty) {
      callback(MetricWarning("No active timeseries for this query."))
    }

    // TODO: Multiple aggregations for an individual query.
    val gid = 1
    val ret = new AggregateListener(gid, Aggregator(query.agg), callback)

    for (id <- active.iterator) {
      entryPoints.getOrElseUpdate(id, new ListenerSet(id)).add(ret)
    }
    callback(MetricHeader(gid, tags.map(tag => tag.k -> tag.v).toMap))

    // Compute historical data.
    case class TS(id: Int, it: PeekIterator[TimeSeriesPoint])
    var ats = (for (id <- active.iterator) yield {
      TS(id, new PeekIterator(dao.read(id, Time(0))))
    }).filter(_.it.hasNext).toSeq

    val historicalPoints = mutable.ArrayBuffer[Seq[Double]]()

    while (ats.nonEmpty) {
      var currTm = ats.view.map(_.it.peek.tm).min
      for (ts <- ats) {
        if (ts.it.peek.tm == currTm) {
          for (pt <- ret.aggHistorical(ts.id, ts.it.next())) {
            historicalPoints += Seq(pt.tm.toSeconds, pt.value)
          }
        }
      }
      ats = ats.filter(_.it.hasNext)
    }
    callback(MetricValue(historicalPoints))

    ret
  }

  def timeseries: Iterable[Seq[ExactTag]] = idMap.timeSeries().map(_.tags)

  class AggregateListener(gid: Int, agg: Aggregator, pCallback: MetricResponse => Unit) extends ListenerCallback {
    streamCount.incrementAndGet()
    private[this] val closed = new AtomicBoolean(false)

    def isClosed: Boolean = closed.get()

    def close(): Unit = {
      closed.set(true)
      streamCount.decrementAndGet()
    }

    def callback(id: Mustang.TSID, tsp: TimeSeriesPoint): Unit = this.synchronized {
      agg.update(id, tsp).map(pt => pCallback(MetricValue(pt)))
    }

    def aggHistorical(id: Mustang.TSID, tsp: TimeSeriesPoint): Option[TimeSeriesPoint] = {
      agg.update(id, tsp)
    }
  }

  def status: Status = new Status

  class Status {
    val isConnected = dao.isConnected && idMap.isConnected
  }
}
