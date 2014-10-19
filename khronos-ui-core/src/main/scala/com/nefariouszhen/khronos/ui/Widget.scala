package com.nefariouszhen.khronos.ui

import com.fasterxml.jackson.annotation.JsonProperty

trait Widget[T] {
  @JsonProperty def data: T
  @JsonProperty def title: String
  @JsonProperty def partial: String
}
