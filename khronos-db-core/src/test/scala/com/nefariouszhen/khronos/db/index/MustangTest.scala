package com.nefariouszhen.khronos.db.index

import java.util.concurrent.atomic.AtomicInteger

import com.google.inject
import com.google.inject.{Guice, Provides}
import com.nefariouszhen.khronos.KeyValuePair
import com.nefariouszhen.khronos.db.{TimeSeriesMapping, TimeSeriesMappingDAO}
import com.nefariouszhen.khronos.metrics.MetricsRegistry
import com.nefariouszhen.khronos.metrics.MetricsRegistry.Timer
import net.codingwell.scalaguice.ScalaModule
import org.junit.Test
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.{Matchers => TestMatchers}

import scala.reflect.ClassTag

class MustangTest extends TestMatchers {
  def beSorted[T: Ordering : ClassTag]: Matcher[Iterable[T]] = new Matcher[Iterable[T]] {
    override def apply(left: Iterable[T]): MatchResult = {
      val right = Array.ofDim[T](left.size)
      left.copyToArray(right)
      MatchResult(
        left == right.sorted.toIterable,
        left + " was not sorted but was expected to be.",
        left + " was sorted and expected it not to be.",
        Vector(left, right)
      )
    }
  }

  class DataSet {
    import net.codingwell.scalaguice.InjectorExtensions._

    val injector = Guice.createInjector(new ScalaModule {
      override def configure(): Unit = {
        binder.requireExplicitBindings()
        bind[Mustang].asEagerSingleton()
      }

      @Provides
      @inject.Singleton
      def mockRegistry(): MetricsRegistry = {
        val registry = mock(classOf[MetricsRegistry], RETURNS_DEEP_STUBS)
        when(registry.newTimer(Matchers.any())).thenReturn(new Timer)
        registry
      }

      @Provides
      @inject.Singleton
      def mockMappingDAO(): TimeSeriesMappingDAO = {
        val dao = mock(classOf[TimeSeriesMappingDAO])
        when(dao.timeSeries()).thenReturn(Iterable())
        dao
      }
    })

    val mustang = injector.instance[Mustang]
    mustang.start()

    val registerMapping = {
      val captor = ArgumentCaptor.forClass(classOf[TimeSeriesMapping => Unit])
      verify(injector.instance[TimeSeriesMappingDAO]).subscribe(captor.capture())
      captor.getValue
    }

    val nextId = new AtomicInteger()

    def registerMapping(kvps: KeyValuePair*): Unit = {
      registerMapping(TimeSeriesMapping(nextId.incrementAndGet, kvps))
    }

    def emptyQuery(numResults: Int = 10): Iterable[AutoCompleteResult] = {
      mustang.query(Iterable(), PartialQuery(""), numResults)
    }
  }

  @Test
  def shouldStartEmpty(): Unit = new DataSet {
    mustang.query(Iterable(), PartialQuery("")) should have size 0
  }

  @Test
  def shouldReturnKeysSorted(): Unit = new DataSet {
    val keys = List("type", "valtype", "app", "system", "units")
    for (key <- keys) {
      registerMapping(KeyValuePair(key, "a"))
      emptyQuery().map(_.q) should beSorted[String]
    }

    emptyQuery() should have size keys.size
  }

  @Test
  def shouldFilterKey(): Unit = new DataSet {
    val keys = List("type", "valtype", "app", "system", "units")
    for (key <- keys) {
      registerMapping(KeyValuePair(key, "a"))
    }

    for (key <- keys) {
      val result = mustang.query(Iterable(), PartialQuery(key.substring(0, 1)))
      result should have size 1
      result.head.sz should equal(1)
      result.head.q should startWith(key)
    }
  }

  @Test
  def shouldExpandKeyWithColon(): Unit = new DataSet {
    val keys = List("type", "valtype", "app", "system", "units")
    for (key <- keys) {
      registerMapping(KeyValuePair(key, "a"))
    }

    for (key <- keys) {
      val result = mustang.query(Iterable(), PartialQuery(s"$key:"))
      result should have size 1
      result.head.sz should equal(1)
      result.head.q should equal(s"$key:a")
    }
  }
}