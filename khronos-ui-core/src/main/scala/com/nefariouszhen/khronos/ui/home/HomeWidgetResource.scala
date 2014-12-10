package com.nefariouszhen.khronos.ui.home

import javax.ws.rs.core.MediaType
import javax.ws.rs.{GET, Produces, Path}

import com.google.inject.Inject
import com.nefariouszhen.khronos.db.Multiplexus
import com.nefariouszhen.khronos.ui.Widget

import scala.collection.mutable

private class BlankWidget extends Widget[Unit] {
  val partial: String = "partials/widgets/blank.html"
  val name: String = "Blank"
}

private class StatusWidget extends Widget[Unit] {
  val partial: String = "partials/widgets/status.html"
  val name: String = "Status"
}

private class MetricWidget extends Widget[Unit] {
  val partial: String = "partials/widgets/metric.html"
  val name: String = "Metric"
}

@Path("/1/ui/widgets")
@Produces(Array(MediaType.APPLICATION_JSON))
class HomeWidgetResource @Inject() (tsdb: Multiplexus) {
  private[this] val widgets = mutable.ArrayBuffer[Widget[_]]()

  def addWidget[T](w: Widget[T]): Unit = {
    widgets += w
  }

  addWidget(new BlankWidget)
  addWidget(new MetricWidget)

  @GET
  @Path("/all")
  def getWidgets: Seq[Widget[_]] = widgets
}
