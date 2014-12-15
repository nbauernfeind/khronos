package com.nefariouszhen.khronos

case class Time(nanos: Long) extends AnyVal with Ordered[Time] {
  def toDouble: Double = nanos
  override def compare(that: Time): Int = nanos.compare(that.nanos)
}
