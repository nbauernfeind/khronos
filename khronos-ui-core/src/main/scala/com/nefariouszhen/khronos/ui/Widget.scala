package com.nefariouszhen.khronos.ui

import com.fasterxml.jackson.annotation.JsonProperty

trait Widget[T] {
  @JsonProperty def name: String
  @JsonProperty def partial: String
}
