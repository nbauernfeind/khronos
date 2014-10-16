package com.nefariouszhen.saturn.db

import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheck.Result
import com.google.inject.Inject

class DatabaseHealthCheck @Inject()(tsdb: TimeSeriesDatabase) extends HealthCheck {
  def check(): Result = {
    if (!tsdb.status.isConnected) {
      Result.unhealthy("TSDB not connected.")
    } else {
      Result.healthy()
    }
  }
}
