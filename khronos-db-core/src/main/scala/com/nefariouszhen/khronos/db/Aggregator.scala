package com.nefariouszhen.khronos.db

import java.util

import com.nefariouszhen.khronos.db.index.Mustang
import com.nefariouszhen.khronos.{Time, TimeSeriesPoint}

import scala.collection.JavaConversions._

sealed trait Aggregator {
  protected[this] val ts = new util.TreeMap[Mustang.TSID, Double]()
  protected[this] var lastTm = Time(0)

  def update(id: Mustang.TSID, tsp: TimeSeriesPoint): Option[TimeSeriesPoint] = {
    var ret: Option[TimeSeriesPoint] = None
    if (tsp.tm > lastTm && lastTm != Time(0)) {
      ret = Some(evaluate(lastTm))
    }
    // Ignore Old Data (Need to completely re-aggregate graphs anyways.)
    if (tsp.tm >= lastTm) {
      lastTm = tsp.tm
      ts.put(id, tsp.value)
    }
    ret
  }

  def evaluate(tm: Time): TimeSeriesPoint
}

object Aggregator {
  def apply(agg: Aggregation): Aggregator = agg match {
    case Aggregation.AVG => new Avg
    case Aggregation.MAX => new Max
    case Aggregation.MIN => new Min
    case Aggregation.SUM => new Sum
    case _ => throw new IllegalArgumentException(s"No known function for aggregation type: $agg")
  }

  class Max extends Aggregator {
    override def evaluate(tm: Time): TimeSeriesPoint = TimeSeriesPoint(tm, ts.values.max)
  }

  class Min extends Aggregator {
    override def evaluate(tm: Time): TimeSeriesPoint = TimeSeriesPoint(tm, ts.values.min)
  }

  class Sum extends Aggregator {
    override def evaluate(tm: Time): TimeSeriesPoint = TimeSeriesPoint(tm, ts.values.sum)
  }

  class Avg extends Aggregator {
    override def evaluate(tm: Time): TimeSeriesPoint = TimeSeriesPoint(tm, ts.values.sum / ts.values.size)
  }
}
