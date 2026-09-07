package org.dbpedia.databus

import com.apicatalog.jsonld.context.cache.Cache
import com.apicatalog.jsonld.document.Document
import com.apicatalog.jsonld.http.media.MediaType
import com.apicatalog.jsonld.loader.{DocumentLoader, DocumentLoaderOptions}
import com.apicatalog.rdf.RdfDataset
import jakarta.json.JsonStructure
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentHashMap
import org.dbpedia.databus.CachingJsonldContexts.{AliasedDocument, ApproxSizeStringKeyCache, TimedEntry}

import java.net.URI
import java.util.Optional
import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

object JsonldContextLoaderLog {
  val logger = LoggerFactory.getLogger("gstore.jsonld")
}

class CachingJsonldContexts(sizeLimit: Int, ttl: FiniteDuration) extends Cache[String, Document] {

  private val cache = new ApproxSizeStringKeyCache[Document](sizeLimit, ttl)

  override def containsKey(key: String): Boolean = cache.get(key).isDefined

  override def get(key: String): Document = cache.get(key).orNull

  override def put(key: String, value: Document): Unit = cache.put(key, value)
}

object CachingJsonldContexts {

  case class TimedEntry[T](value: T, cachedAtNanos: Long)

  /** Presents a fetched document under an aliased URL without mutating the delegate (HttpLoader may cache it). */
  final class AliasedDocument private (
    val delegate: Document,
    documentUrl: URI,
    contextUrl: URI
  ) extends Document {
    override def getDocumentUrl(): URI = documentUrl
    override def getContextUrl(): URI = contextUrl
    override def getContentType(): MediaType = delegate.getContentType()
    override def getProfile(): Optional[String] = delegate.getProfile()
    override def getJsonContent(): Optional[JsonStructure] = delegate.getJsonContent()
    override def getRdfContent(): Optional[RdfDataset] = delegate.getRdfContent()
    override def setContextUrl(x$1: URI): Unit = delegate.setContextUrl(x$1)
    override def setDocumentUrl(x$1: URI): Unit = delegate.setDocumentUrl(x$1)
  }

  object AliasedDocument {
    def apply(delegate: Document, url: URI): AliasedDocument =
      new AliasedDocument(delegate, url, url)

    def forRequest(doc: Document, url: URI): Document = doc match {
      case aliased: AliasedDocument if aliased.getDocumentUrl == url => aliased
      case aliased: AliasedDocument => new AliasedDocument(aliased.delegate, url, url)
      case other => new AliasedDocument(other, url, url)
    }
  }

  // not the most efficient impl, but should work for now :)
  class ApproxSizeStringKeyCache[T](sizeLimit: Int, ttl: FiniteDuration) {
    private val cache = new ConcurrentHashMap[StringCacheKey, TimedEntry[T]](sizeLimit)
    private val ttlNanos = ttl.toNanos

    def put(s: String, c: T): Unit = {
      cache.put(new StringCacheKey(s), TimedEntry(c, System.nanoTime()))
      if (cache.size() > sizeLimit) {
        keysSorted
          .take(cache.size() - sizeLimit)
          .foreach(cache.remove)
      }
    }

    def get(s: String): Option[T] = {
      val key = new StringCacheKey(s)
      Option(cache.get(key)).flatMap { entry =>
        if (isExpired(entry)) {
          cache.remove(key)
          None
        } else {
          Some(entry.value)
        }
      }
    }

    def keysSorted: Seq[StringCacheKey] =
      cache.keySet()
        .asScala.toSeq.sorted

    private def isExpired(entry: TimedEntry[T]): Boolean =
      (System.nanoTime() - entry.cachedAtNanos) > ttlNanos
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

object AliasingTtlDocumentCacheLoader {
  private val LocalHosts = Set("localhost", "127.0.0.1", "[::1]", "::1")

  private[databus] def isLocalhost(uri: URI): Boolean =
    Option(uri.getHost).exists(h => LocalHosts.contains(h.toLowerCase))

