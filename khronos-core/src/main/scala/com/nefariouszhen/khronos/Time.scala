package com.nefariouszhen.khronos

case class Time(nanos: Long) extends AnyVal with Ordered[Time] {
  def toSeconds: Double = nanos.toDouble / 1e9
  def toDouble: Double = nanos
  override def compare(that: Time): Int = nanos.compare(that.nanos)
}

object Time {
  def fromSeconds(tm: Double): Time = Time((tm * 1e9).toLong)
}
