package com.nefariouszhen.khronos.db

import javax.ws.rs.core.MediaType
import javax.ws.rs.{GET, Path, Produces}

import com.google.inject.Inject
import com.nefariouszhen.khronos.KeyValuePair

@Path("/1/db")
@Produces(Array(MediaType.APPLICATION_JSON))
class DatabaseResource @Inject()(tsdb: Multiplexus) {
  @GET
  @Path("/status")
  def status(): Multiplexus#Status = tsdb.status

  @GET
  @Path("/timeseries")
  def timeseries(): Iterable[Seq[KeyValuePair]] = tsdb.timeseries
}
