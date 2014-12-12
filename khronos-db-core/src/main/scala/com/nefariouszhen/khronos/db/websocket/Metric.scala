package com.nefariouszhen.khronos.db.websocket

import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.{JsonTypeInfo, JsonTypeName}
import com.nefariouszhen.khronos.TimeSeriesPoint
import com.nefariouszhen.khronos.db.Aggregation
import com.nefariouszhen.khronos.websocket.WebSocketRequest
import io.dropwizard.jackson.Discoverable

@JsonTypeName("metric-subscribe")
case class MetricSubscribe(tags: Seq[String], agg: Aggregation) extends WebSocketRequest

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.PROPERTY, property = "type")
sealed trait MetricResponse extends Discoverable

@JsonTypeName("header")
case class MetricHeader(id: Int, tags: Map[String, String]) extends MetricResponse

@JsonTypeName("value")
case class MetricValue(id: Int, points: Seq[TimeSeriesPoint]) extends MetricResponse

@JsonTypeName("warn")
case class MetricWarning(what: String) extends MetricResponse

@JsonTypeName("error")
case class MetricError(what: String) extends MetricResponse
