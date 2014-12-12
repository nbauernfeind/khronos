package com.nefariouszhen.khronos

case class Time(nanos: Long) extends AnyVal with Ordered[Time] {
  override def compare(that: Time): Int = nanos.compare(that.nanos)
}
