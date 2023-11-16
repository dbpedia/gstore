package org.dbpedia.databus

import java.util.concurrent.ConcurrentHashMap

import com.github.jsonldjava.core.{Context, JsonLdOptions}
import org.dbpedia.databus.CachingJsonldContext.ApproxSizeStringKeyCache

import scala.collection.JavaConverters._

class CachingJsonldContext(sizeLimit: Int, opts: JsonLdOptions) extends Context(opts) {

  private val cache = new ApproxSizeStringKeyCache[Context](sizeLimit)

  override def parse(ctx: Object): Context =
    ctx match {
      case s: String =>
        cache.get(s)
          .map(c => super.parse(c))
          .getOrElse({
            val re = super.parse(ctx)
            cache.put(s, re)
            re
          })
      case _ => super.parse(ctx)
    }
  def putInCache(contextUri: String, ctx: Context) =
    cache.put(contextUri, ctx)

}

object CachingJsonldContext {

  // not the most efficient impl, but should work for now :)
  class ApproxSizeStringKeyCache[T](sizeLimit: Int) {
    private val cache = new ConcurrentHashMap[StringCacheKey, T](sizeLimit)

    def put(s: String, c: T) = {
      // not trying to keep the size strictly equal to the limit
      cache.put(new StringCacheKey(s), c)
      if (cache.size() > sizeLimit) {
        keysSorted
          .take(cache.size() - sizeLimit)
          .foreach(cache.remove)
      }
    }

    def get(s: String): Option[T] =
      Option(cache.get(new StringCacheKey(s)))

    def keysSorted: Seq[StringCacheKey] =
      cache.keySet()
        .asScala.toSeq.sorted

  }

  class StringCacheKey(val str: String, val order: Long = System.nanoTime()) extends Comparable[StringCacheKey] {
    override def equals(other: Any): Boolean = other match {
      case that: StringCacheKey => that.str == this.str
      case _ => false
    }

    override def hashCode(): Int = str.hashCode

    override def compareTo(o: StringCacheKey): Int = this.order.compareTo(o.order)
  }

}
