package com.nefariouszhen.khronos.db.index

import java.util.concurrent.atomic.AtomicReference

import com.nefariouszhen.khronos.util.PeekIterator

import scala.collection.mutable
import scala.reflect.ClassTag

trait ContentGroup[T] {
  def size: Int

  def iterator: Iterator[T]

  def isEmpty: Boolean = size == 0
}

class FrozenContentGroup[T](sortedIds: Array[T]) extends ContentGroup[T] {
  def size: Int = sortedIds.length

  def iterator: Iterator[T] = sortedIds.iterator
}

class ContentGroupBuilder[T: ClassTag](implicit ord: Ordering[T]) {
  private[this] val idBuffer = mutable.ArrayBuffer[T]()

  def +=(id: T): Unit = {
    idBuffer += id
  }

  def build(): FrozenContentGroup[T] = new FrozenContentGroup(buildArray())

  protected def buildArray(): Array[T] = idBuffer.toArray.sorted
}

class ConcurrentContentGroup[T: ClassTag](implicit ord: Ordering[T])
  extends ContentGroupBuilder[T] with ContentGroup[T] {

  private[this] val sortedIds = new AtomicReference[Array[T]](Array())

  override def +=(id: T): Unit = {
    super.+=(id)
    sortedIds.set(buildArray())
  }

  def size = sortedIds.get.length

  def iterator = sortedIds.get.iterator
}

object ContentGroup {
  def empty[T: ClassTag]: ContentGroup[T] = new FrozenContentGroup[T](Array.empty[T])

  def union[T: ClassTag](gs: Iterable[ContentGroup[T]])(implicit ord: Ordering[T]): ContentGroup[T] = {
    val result = new ContentGroupBuilder[T]

    var iterators = gs.map(_.iterator).map(new PeekIterator(_)).filter(_.hasNext)

    while (iterators.nonEmpty) {
      val id = iterators.view.map(_.peek).min

      result += id

      iterators.foreach(_.nextIf(id))
      iterators = iterators.filter(_.hasNext)
    }

    result.build()
  }

  def intersection[T: ClassTag](gs: Iterable[ContentGroup[T]])(implicit ord: Ordering[T]): ContentGroup[T] = {
    val result = new ContentGroupBuilder[T]

    val iterators = gs.map(_.iterator).map(new PeekIterator(_))

    while (iterators != Nil && iterators.find(_.isEmpty).isEmpty) {
      val id = iterators.view.map(_.peek).min

      if (iterators.find(_.peek != id).isEmpty) {
        result += id
      }

      iterators.foreach(_.nextIf(id))
    }

    result.build()
  }

  def countIntersection[T: ClassTag](gs: Iterable[ContentGroup[T]])(implicit ord: Ordering[T]): Int = {
    var result = 0

    val iterators = gs.map(_.iterator).map(new PeekIterator(_))

    while (iterators != Nil && iterators.find(_.isEmpty).isEmpty) {
      val id = iterators.view.map(_.peek).min

      if (iterators.find(_.peek != id).isEmpty) {
        result += 1
      }

      iterators.foreach(_.nextIf(id))
    }

    result
  }
}