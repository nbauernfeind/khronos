package com.nefariouszhen.khronos.db.ram

import com.nefariouszhen.khronos.{Time, TimeSeriesPoint}
import org.junit.Test
import org.scalatest.Matchers

class InMemoryStorageTest extends Matchers {
  val startTime = Time(0)

  def tspStream(tm: Time = startTime): Stream[TimeSeriesPoint] = {
    TimeSeriesPoint(tm, tm.nanos / 100.0) #:: tspStream(Time(tm.nanos + 1))
  }

  class DataSet(val len: Int = 4, val tm: Time = startTime) {
    val storage = new InMemoryStorage(len)
    val writeIt = tspStream(tm).iterator

    val reader = storage.read(tm)
    val expectIt = tspStream(tm).iterator

    def write(n: Int) = take(n, writeIt).foreach(storage.write)
    def drop(n: Int) = take(n, expectIt).foreach(_ => ())
    def take(n: Int, it: Iterator[TimeSeriesPoint]) = (0 until n).map(_ => it.next())

    def expect(n: Int) = {
      val pointsRead = reader.toList
      pointsRead.length should equal(n)
      pointsRead should equal(take(n, expectIt).toList)
    }
  }

  @Test
  def shouldWriteContent(): Unit = new DataSet {
    write(len)
  }

  @Test
  def shouldReadContent(): Unit = new DataSet {
    write(len)
    expect(len)
  }

  @Test
  def shouldWrapAroundOldContent(): Unit = new DataSet {
    write(2 * len)
    drop(len)
    expect(len)
  }

  @Test
  def readerShouldWrapAround(): Unit = new DataSet {
    for (i <- 0 until 2) {
      write(len)
      expect(len)
    }
  }

  @Test
  def readerWriterDanceAround(): Unit = new DataSet(len = 3) {
    for (i <- 0 until 10) {
      write(2)
      expect(2)
    }
  }
}
