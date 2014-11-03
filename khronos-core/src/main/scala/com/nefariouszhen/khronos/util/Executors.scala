package com.nefariouszhen.khronos.util

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, ScheduledExecutorService, ThreadFactory}

import io.dropwizard.lifecycle.Managed
import org.slf4j.Logger

object Executors {
  private class DefaultThreadFactory extends ThreadFactory {
    def newThread(r: Runnable): Thread = new Thread(r)
  }

  private class NamedThreadFactory(name: String, threadFactory: ThreadFactory) extends ThreadFactory {
    private[this] val counter = new AtomicInteger()

    def newThread(r: Runnable): Thread = {
      val thread = threadFactory.newThread(r)
      thread.setName(name + "-" + counter.getAndIncrement)
      thread
    }
  }

  def newFixedThreadPool(name: String, numThreads: Int, threadFactory: Option[ThreadFactory] = None): ExecutorService = {
    val namedThreadFactory = new NamedThreadFactory(name, threadFactory.getOrElse(new DefaultThreadFactory()))
    java.util.concurrent.Executors.newFixedThreadPool(numThreads, namedThreadFactory)
  }

  def newCachedThreadPool(name: String, threadFactory: Option[ThreadFactory] = None): ExecutorService = {
    val namedThreadFactory = new NamedThreadFactory(name, threadFactory.getOrElse(new DefaultThreadFactory()))
    java.util.concurrent.Executors.newCachedThreadPool(namedThreadFactory)
  }

  def newScheduledThreadPool(name: String, numThreads: Int, threadFactory: Option[ThreadFactory] = None): ScheduledExecutorService = {
    val namedThreadFactory = new NamedThreadFactory(name, threadFactory.getOrElse(new DefaultThreadFactory()))
    java.util.concurrent.Executors.newScheduledThreadPool(numThreads, namedThreadFactory)
  }
}

class ManagedExecutor(executor: ExecutorService) extends Managed {
  def start() {}

  def stop() {
    executor.shutdownNow()
  }
}

class SafeRunnable(log: Logger, thunk: => Unit) extends Runnable {
  def run() {
    try {
      thunk
    } catch {
      case e: Exception =>
        log.error("Unexpected failure.", e)
    }
  }
}
