package com.nefariouszhen.khronos.db

import javax.ws.rs.core.{Response, MediaType}
import javax.ws.rs.{Consumes, PUT, GET, Path, Produces, QueryParam}

import com.google.inject.Inject
import com.nefariouszhen.khronos.{Time, ContentTag, ExactTag}
import com.nefariouszhen.khronos.db.index.{AutoCompleteRequest, AutoCompleteResult, Mustang}

@Path("/1/db")
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class DatabaseResource @Inject()(tsdb: Multiplexus, index: Mustang) {
  @GET
  @Path("/status")
  def status(): Multiplexus#Status = tsdb.status

  @GET
  @Path("/timeseries")
  def timeseries(): Iterable[Seq[ExactTag]] = tsdb.timeseries

  @GET
  @Path("/autocomplete")
  def autocomplete(@QueryParam("q") query: String): Iterable[AutoCompleteResult] = {
    val chunks = query.split(" ")
    if (query.endsWith(" ")) {
      index.query(AutoCompleteRequest(chunks, "", None))
    } else {
      val partial = chunks.lastOption.getOrElse("")
      index.query(AutoCompleteRequest(chunks.slice(0, chunks.length - 1), partial, None))
    }
  }

  @PUT
  @Path("/metrics")
  def putMetrics(payload: MetricsPayload): Response = {
    val tags = for (tsTags <- payload.tags) yield {
      val res = for (tag <- tsTags) yield {
        val t = ContentTag(tag)
        if (!t.isInstanceOf[ExactTag]) {
          return Response
            .status(Response.Status.BAD_REQUEST)
            .entity(s"Could not parse tag: $tag")
            .build()
        }
        t.asInstanceOf[ExactTag]
      }

      if (res.length < 2) {
        return Response
          .status(Response.Status.BAD_REQUEST)
          .entity(s"Not enough tags for timeseries (min: 2): ${res.mkString(" ")}")
          .build()
      }

      res
    }

    for (line <- payload.values) {
      if (line.length != tags.length + 1) {
        return Response
          .status(Response.Status.BAD_REQUEST)
          .entity(s"TimeSeries Element Has Incorrect Size. (expected: ${tags.length + 1} != actual: ${line.length}")
          .build()
      }
      val tm = Time.fromSeconds(line(0))
      for (i <- 1 until line.length) {
        tsdb.write(tags(i - 1), tm, line(i))
      }
    }

    Response.noContent().build()
  }
}
