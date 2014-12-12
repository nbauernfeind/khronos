package com.nefariouszhen.khronos.db.index

import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.{Lock, ReentrantReadWriteLock}

import com.fasterxml.jackson.annotation.JsonTypeName
import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.google.inject.Inject
import com.nefariouszhen.khronos.db.{TimeSeriesMapping, TimeSeriesMappingDAO}
import com.nefariouszhen.khronos.metrics.MetricsRegistry
import com.nefariouszhen.khronos.metrics.MetricsRegistry.Timer
import com.nefariouszhen.khronos.websocket.{WebSocketManager, WebSocketRequest, WebSocketWriter}
import com.nefariouszhen.khronos.{ExactKeyTag, ContentTag, ExactTag, WildcardKeyTag, WildcardTag}
import com.nefariouszhen.trie.BurstTrie
import io.dropwizard.lifecycle.Managed

import scala.collection.mutable

object AutoCompleteResult {
  def apply(t: Tuple2[String, Int]): AutoCompleteResult = AutoCompleteResult(t._1, t._2)
}

case class AutoCompleteResult(tag: String, numMatches: Int)

@JsonTypeName("metric-typeahead")
case class AutoCompleteRequest(tags: Seq[String], tagQuery: String, numResults: Option[Int]) extends WebSocketRequest

object Mustang {
  type TSID = Int
}

// Mustang is an index that makes it easier for a human to explore and concisely represent metrics.
class Mustang @Inject()(db: TimeSeriesMappingDAO, registry: MetricsRegistry) extends Managed {

  import com.nefariouszhen.khronos.db.index.Mustang._

  private[this] val tsFuture = new AtomicReference[Option[Closeable]](None)
  private[this] val rwLock = new ReentrantReadWriteLock

  private[this] val tagMap = mutable.HashMap[ContentTag, ConcurrentContentGroup[TSID]]()
  private[this] val keyTrie = BurstTrie.newSet()
  private[this] val valTrie = BurstTrie.newMap[ExactTag]()

  private[this] val prefixCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build(new CacheLoader[String, Option[ContentGroup[TSID]]]() {
    override def load(prefix: String): Option[ContentGroup[TSID]] = {
      val res = ContentGroup.union(valTrie.query(prefix).flatMap(tagMap.get).toIterable)
      if (res.size == 0) None else Some(res)
    }
  })

  /** MetricsIndex Metrics **/
  private[this] val metrics = new {

    import com.nefariouszhen.khronos.ContentTag._

    private[this] def newKey(typ: String) = List("type" -> typ, "app" -> "khronos", "system" -> "mustang")

    val numQueries = registry.newCounter(newKey("numQueries"))
    val resolveTm = registry.newTimer(newKey("resolveTm"))
    val queryTm = registry.newTimer(newKey("queryTm"))
    val intersectTm = registry.newTimer(newKey("intersectTm"))
    val lockWaitTm = registry.newTimer(newKey("lockWaitTm"))

    registry.newMeter(newKey("kvpMapSize"), tagMap.size)
    registry.newMeter(newKey("prefixCacheSize"), prefixCache.size)
  }

  @Inject
  private[this] def registerWebHooks(ws: WebSocketManager): Unit = {
    ws.registerCallback(onIndexRequest)
  }

  private[this] def onIndexRequest(writer: WebSocketWriter, request: AutoCompleteRequest): Unit = {
    writer.write(query(request))
  }

  override def start(): Unit = acquire(rwLock.writeLock) {
    tsFuture.getAndSet(Some(db.subscribe(newTimeSeries))).map(_.close())
    db.timeSeries().foreach(newTimeSeries)
  }

  override def stop(): Unit = acquire(rwLock.writeLock) {
    tsFuture.getAndSet(None).map(_.close())
    prefixCache.invalidateAll()
  }

  def resolve(tag: ContentTag): ContentGroup[TSID] = acquire(rwLock.readLock, metrics.resolveTm) {
    def forPrefix(prefix: String): Option[ContentGroup[TSID]] = {
      val res = prefixCache.get(prefix)
      if (res.isEmpty) prefixCache.invalidate(res)
      res
    }

    val resOption = tag match {
      case _: ExactTag => tagMap.get(tag)
      case WildcardKeyTag(prefix) => forPrefix(prefix)
      case WildcardTag(key, prefix) => forPrefix(s"$key:$prefix")
      case _ => None
    }

    resOption.getOrElse(ContentGroup.empty)
  }

  def query(request: AutoCompleteRequest): Iterable[AutoCompleteResult] = acquire(rwLock.readLock, metrics.queryTm) {
    metrics.numQueries.increment()

    val resolvedFilters = request.tags.map(ContentTag.apply).map(resolve)
    val filter = ContentGroup.intersection(resolvedFilters)
    if (resolvedFilters.nonEmpty && filter.isEmpty) return Iterable()

    val results = for (tag <- resolveCompletions(ContentTag(request.tagQuery))) yield {
      val group = resolve(tag)
      val sz = if (request.tags.nonEmpty) ContentGroup.countIntersection(Array(filter, group)) else group.size
      AutoCompleteResult(tag.toTagString, sz)
    }

    results.filter(_.numMatches > 0).take(request.numResults.getOrElse(10)).toIterable
  }

  def query(tags: Seq[ContentTag]): ContentGroup[TSID] = acquire(rwLock.readLock, metrics.intersectTm) {
    ContentGroup.intersection(tags.map(resolve))
  }

  private[this] def resolveCompletions(partial: ContentTag): Iterator[ContentTag] = acquire(rwLock.readLock) {
    partial match {
      case WildcardKeyTag(prefix) => keyTrie.query(prefix).map(k => WildcardTag(k, ""))
      case WildcardTag(key, prefix) => valTrie.query(s"$key:$prefix")
      case ExactKeyTag(prefix) => valTrie.query(prefix)
      case ExactTag(key, prefix) => valTrie.query(s"$key:$prefix")
      case _ => Iterator()
    }
  }

  private[this] def newTimeSeries(mapping: TimeSeriesMapping): Unit = acquire(rwLock.writeLock) {
    def newGroup(tag: ExactTag): ConcurrentContentGroup[TSID] = {
      val keyIter = keyTrie.query(tag.k)
      if (keyIter.isEmpty || keyIter.next != tag.k) {
        keyTrie.put(tag.k)
      }

      valTrie.put(tag.toTagString, tag)

      new ConcurrentContentGroup[TSID]
    }

    for (tag <- mapping.tags) {
      tagMap.getOrElseUpdate(tag, newGroup(tag)) += mapping.id
    }
  }

  private[this] def acquire[T](l: Lock, timer: Timer = null)(thunk: => T): T = {
    try {
      metrics.lockWaitTm.time {
        l.lock()
      }
      if (timer != null) timer.time {
        thunk
      } else {
        thunk
      }
    } finally {
      l.unlock()
    }
  }
}
