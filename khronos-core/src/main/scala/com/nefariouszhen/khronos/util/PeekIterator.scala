package com.nefariouszhen.khronos.util

class PeekIterator[T](iter: Iterator[T])(implicit ord: Ordering[T]) {
  private[this] var nextVal: Option[T] = None

  // Initialize Peek Iterator
  primeNext()

  def isEmpty = !hasNext
  def hasNext: Boolean = nextVal.isDefined

  def peek: T = nextVal match {
    case Some(t) => t
    case None => throw new NoSuchElementException()
  }

  def next(): T = {
    val ret = nextVal.get
    primeNext()
    ret
  }

  def nextIf(t: T): Unit = {
    while (hasNext && nextVal.get == t) {
      primeNext()
    }
  }

  def takeUntil(t: T): Unit = {
    while (hasNext && ord.compare(nextVal.get, t) < 0) {
      primeNext()
    }
  }

  private[this] def primeNext() {
    nextVal = if (iter.hasNext) Some(iter.next()) else None
  }
}
