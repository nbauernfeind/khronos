package com.nefariouszhen.khronos.db

import javax.ws.rs.core.MediaType
import javax.ws.rs.{GET, Path, Produces, QueryParam}

import com.google.inject.Inject
import com.nefariouszhen.khronos.KeyValuePair
import com.nefariouszhen.khronos.db.index.{AutoCompleteRequest, AutoCompleteResult, Mustang}

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
    val chunks = query.split(" ")
    if (query.endsWith(" ")) {
      index.query(AutoCompleteRequest(chunks, "", None))
    } else {
      val partial = chunks.lastOption.getOrElse("")
      index.query(AutoCompleteRequest(chunks.slice(0, chunks.length - 1), partial, None))
    }
  }
}
