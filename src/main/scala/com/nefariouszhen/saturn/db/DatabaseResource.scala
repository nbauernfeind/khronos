package com.nefariouszhen.saturn.db

import javax.ws.rs.core.MediaType
import javax.ws.rs.{GET, Path, Produces}

import com.google.inject.Inject

@Path("/1/db")
@Produces(Array(MediaType.APPLICATION_JSON))
class DatabaseResource @Inject()(tsdb: TimeSeriesDatabase) {
  @GET
  @Path("/status")
  def status(): TimeSeriesDatabase.Status = tsdb.status
}
