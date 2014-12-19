package com.nefariouszhen.khronos.db

import java.io.Closeable
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import java.util.concurrent.{ScheduledExecutorService, ScheduledFuture, TimeUnit}

import com.google.inject.Inject
import com.nefariouszhen.khronos.db.index.Mustang
import com.nefariouszhen.khronos.metrics.MetricsRegistry
import com.nefariouszhen.khronos.util.{PeekIterator, SafeRunnable}
import com.nefariouszhen.khronos.websocket.{WebSocketManager, WebSocketWriter}
import com.nefariouszhen.khronos.{WildcardTag, SplitTag, ContentTag, ExactTag, Time, TimeSeriesPoint}
import io.dropwizard.lifecycle.Managed
import org.slf4j.LoggerFactory

import scala.collection.mutable

object Multiplexus {
  val WRITE_DELAY_SECONDS = 1
  val LINE_LIMIT = 50

  val NULL_CLOSEABLE = new Closeable {
    def close(): Unit = {}
  }

  class ListenerSet(id: Mustang.TSID) {
    // Returns true if still open.
    type Callback = (Mustang.TSID, TimeSeriesPoint) => Boolean
    private[this] var listeners: List[Callback] = Nil

    def isEmpty: Boolean = listeners.isEmpty

    def add(callback: Callback): Unit = this.synchronized {
      listeners = callback :: listeners
    }

    def deliver(tsp: TimeSeriesPoint): Long = this.synchronized {
      var cnt = 0
      listeners = listeners.filter(listener => {
        cnt += 1
        listener(id, tsp)
      })
      cnt
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
    val startTm = Time.fromSeconds(query.startTm)
    val endTm = Time.fromSeconds(query.startTm + query.timeRange)

    val tags = query.tags.map(ContentTag.apply)
    val splitTags = tags collect { case t: SplitTag => t }

    // TODO: Cartesian Product of Splits (if there are few enough tags; why not?)
    if (splitTags.size > 1) {
      callback(MetricError("Can only split on a single key, but found: " + splitTags.mkString(", ")))
      return NULL_CLOSEABLE
    }

    val valTypeQuery = mustang.query(tags, WildcardTag("valtype", ""), numResults = 2)
    if (splitTags.filter(_.k == "valtype").isEmpty && valTypeQuery.size > 1) {
      callback(MetricWarning("Consider filtering on 'valtype', aggregating across different types is not recommended."))
    }

    val typeQuery = mustang.query(tags, WildcardTag("type", ""), numResults = 2)
    if (splitTags.filter(_.k == "type").isEmpty && typeQuery.size > 1) {
      callback(MetricWarning("Consider filtering on 'type', aggregating across different types is not recommended."))
    }

    val typeTag = tags.find(_.k == "type").orElse(tags.headOption)
    val lines = if (splitTags.size == 1) mustang.resolveCompletions(splitTags.head) else typeTag.iterator

    val filteredTags = tags.filterNot(splitTags.contains).toList

    val subscription = new SubscriptionListener(callback)

    case class TS(aggId: Int, id: Int, it: PeekIterator[TimeSeriesPoint])
    val tsBuffer = mutable.ArrayBuffer[TS]()

    // Let's only keep LINE_LIMIT lines to prevent the UI from being too slow, and also from doing invalid work.
    var linesSkipped = 0

    // Note: we name each line after the first valid tag from: split tag, type tag, initial tag
    for (line <- lines) {
      val lineTags = line :: filteredTags
      val active = mustang.query(lineTags)

      // Only add this line if the other tags make it valid.
      if (!active.isEmpty) {
        if (subscription.numLines >= LINE_LIMIT) {
          linesSkipped += 1
        } else {
          val aggId = subscription.newLine(Aggregator(query.agg))
          for (id <- active.iterator) {
            // TODO: not thread safe
            // TODO: race condition between historical data and new-writes
            entryPoints.getOrElseUpdate(id, new ListenerSet(id)).add(subscription.onWrite(aggId))
          }
          callback(MetricHeader(aggId, line.v, lineTags.map(tag => tag.k -> tag.v).toMap))

          for (id <- active.iterator) {
            tsBuffer += TS(aggId, id, new PeekIterator(dao.read(id, startTm)))
          }
        }
      }
    }

    if (linesSkipped > 0) {
      callback(MetricWarning(s"Too many lines. Dropping $linesSkipped matches. (keeping: $LINE_LIMIT)"))
    }

    var ats = tsBuffer.filter(_.it.hasNext).toList
    val historicalPoints = mutable.ArrayBuffer[Seq[Double]]()

    if (ats.isEmpty) {
      callback(MetricWarning("No active timeseries for this query in this time period."))
    }

    // While there are historical ticks to iterate over, keep generating data points.
    while (ats.nonEmpty) {
      var currTm = ats.view.map(_.it.peek.tm).min
      if (currTm <= endTm) {
        for (ts <- ats) {
          if (ts.it.peek.tm == currTm) {
            for (pt <- subscription.onHistorical(ts.aggId)(ts.id, ts.it.next())) {
              historicalPoints += pt
            }
          }
        }
        ats = ats.filter(_.it.hasNext)
      } else {
        ats = Nil
      }
    }

    // Send known historical data.
    callback(MetricValue(historicalPoints))

    subscription
  }

  def timeseries: Iterable[Seq[ExactTag]] = idMap.timeSeries().map(_.tags)

  trait Line {
    def evaluate(tm: Time): Double
    def update(id: Mustang.TSID, tsp: TimeSeriesPoint): Boolean
  }

  class TimeLine extends Line {
    def evaluate(tm: Time): Double = tm.toSeconds
    def update(id: Mustang.TSID, tsp: TimeSeriesPoint): Boolean = true
  }

  class SubscriptionListener(pCallback: MetricResponse => Unit) extends Closeable {
    streamCount.incrementAndGet()

    private[this] val closed = new AtomicBoolean(false)
    def isClosed: Boolean = closed.get()

    def close(): Unit = {
      closed.set(true)
      streamCount.decrementAndGet()
    }

    private[this] val lines = mutable.ArrayBuffer[Aggregator](new Aggregator.Now)
    def newLine(agg: Aggregator): Int = {
      val id = lines.length
      lines += agg
      id
    }

    def numLines: Int = lines.size - 1

    private[this] var lastTm = Time(0)
    // This returns true if it is still open.
    def onWrite(gid: Int)(id: Mustang.TSID, tsp: TimeSeriesPoint): Boolean = this.synchronized {
      if (isClosed) return false
      if (tsp.tm < lastTm) return true

      if (lastTm != tsp.tm && lastTm != Time(0)) {
        pCallback(MetricValue(Seq(lines.map(_.evaluate(lastTm)))))
      }
      lastTm = tsp.tm
      lines(gid).update(id, tsp)

      !isClosed
    }

    def onHistorical(gid: Int)(id: Mustang.TSID, tsp: TimeSeriesPoint): Option[Seq[Double]] = {
      var ret: Option[Seq[Double]] = None
      if (tsp.tm < lastTm) return ret

      if (lastTm != tsp.tm && lastTm != Time(0)) {
        ret = Some(lines.map(_.evaluate(lastTm)))
      }
      lastTm = tsp.tm
      lines(gid).update(id, tsp)
      ret
    }
  }

  def status: Status = new Status

  class Status {
    val isConnected = dao.isConnected && idMap.isConnected
  }
}
