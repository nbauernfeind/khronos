package com.nefariouszhen.khronos

import com.fasterxml.jackson.annotation.{JsonIgnore, JsonProperty}

sealed trait ContentTag {
  @JsonProperty
  def k: String
  @JsonProperty
  def v: String
  @JsonIgnore
  def toTagString: String = s"$k:$v"
}

case class ExactTag(k: String, v: String) extends ContentTag with Ordered[ExactTag] {
  override def compare(that: ExactTag): Int = toTagString.compareToIgnoreCase(that.toTagString)
}

case class ExactKeyTag(k: String) extends ContentTag {
  val v = "*"
  override def toTagString: String = s"$k*"
}

case class WildcardTag(k: String, v: String) extends ContentTag {
  override def toTagString: String = s"$k:$v*"
}

case class WildcardKeyTag(k: String) extends ContentTag {
  val v = "*"
  override def toTagString: String = s"$k*"
}

case class IllegalTag(k: String) extends ContentTag {
  val v = "*"
  override def toTagString: String = s"$k"
}

object ContentTag {
  def apply(partial: String): ContentTag = {
    partial match {
      case WILDCARD_KEY(prefix) => WildcardKeyTag(prefix)
      case WILDCARD_VALUE(key, prefix) => WildcardTag(key, prefix)
      case EXACT(key, value) => ExactTag(key, value)
      case EXACT_KEY(prefix) => WildcardKeyTag(prefix)
      case _ => IllegalTag(partial)
    }
  }

  implicit class ExactTagBuilder(k: String) {
    def ->(v: String): ExactTag = ExactTag(k, v)
  }

  val EXACT = """([a-zA-Z0-9.]+):([a-zA-Z0-9.]*)""".r
  val EXACT_KEY = """([a-zA-Z0-9.]*)""".r
  val WILDCARD_KEY = """([a-zA-Z0-9.]*)\*""".r
  val WILDCARD_VALUE = """([a-zA-Z0-9.]+):([a-zA-Z0-9.]*)\*""".r
}
