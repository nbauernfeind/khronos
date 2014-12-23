package com.nefariouszhen.khronos.ui

import scala.collection.mutable

class WidgetRegistry {
  private[this] val widgets = mutable.HashMap[String, Widget[_]]()

  def addWidget[T](w: Widget[T]): Unit = {
    if (widgets.contains(w.name)) {
      throw new IllegalArgumentException(s"Widget already registered w/name '${w.name}'")
    }
    widgets.put(w.name, w)
  }

  def getWidgets: Map[String, Widget[_]] = widgets.toMap
}
