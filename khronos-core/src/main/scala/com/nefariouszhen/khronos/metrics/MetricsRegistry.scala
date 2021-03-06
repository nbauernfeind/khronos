package com.nefariouszhen.khronos.metrics

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

import com.codahale.metrics.{Timer => CodaTimer}
import com.nefariouszhen.khronos.ContentTag._
import com.nefariouszhen.khronos.metrics.MetricsRegistry._
import com.nefariouszhen.khronos.{ExactTag, Time}

import scala.collection.mutable

/**
 * MetricsRegistry with concepts that best fit Khronos.
 *
 * One of khronos's fundamental design ideas is that most users will be
 * horizontally scaling and rapidly deploying. So a goal is to push the
 * responsibility of aggregations onto khronos as often as possible.
 *
 * These metrics reset after every reading and your custom metrics should
 * too.
 *
 * The custom key "valtype" is used to hint to Khronos how to aggregate
 * historical data as well as related timeseries aggregated across some
 * metric such as "host" or "region".
 */
object MetricsRegistry {
  trait Metric

  class Meter(compute: () => Double) extends Metric {
    private[MetricsRegistry] def getValue: Double = compute()
  }

  class Counter extends Metric {
    private[this] val underlying = new AtomicLong()

    private[MetricsRegistry] def getValue: Double = underlying.getAndSet(0)

    def increment(dt: Long = 1): Unit = underlying.addAndGet(dt)
  }

  class Timer extends Metric {
    private[this] val underlying = new AtomicReference[CodaTimer](new CodaTimer)

    private[MetricsRegistry] def getValue: CodaTimer = underlying.getAndSet(new CodaTimer)

    def time(): CodaTimer.Context = underlying.get().time()

    def time[T](thunk: => T): T = {
      val context = time()
      try {
        thunk
      } finally {
        context.stop()
      }
    }
  }
}

class MetricsRegistry {
  private[this] val registry = mutable.HashMap[Seq[ExactTag], Metric]()

  def newCounter(rawKeys: Seq[ExactTag]): Counter = this.synchronized {
    val counter = new Counter
    registry.getOrElseUpdate(validateKey("count", rawKeys), counter)
    counter
  }

  def newMeter(rawKeys: Seq[ExactTag], compute: => Double): Meter = this.synchronized {
    val meter = new Meter(() => compute)
    registry.getOrElseUpdate(validateKey("meter", rawKeys), meter)
    meter
  }

  def newTimer(rawKeys: Seq[ExactTag]): Timer = this.synchronized {
    val timer = new Timer
    registry.getOrElseUpdate(validateKey(rawKeys), timer)
    timer
  }

  private[this] val writeTm = newCounter(Seq(
    "app" -> "khronos",
    "system" -> "registry",
    "type" -> "writeTime",
    "units" -> "ms"
  ))

  def writeMetrics(write: (Seq[ExactTag], Time, Double) => Unit): Unit = this.synchronized {
    val startTm = System.currentTimeMillis()
    val tm = Time.fromSeconds(startTm / 1e3)
    for ((keys, metric) <- registry) metric match {
      case m: Meter => write(keys, tm, m.getValue)
      case m: Counter => write(keys, tm, m.getValue)
      case m: Timer =>
        val t = m.getValue
        val snapshot = t.getSnapshot

        val keyArr = mutable.ArrayBuffer(keys: _*)
        keyArr += "units" -> "seconds"

        val valTypeIdx = keyArr.length
        keyArr += "type" -> "ignore"

        implicit class WriteHelper(value: Double) {
          def ->(typ: String): Unit = {
            keyArr(valTypeIdx) = "valtype" -> typ
            // Note: convert nanoseconds into seconds.
            write(keyArr, tm, if (typ == "count") value else value / 1e9)
          }
        }

        t.getCount -> "count"
        snapshot.getMax -> "max"
        snapshot.getMean -> "mean"
        snapshot.getMin -> "min"
        snapshot.getMedian -> "p50"
        snapshot.get75thPercentile -> "p75"
        snapshot.get95thPercentile -> "p95"
        snapshot.get98thPercentile -> "p98"
        snapshot.get99thPercentile -> "p99"
        snapshot.get999thPercentile -> "p999"
        snapshot.getStdDev -> "stddev"
      case _ =>
    }
    writeTm.increment(System.currentTimeMillis() - startTm)
  }

  private[this] def validateKey(valtype: String, tags: Seq[ExactTag]): Seq[ExactTag] = {
    val rawKeys = mutable.ArrayBuffer[ExactTag]()
    rawKeys.sizeHint(tags.length + 1)
    rawKeys ++= tags
    rawKeys += "valtype" -> valtype
    validateKey(rawKeys)
  }

  private[this] def validateKey(rawTags: Seq[ExactTag]): Seq[ExactTag] = {
    val keys = rawTags.sorted
    if (registry.get(keys).isDefined) {
      throw new IllegalArgumentException("Metric pre-existing for key: " + keys.mkString(","))
    }
    keys
  }
}
