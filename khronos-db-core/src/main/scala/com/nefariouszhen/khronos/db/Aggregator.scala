package com.nefariouszhen.khronos.db

import java.util

import com.nefariouszhen.khronos.db.index.Mustang
import com.nefariouszhen.khronos.{Time, TimeSeriesPoint}

import scala.collection.JavaConversions._

sealed trait Aggregator {
  protected[this] val ts = new util.TreeMap[Mustang.TSID, Double]()

  def update(id: Mustang.TSID, tsp: TimeSeriesPoint): Unit = {
    ts.put(id, tsp.value)
  }

  def evaluate(tm: Time): Double
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
    def evaluate(tm: Time): Double = ts.values.max
  }

  class Min extends Aggregator {
    def evaluate(tm: Time): Double = ts.values.min
  }

  class Sum extends Aggregator {
    def evaluate(tm: Time): Double = ts.values.sum
  }

  class Avg extends Aggregator {
    def evaluate(tm: Time): Double = if (ts.values.size == 0) Double.NaN else ts.values.sum / ts.values.size
  }

  class Now extends Aggregator {
    def evaluate(tm: Time): Double = tm.toSeconds
  }
}
