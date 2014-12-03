package com.nefariouszhen.khronos.ui.home

import javax.ws.rs.core.MediaType
import javax.ws.rs.{GET, Produces, Path}

import com.google.inject.Inject
import com.nefariouszhen.khronos.db.Multiplexus
import com.nefariouszhen.khronos.ui.Widget

private class BlankWidget extends Widget[Unit] {
  val data: Unit = {}
  val partial: String = "partials/widgets/blank.html"
  val title: String = "Sample Title"
}

private class StatusWidget extends Widget[Unit] {
  val data: Unit = {}
  val partial: String = "partials/widgets/status.html"
  val title: String = "Khronos Status"
}

private class MetricWidget extends Widget[Unit] {
  val data: Unit = {}
  val partial: String = "partials/widgets/metric.html"
  val title: String = "Metric"
}

@Path("/1/ui/widgets/home")
@Produces(Array(MediaType.APPLICATION_JSON))
class HomeWidgetResource @Inject() (tsdb: Multiplexus) {
  @GET
  def getWidgets: Seq[Widget[_]] = Seq(
    new StatusWidget,
    new MetricWidget,
    new BlankWidget,
    new BlankWidget,
    new BlankWidget,
    new BlankWidget
  )
}
