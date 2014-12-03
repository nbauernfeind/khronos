package com.nefariouszhen.khronos.db.index

import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.{Lock, ReentrantReadWriteLock}

import com.fasterxml.jackson.annotation.JsonTypeName
import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.google.inject.Inject
import com.nefariouszhen.khronos.KeyValuePair
import com.nefariouszhen.khronos.db.{TimeSeriesMapping, TimeSeriesMappingDAO}
import com.nefariouszhen.khronos.metrics.MetricsRegistry
import com.nefariouszhen.khronos.metrics.MetricsRegistry.Timer
import com.nefariouszhen.khronos.websocket.{WebSocketWriter, WebSocketRequest, WebSocketManager}
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

  val EXACT = """([a-zA-Z0-9]+):([a-zA-Z0-9]+)""".r
  val WILDCARD = """([a-zA-Z0-9]+:?[a-zA-Z0-9]*)\*""".r
  val KEY_ONLY = """([a-zA-Z0-9]*)""".r
  val SPLIT = """([a-zA-Z0-9]+):([a-zA-Z0-9]*)""".r
}

// Mustang is an index that makes it easier for a human to explore and concisely represent metrics.
class Mustang @Inject()(db: TimeSeriesMappingDAO, registry: MetricsRegistry) extends Managed {

  import com.nefariouszhen.khronos.db.index.Mustang._

  private[this] val tsFuture = new AtomicReference[Option[Closeable]](None)
  private[this] val rwLock = new ReentrantReadWriteLock

  private[this] val kvpMap = mutable.HashMap[KeyValuePair, ConcurrentContentGroup[TSID]]()
  private[this] val keyTrie = BurstTrie.newSet()
  private[this] val valTrie = BurstTrie.newMap[KeyValuePair]()

  private[this] val prefixCache = CacheBuilder.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build(new CacheLoader[String, Option[ContentGroup[TSID]]]() {
    override def load(prefix: String): Option[ContentGroup[TSID]] = {
      val res = ContentGroup.union(valTrie.query(prefix).flatMap(kvpMap.get).toIterable)
      if (res.size == 0) None else Some(res)
    }
  })

  /** MetricsIndex Metrics **/
  private[this] val metrics = new {
    import com.nefariouszhen.khronos.KeyValuePair._

    private[this] def newKey(typ: String) = List("type" -> typ, "app" -> "khronos", "system" -> "mustang")

    val numQueries = registry.newCounter(newKey("numQueries"))
    val resolveTm = registry.newTimer(newKey("resolveTm"))
    val queryTm = registry.newTimer(newKey("queryTm"))
    val lockWaitTm = registry.newTimer(newKey("lockWaitTm"))

    registry.newMeter(newKey("kvpMapSize"), kvpMap.size)
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

  def resolve(partial: String): ContentGroup[TSID] = acquire(rwLock.readLock, metrics.resolveTm) {
    val resOption = partial match {
      case WILDCARD(prefix) =>
        val res = prefixCache.get(prefix)
        if (res.isEmpty) prefixCache.invalidate(res)
        res
      case EXACT(key, value) => kvpMap.get(KeyValuePair(key, value))
      case _ => None
    }

    resOption.getOrElse(ContentGroup.empty)
  }

  def query(request: AutoCompleteRequest): Iterable[AutoCompleteResult] = acquire(rwLock.readLock, metrics.queryTm) {
    metrics.numQueries.increment()

    val resolvedFilters = request.tags.map(resolve)
    val filter = ContentGroup.intersection(resolvedFilters)
    if (resolvedFilters.nonEmpty && filter.isEmpty) return Iterable()

    val results = for (partial <- resolveCompletions(request.tagQuery)) yield {
      val group = resolve(partial)
      val intersection = if (request.tags.nonEmpty) ContentGroup.intersection(Array(filter, group)) else group
      AutoCompleteResult(partial, intersection.size)
    }

    results.filter(_.numMatches > 0).take(request.numResults.getOrElse(10)).toIterable
  }

  private[this] def resolveCompletions(partial: String): Iterator[String] = acquire(rwLock.readLock) {
    partial match {
      case KEY_ONLY(_) => keyTrie.query(partial).map(s => s"$s:*")
      case SPLIT(_, _) => valTrie.query(partial).map(s => s"$s")
      case WILDCARD(_) => Iterator(partial) // If manually specified, don't expand the wildcard.
      case _ => Iterator()
    }
  }

  private[this] def newTimeSeries(mapping: TimeSeriesMapping): Unit = acquire(rwLock.writeLock) {
    def newGroup(kvp: KeyValuePair): ConcurrentContentGroup[TSID] = {
      val keyIter = keyTrie.query(kvp.k)
      if (keyIter.isEmpty || keyIter.next != kvp.k) {
        keyTrie.put(kvp.k)
      }

      valTrie.put(kvp.toString, kvp)

      new ConcurrentContentGroup[TSID]
    }

    for (kvp <- mapping.kvps) {
      kvpMap.getOrElseUpdate(kvp, newGroup(kvp)) += mapping.id
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
