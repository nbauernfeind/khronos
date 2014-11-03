package com.nefariouszhen.khronos.db

import java.util.concurrent.atomic.AtomicLong

import com.nefariouszhen.khronos.{KeyValuePair, Time}

import scala.collection.mutable

object MetricsRegistry {
  trait Metric {
    @volatile protected var lastVal: Double = 0
    def getLast = lastVal
    def getValue(tm: Time): Double
  }

  class Meter(compute: () => Double) extends Metric {
    def getValue(tm: Time): Double = {
      lastVal = compute()
      lastVal
    }
  }

  class Counter extends AtomicLong with Metric {
    def getValue(tm: Time): Double = {
      lastVal = getAndSet(0)
      lastVal
    }
  }
}

class MetricsRegistry {

  import com.nefariouszhen.khronos.db.MetricsRegistry._

  private[this] val registry = mutable.HashMap[Seq[KeyValuePair], Metric]()

  def newCounter(rawKeys: Seq[KeyValuePair]): Counter = this.synchronized {
    val counter = new Counter
    registry.getOrElseUpdate(validateKey(rawKeys), counter)
    counter
  }

  def newMeter(rawKeys: Seq[KeyValuePair], compute: => Double): Meter = this.synchronized {
    val meter = new Meter(() => compute)
    registry.getOrElseUpdate(validateKey(rawKeys), meter)
    meter
  }

  def writeMetrics(tsdb: Multiplexus): Unit = this.synchronized {
    val tm = Time(System.currentTimeMillis() / 1e3.toLong)
    for ((keys, metric) <- registry) {
      tsdb.write(keys, tm, metric.getValue(tm))
    }
  }

  private[this] def validateKey(rawKeys: Seq[KeyValuePair]): Seq[KeyValuePair] = {
    val keys = rawKeys.sorted
    if (registry.get(keys).isDefined) {
      throw new IllegalArgumentException("Metric pre-existing for key: " + keys.mkString(","))
    }
    keys
  }
}
