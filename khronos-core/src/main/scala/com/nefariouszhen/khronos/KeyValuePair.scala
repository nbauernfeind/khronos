package com.nefariouszhen.khronos

case class KeyValuePair(k: String, v: String) extends Ordered[KeyValuePair] {
  override def toString: String = k + ":" + v

  def compare(that: KeyValuePair): Int = {
    val kc = k.compare(that.k)
    if (kc == 0) v.compare(that.v) else kc
  }
}

object KeyValuePair {
  private[this] val kvpRegex = """([\w]+):([\w]+)""".r
  def apply(s: String): KeyValuePair = s match {
    case kvpRegex(key, value) => KeyValuePair(key, value)
  }
}
