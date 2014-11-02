package com.nefariouszhen.khronos

case class TimeSeriesPoint(tm: Time, value: Double) extends Ordered[TimeSeriesPoint] {
  override def compare(that: TimeSeriesPoint): Int = tm.nanos.compareTo(that.tm.nanos)
}
