package org.dbpedia.databus

import com.apicatalog.jsonld.document.Document
import com.apicatalog.jsonld.http.media.MediaType
import com.apicatalog.jsonld.loader.{DocumentLoader, DocumentLoaderOptions}
import org.dbpedia.databus.CachingJsonldContexts.{AliasedDocument, ApproxSizeStringKeyCache}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import java.net.URI
import java.util.{Optional, UUID}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._

class CacheTests extends FlatSpec with Matchers with BeforeAndAfter {

  private val defaultTtl = 15.minutes

  private class StubDocument extends Document {
    var documentUrl: URI = _
    var contextUrl: URI = _
    override def getContentType(): MediaType = null
    override def getContextUrl(): URI = contextUrl
    override def getDocumentUrl(): URI = documentUrl
    override def getProfile(): Optional[String] = Optional.empty()
    override def setContextUrl(x$1: URI): Unit = contextUrl = x$1
    override def setDocumentUrl(x$1: URI): Unit = documentUrl = x$1
  }

  "CacheKey" should "be sorted by time of creation" in {

    val caches =
      Seq(
        new CachingJsonldContexts.StringCacheKey("scsc", 0),
        new CachingJsonldContexts.StringCacheKey("scsc", -10),
        new CachingJsonldContexts.StringCacheKey("scsc", 100),
        new CachingJsonldContexts.StringCacheKey("zzzz", 0),
        new CachingJsonldContexts.StringCacheKey("aaaa", 0)
      )

    caches.sorted.map(k => k.order) should contain theSameElementsInOrderAs (Seq(-10, 0, 0, 0, 100))

  }

  "CacheKey" should "be equal with same string" in {
    val re = new CachingJsonldContexts.StringCacheKey("scsc", 0) == new CachingJsonldContexts.StringCacheKey("scsc", -10)
    re should be(true)

    val re2 = new CachingJsonldContexts.StringCacheKey("scsc", 0) == new CachingJsonldContexts.StringCacheKey("aaaa", 0)
    re2 should be(false)
  }

  "ApproxSizeCache" should "not overflow the size" in {
    val cache = new ApproxSizeStringKeyCache[Int](10, defaultTtl)
    val seq = (1 to 100).map(i => (UUID.randomUUID().toString, i))
    seq.foreach(p => cache.put(p._1, p._2))

    cache.keysSorted.map(_.str) should contain theSameElementsInOrderAs (seq.drop(90).map(_._1))
  }

  "ApproxSizeCache" should "have same size for same string key" in {
    val cache = new ApproxSizeStringKeyCache[Int](10, defaultTtl)
    val seq = Seq("a", "a", "a")
    seq.foreach(p => cache.put(p, UUID.randomUUID().hashCode()))

    cache.keysSorted.size should be(1)
  }

  "ApproxSizeCache" should "expire entries after TTL" in {
    val cache = new ApproxSizeStringKeyCache[Int](10, 50.millis)
    cache.put("key", 42)
    cache.get("key") should be(Some(42))
    Thread.sleep(60)
    cache.get("key") should be(None)
  }

  "AliasingTtlDocumentCacheLoader" should "fetch remote URI and cache under local alias key" in {
    val localUri = "http://localhost:3000/res/context.jsonld"
    val remoteUri = "https://example.org/context.jsonld"
    val fetchCount = new AtomicInteger(0)
    val document = new StubDocument
    val backingLoader = new DocumentLoader {
      override def loadDocument(url: URI, options: DocumentLoaderOptions): Document = {
        fetchCount.incrementAndGet()
        url.toString should be(remoteUri)
        document
      }
    }
    val cache = new CachingJsonldContexts(32, defaultTtl)
    val loader = new AliasingTtlDocumentCacheLoader(cache, backingLoader, Map(localUri -> remoteUri))

    val returned = loader.loadDocument(new URI(localUri), new DocumentLoaderOptions())
    returned shouldBe a[AliasedDocument]
    returned.getDocumentUrl.toString should be(localUri)
    loader.loadDocument(new URI(localUri), new DocumentLoaderOptions()) should be(returned)
    document.getDocumentUrl should be(null)
    fetchCount.get() should be(1)
  }

  "AliasingTtlDocumentCacheLoader" should "reuse cached alias when remote URI is requested" in {
    val localUri = "http://localhost:3000/res/context.jsonld"
    val remoteUri = "https://example.org/context.jsonld"
    val fetchCount = new AtomicInteger(0)
    val document = new StubDocument
    val backingLoader = new DocumentLoader {
      override def loadDocument(url: URI, options: DocumentLoaderOptions): Document = {
        fetchCount.incrementAndGet()
        document
      }
    }
    val cache = new CachingJsonldContexts(32, defaultTtl)
    val loader = new AliasingTtlDocumentCacheLoader(cache, backingLoader, Map(localUri -> remoteUri))

    loader.loadDocument(new URI(localUri), new DocumentLoaderOptions())
    loader.loadDocument(new URI(remoteUri), new DocumentLoaderOptions()).getDocumentUrl.toString should be(remoteUri)
    fetchCount.get() should be(1)
  }

  "AliasingTtlDocumentCacheLoader" should "not cache failed loads" in {
    val uri = "https://example.org/context.jsonld"
    val fetchCount = new AtomicInteger(0)
    val document = new StubDocument
    val backingLoader = new DocumentLoader {
      override def loadDocument(url: URI, options: DocumentLoaderOptions): Document = {
        if (fetchCount.incrementAndGet() == 1) {
          throw new RuntimeException("temporary failure")
        }
        document
      }
    }
    val cache = new CachingJsonldContexts(32, defaultTtl)
    val loader = new AliasingTtlDocumentCacheLoader(cache, backingLoader, Map.empty)

    intercept[RuntimeException] {
      loader.loadDocument(new URI(uri), new DocumentLoaderOptions())
    }
    loader.loadDocument(new URI(uri), new DocumentLoaderOptions()) should be(document)
    fetchCount.get() should be(2)
  }

}
