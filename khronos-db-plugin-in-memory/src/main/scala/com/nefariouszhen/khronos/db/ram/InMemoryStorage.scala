package com.nefariouszhen.khronos.db.ram

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

import com.nefariouszhen.khronos.{TimeSeriesPoint, Time}

class InMemoryStorage(len: Int) {
  private[this] val POINT_SIZE = 16

  def write(tsp: TimeSeriesPoint): Unit = write(tsp.tm, tsp.value)

  def write(tm: Time, value: Double): Unit = this.synchronized {
    val i = idx.getAndIncrement
    val offset = (i % len).toInt * POINT_SIZE
    writer.putLong(offset, tm.nanos)
    writer.putDouble(offset + 8, value)
  }

  def read(fromTime: Time): Iterator[TimeSeriesPoint] = read(fromTime, None)
  def read(fromTime: Time, toTm: Time): Iterator[TimeSeriesPoint] = read(fromTime, Some(toTm))

  private[this] def read(fromTm: Time, toTm: Option[Time]): Iterator[TimeSeriesPoint] = this.synchronized {
    new Iterator[TimeSeriesPoint] {
      private[this] var current: Option[TimeSeriesPoint] = None
      private[this] var nextIdx: Long = 0

      // Initialize Iterator
      primeNext()

      override def hasNext: Boolean = {
        if (current.isDefined) true else primeNext()
      }
      override def next(): TimeSeriesPoint = {
        val t = current.get
        primeNext()
        t
      }

      private[this] def primeNext(): Boolean = InMemoryStorage.this.synchronized {
        nextIdx = Math.max(nextIdx, idx.get() - len)
        if (idx.get() > nextIdx) {
          val offset = (nextIdx % len).toInt * POINT_SIZE
          nextIdx += 1

          val pt = TimeSeriesPoint(
            Time(storage.getLong(offset)),
            storage.getDouble(offset + 8)
          )

          current = Some(pt)
        } else {
          current = None
        }

        current.isDefined
      }
    }
  }

  private val storage = ByteBuffer.allocateDirect(len * POINT_SIZE)
  private val writer = storage.duplicate()
  private val idx = new AtomicLong()
}