  /** Replace localhost host with fallback base, keeping port and path. */
  private[databus] def rewriteLocalhostUrl(original: URI, fallbackBase: String): URI = {
    val base = fallbackBase.stripSuffix("/")
    val portPart = if (original.getPort != -1) s":${original.getPort}" else ""
    val path = Option(original.getRawPath).getOrElse("")
    new URI(s"$base$portPart$path")
  }
}

class AliasingTtlDocumentCacheLoader(
  private val cache: Cache[String, Document],
  private val documentLoader: DocumentLoader,
  aliases: Map[String, String],
  localhostFallbackBase: Option[String] = None
) extends DocumentLoader {

  private val log = JsonldContextLoaderLog.logger
  private val normalizedAliases = aliases.map { case (local, remote) => normalizeUri(local) -> normalizeUri(remote) }
  private val remoteToLocal: Map[String, String] = normalizedAliases.map { case (local, remote) => remote -> local }

  override def loadDocument(url: URI, options: DocumentLoaderOptions): Document = {
    val key = normalizeUri(url.toString)
    Option(cache.get(key)) match {
      case Some(doc) =>
        log.debug(
          s"JSON-LD context cache HIT url=$key documentUrl=${doc.getDocumentUrl} contextUrl=${doc.getContextUrl}"
        )
        doc
      case None =>
        loadMiss(key, url, options)
    }
  }

  private def loadMiss(key: String, url: URI, options: DocumentLoaderOptions): Document =
    normalizedAliases.get(key) match {
      case Some(remoteUri) =>
        log.debug(s"JSON-LD context ALIAS fetch local=$key remote=$remoteUri")
        loadAndCacheAlias(key, new URI(remoteUri), options)
      case None =>
        remoteToLocal.get(key) match {
          case Some(local) =>
            val canonical = Option(cache.get(local)).getOrElse {
              log.debug(s"JSON-LD context ALIAS canonical fetch local=$local source=$key")
              loadAndCacheAlias(local, url, options)
            }
            log.debug(s"JSON-LD context ALIAS reverse local=$local requested=$key")
            AliasedDocument.forRequest(canonical, url)
          case None =>
            loadDirect(key, url, options)
        }
    }

  private def loadDirect(key: String, url: URI, options: DocumentLoaderOptions): Document = {
    log.debug(s"JSON-LD context DIRECT fetch url=$key")
    try {
      val doc = documentLoader.loadDocument(url, options)
      log.debug(
        s"JSON-LD context DIRECT loaded url=$key documentUrl=${doc.getDocumentUrl} contextUrl=${doc.getContextUrl}"
      )
      cache.put(key, doc)
      doc
    } catch {
      case NonFatal(_) if localhostFallbackBase.isDefined && AliasingTtlDocumentCacheLoader.isLocalhost(url) =>
        val fallbackUri = AliasingTtlDocumentCacheLoader.rewriteLocalhostUrl(url, localhostFallbackBase.get)
        log.debug(s"JSON-LD context LOCALHOST fallback original=$key retry=$fallbackUri")
        loadAndCacheAlias(key, fallbackUri, options)
    }
  }

  private def loadAndCacheAlias(requestedKey: String, fetchUri: URI, options: DocumentLoaderOptions): Document = {
    val fetched = documentLoader.loadDocument(fetchUri, options)
    log.debug(
      s"JSON-LD context ALIAS source loaded fetchUri=$fetchUri documentUrl=${fetched.getDocumentUrl} contextUrl=${fetched.getContextUrl}"
    )
    val aliased = AliasedDocument(fetched, new URI(requestedKey))
    cache.put(requestedKey, aliased)
    log.debug(
      s"JSON-LD context ALIAS cached key=$requestedKey documentUrl=${aliased.getDocumentUrl} contextUrl=${aliased.getContextUrl}"
    )
    aliased
  }

  private def normalizeUri(uri: String): String =
    if (uri.endsWith("/")) uri.dropRight(1) else uri
}
