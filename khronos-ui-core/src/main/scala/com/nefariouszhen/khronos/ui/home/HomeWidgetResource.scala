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

private class DatabaseWidget(val data: Multiplexus#Status) extends Widget[Multiplexus#Status] {
  val partial: String = "partials/widgets/db-panel.html"
  val title: String = "Khronos Status"
}

@Path("/1/ui/widgets/home")
@Produces(Array(MediaType.APPLICATION_JSON))
class HomeWidgetResource @Inject() (tsdb: Multiplexus) {
  @GET
  def getWidgets: Seq[Widget[_]] = Seq(
    new DatabaseWidget(tsdb.status),
    new BlankWidget,
    new BlankWidget,
    new BlankWidget,
    new BlankWidget,
    new BlankWidget
  )
}
