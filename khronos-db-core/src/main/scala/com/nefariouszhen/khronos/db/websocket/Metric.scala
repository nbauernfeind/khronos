package com.nefariouszhen.khronos.db.websocket

import com.fasterxml.jackson.annotation.JsonTypeInfo.As
import com.fasterxml.jackson.annotation.{JsonTypeInfo, JsonTypeName}
import com.nefariouszhen.khronos.{Time, TimeSeriesPoint}
import com.nefariouszhen.khronos.db.Aggregation
import com.nefariouszhen.khronos.websocket.WebSocketRequest
import io.dropwizard.jackson.Discoverable

@JsonTypeName("metric-subscribe")
case class MetricSubscribe(tags: Seq[String], agg: Aggregation) extends WebSocketRequest

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.PROPERTY, property = "type")
sealed trait MetricResponse extends Discoverable

@JsonTypeName("header")
case class MetricHeader(id: Int, label: String, tags: Map[String, String]) extends MetricResponse

/**
 * This is a value array that is structured to work with common graphing libraries.
 * The first element is a timestamp, and every value thereafter is a single timeseries in the data.
 */
@JsonTypeName("value")
case class MetricValue(data: Seq[Seq[Double]]) extends MetricResponse

@JsonTypeName("warn")
case class MetricWarning(what: String) extends MetricResponse

@JsonTypeName("error")
case class MetricError(what: String) extends MetricResponse
