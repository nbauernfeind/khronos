package com.nefariouszhen.khronos.db

import com.fasterxml.jackson.annotation.JsonProperty

class MetricsPayload(
  @JsonProperty
  val tags: Array[Array[String]],
  @JsonProperty
  val values: Array[Array[Double]]
)
