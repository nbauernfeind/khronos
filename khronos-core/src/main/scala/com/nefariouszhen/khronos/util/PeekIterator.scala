package com.nefariouszhen.khronos.util

class PeekIterator[T](iter: Iterator[T]) {
  private[this] var current: Option[T] = None

  // Initialize Peek Iterator
  primeNext()

  def isEmpty = !hasNext
  def hasNext: Boolean = current.isDefined

  def peek: T = current match {
    case Some(t) => t
    case None => throw new NoSuchElementException()
  }

  def next(): T = {
    val t = current.get
    primeNext()
    t
  }

  private[this] def primeNext() {
    current = if (iter.hasNext) Some(iter.next()) else None
  }
}
