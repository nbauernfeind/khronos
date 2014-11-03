package com.nefariouszhen.khronos

case class KeyValuePair(k: String, v: String) extends Ordered[KeyValuePair] {
  override def toString: String = k + ":" + v

  override def equals(o: Any): Boolean = o match {
    case that: KeyValuePair => compare(that) == 0
    case _ => false
  }

  def compare(that: KeyValuePair): Int = {
    val kc = k.compareToIgnoreCase(that.k)
    if (kc == 0) v.compareToIgnoreCase(that.v) else kc
  }
}

object KeyValuePair {
  private[this] val kvpRegex = """([\w]+):([\w]+)""".r
  def apply(s: String): KeyValuePair = s match {
    case kvpRegex(key, value) => KeyValuePair(key, value)
  }

  implicit class KeyValuePairBuilder(k: String) {
    def ->(v: String): KeyValuePair = KeyValuePair(k, v)
  }
}
