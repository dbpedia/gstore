package org.dbpedia.databus

import org.apache.jena.sys.JenaSystem

import java.util.UUID
import org.dbpedia.databus.CachingJsonldContext.ApproxSizeStringKeyCache
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

class CacheTests extends FlatSpec with Matchers with BeforeAndAfter {

  before {
    JenaSystem.init()
  }
  after {
    JenaSystem.shutdown()
  }

  "CacheKey" should "be sorted by time of creation" in {

    val caches =
      Seq(
        new CachingJsonldContext.StringCacheKey("scsc", 0),
        new CachingJsonldContext.StringCacheKey("scsc", -10),
        new CachingJsonldContext.StringCacheKey("scsc", 100),
        new CachingJsonldContext.StringCacheKey("zzzz", 0),
        new CachingJsonldContext.StringCacheKey("aaaa", 0)
      )

    caches.sorted.map(k => k.order) should contain theSameElementsInOrderAs (Seq(-10, 0, 0, 0, 100))

  }

  "CacheKey" should "be equal with same string" in {
    val re = new CachingJsonldContext.StringCacheKey("scsc", 0) == new CachingJsonldContext.StringCacheKey("scsc", -10)
    re should be(true)

    val re2 = new CachingJsonldContext.StringCacheKey("scsc", 0) == new CachingJsonldContext.StringCacheKey("aaaa", 0)
    re2 should be(false)
  }

  "ApproxSizeCache" should "not overflow the size" in {
    val cache = new ApproxSizeStringKeyCache[Int](10)
    val seq = (1 to 100).map(i => (UUID.randomUUID().toString, i))
    seq.foreach(p => cache.put(p._1, p._2))

    cache.keysSorted.map(_.str) should contain theSameElementsInOrderAs (seq.drop(90).map(_._1))
  }

  "ApproxSizeCache" should "have same size for same string key" in {
    val cache = new ApproxSizeStringKeyCache[Int](10)
    val seq = Seq("a", "a", "a")
    seq.foreach(p => cache.put(p, UUID.randomUUID().hashCode()))

    cache.keysSorted.size should be(1)
  }


}
