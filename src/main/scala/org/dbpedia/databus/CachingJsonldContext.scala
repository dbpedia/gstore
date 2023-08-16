package org.dbpedia.databus

import java.util.concurrent.ConcurrentHashMap
import java.net.URL

import com.github.jsonldjava.core.{Context, JsonLdOptions}
import org.dbpedia.databus.CachingJsonldContext.ApproxSizeStringKeyCache

import scala.collection.JavaConverters._

class CachingJsonldContext(sizeLimit: Int, opts: JsonLdOptions) extends Context(opts) {

  private val cache = new ApproxSizeStringKeyCache[Context](sizeLimit)

  private def isLocalhostURL(url: String): Boolean = {
    val localhostPatterns = List("http://localhost", "localhost", "127.0.0.1", "::1")
    localhostPatterns.exists(pattern => url.startsWith(pattern))
  }

  // Parse with fallback for localhost URLs. This can help for development when working with
  // context URLs on the localhost. Should a localhost request fail, the parser will
  // attempt to call the URL defined in GSTORE_LOCALHOST_CONTEXT_FALLBACK_URL instead.
  // (defaults to the docker host IP)
  private def tryParseWithFallback(s: String): Context = {
    try {
      val re = super.parse(s)
      cache.put(s, re)
      re
    } catch {
      case _: Exception if isLocalhostURL(s) =>
        val dockerHostURL = sys.env.getOrElse("GSTORE_LOCALHOST_CONTEXT_FALLBACK_URL", "http://172.17.0.1")
        val originalURL = new URL(s)
        val portPart = if (originalURL.getPort != -1) ":" + originalURL.getPort else ""
        val dockerHostContextURL = new URL(dockerHostURL + portPart + originalURL.getPath)
        cache.get(dockerHostContextURL.toString)
          .map(c => super.parse(c))
          .getOrElse({
            val re = super.parse(dockerHostContextURL.toString)
            cache.put(s, re)
            re
          })
      case _: Exception => super.parse(s)
    }
  }

  override def parse(ctx: Object): Context =
    ctx match {
      case s: String =>
        cache.get(s)
          .map(c => super.parse(c))
          .getOrElse(tryParseWithFallback(s))
      case _ => super.parse(ctx)
    }
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
