package org.dbpedia.databus

import com.apicatalog.jsonld.context.cache.Cache
import com.apicatalog.jsonld.document.Document
import com.apicatalog.jsonld.loader.{DocumentLoader, DocumentLoaderOptions, JsonLdInit}

import java.util.concurrent.ConcurrentHashMap
import org.dbpedia.databus.CachingJsonldContexts.ApproxSizeStringKeyCache

import java.net.URI
import scala.collection.JavaConverters._

class CachingJsonldContexts(sizeLimit: Int) extends Cache[String, Document] {

  private val cache = new ApproxSizeStringKeyCache[Document](sizeLimit)

  override def containsKey(key: String): Boolean = cache.get(key).isDefined

  override def get(key: String): Document = cache.get(key).orNull

  override def put(key: String, value: Document): Unit = cache.put(key, value)
}

object CachingJsonldContexts {

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

class LRUDocumentCacheLoader(private val cache: Cache[String, Document], private val documentLoader: DocumentLoader) extends DocumentLoader {

  override def loadDocument(url: URI, options: DocumentLoaderOptions): Document = {
    val k = url.toString
    var result = cache.get(k)
    if (result == null) {
      result = documentLoader.loadDocument(url, options)
      cache.put(k, result)
    }
    result
  }

  def put(k: String, v: Document) = cache.put(k, v)

}
