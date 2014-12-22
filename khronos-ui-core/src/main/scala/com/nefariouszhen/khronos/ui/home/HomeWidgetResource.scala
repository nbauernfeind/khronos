package com.nefariouszhen.khronos.ui.home

import javax.ws.rs.core.MediaType
import javax.ws.rs.{GET, Path, Produces}

import com.google.inject.Inject
import com.nefariouszhen.khronos.ui.{Widget, WidgetRegistry}

private class MetricWidget extends Widget[Unit] {
  val partial: String = "partials/widgets/metric.html"
  val name: String = "Metric"
}

@Path("/1/ui/widgets")
@Produces(Array(MediaType.APPLICATION_JSON))
class HomeWidgetResource @Inject()(registry: WidgetRegistry) {
  registry.addWidget(new MetricWidget)

  @GET
  @Path("/all")
  def getWidgets: Seq[Widget[_]] = registry.getWidgets
}
