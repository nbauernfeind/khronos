package com.nefariouszhen.khronos.ui

import scala.collection.mutable

class WidgetRegistry {
  private[this] val widgets = mutable.ArrayBuffer[Widget[_]]()

  def addWidget[T](w: Widget[T]): Unit = {
    widgets += w
  }

  def getWidgets: Seq[Widget[_]] = widgets
}
