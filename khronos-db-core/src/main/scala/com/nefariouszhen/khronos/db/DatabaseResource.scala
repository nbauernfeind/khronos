package com.nefariouszhen.khronos.db

import javax.ws.rs.core.MediaType
import javax.ws.rs.{QueryParam, GET, Path, Produces}

import com.google.inject.Inject
import com.nefariouszhen.khronos.KeyValuePair
import com.nefariouszhen.khronos.db.index.{PartialQuery, AutoCompleteResult, Mustang}

@Path("/1/db")
@Produces(Array(MediaType.APPLICATION_JSON))
class DatabaseResource @Inject()(tsdb: Multiplexus, index: Mustang) {
  @GET
  @Path("/status")
  def status(): Multiplexus#Status = tsdb.status

  @GET
  @Path("/timeseries")
  def timeseries(): Iterable[Seq[KeyValuePair]] = tsdb.timeseries

  @GET
  @Path("/autocomplete")
  def autocomplete(@QueryParam("q") query: String): Iterable[AutoCompleteResult] = {
    val pqs = query.split(" ").map(PartialQuery.apply)
    if (query.endsWith(" ")) {
      index.query(pqs, PartialQuery(""))
    } else {
      val pq = pqs.lastOption.getOrElse(PartialQuery(""))
      index.query(pqs.slice(0, pqs.length - 1), pq)
    }
  }
}
