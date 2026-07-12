package org.dbpedia.databus

import java.net.{HttpURLConnection, URL}
import java.nio.file.Path

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.eclipse.jetty.rewrite.handler.Rule.ApplyURI
import org.eclipse.jetty.rewrite.handler.{RewriteHandler, RewriteRegexRule, Rule}
import org.eclipse.jetty.server.{HandlerContainer, Request}
import org.eclipse.jetty.server.handler.{ContextHandler, ResourceHandler}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHandler}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.Try

object JettyHelpers {

  val DefaultTimeout = 1 hour

  def normalizeSparqlEndpoint(uri: String): String = {
    val trimmed = uri.stripSuffix("/")
    val parsed = java.net.URI.create(trimmed)
    val path = Option(parsed.getRawPath).filter(p => p.nonEmpty && p != "/").getOrElse("/sparql")
    val port = parsed.getPort match {
      case -1 => parsed.getScheme match {
        case "https" => ":443"
        case _ => ":80"
      }
      case p => s":$p"
    }
    s"${parsed.getScheme}://${parsed.getHost}$port$path"
  }

  def proxyContext(parent: HandlerContainer, virtUri: String, contextPath: String) = {
    val endpoint = normalizeSparqlEndpoint(virtUri)
    val proxyContext = new ServletContextHandler(parent, contextPath, ServletContextHandler.SESSIONS)
    val handler = new ServletHandler
    val holder = handler.addServletWithMapping(classOf[SparqlProxyServlet], "/*")
    holder.setInitParameter("sparqlEndpointUri", endpoint)
    proxyContext.setServletHandler(handler)
    proxyContext.setAllowNullPathInfo(true)
    proxyContext
  }

  def waitForSparqlBackend(endpoint: String, attempts: Int = 30, delayMs: Long = 2000): Unit = {
    val log = LoggerFactory.getLogger("JettyLauncher")
    val host = java.net.URI.create(endpoint).getHost
    Try(java.net.InetAddress.getByName(host)).foreach { addr =>
      log.info(s"SPARQL backend host $host resolves to ${addr.getHostAddress}")
    }.failed.foreach(e => log.warn(s"SPARQL backend host $host does not resolve: ${e.getMessage}"))

    val ready = (1 to attempts).exists { attempt =>
      Try {
        val conn = new URL(endpoint).openConnection().asInstanceOf[HttpURLConnection]
        conn.setConnectTimeout(2000)
        conn.setReadTimeout(2000)
        conn.setRequestMethod("GET")
        val code = conn.getResponseCode
        conn.disconnect()
        log.info(s"SPARQL backend reachable at $endpoint (HTTP $code)")
        true
      }.recover {
        case e: Exception =>
          log.warn(s"Waiting for SPARQL backend at $endpoint (attempt $attempt/$attempts): ${e.getMessage}")
          Thread.sleep(delayMs)
          false
      }.get
    }

    if (!ready) {
      log.warn(s"SPARQL backend not reachable at $endpoint after $attempts attempts")
    }
  }

  def fileBrowserContext(fileRoot: Path, fileBrowserPath: String) = {
    val resourceHandler = new ResourceHandler
    resourceHandler.setResourceBase(fileRoot.toAbsolutePath.toString)
    resourceHandler.setDirectoriesListed(true)

    val contextHandler = new ContextHandler(fileBrowserPath)
    contextHandler.setHandler(resourceHandler)
    contextHandler
  }

  def locationRewriteHandler(prefix: String) = {
    val rewriteHandler = new RewriteHandler()
    val getRule = new GstoreRewriteRule(prefix)
    rewriteHandler.addRule(getRule)
    rewriteHandler
  }


  class GstoreRewriteRule(prefix: String) extends Rule with ApplyURI {

    private val RequestRuleMapping = Map(
      "GET" -> new RewriteRegexRule(s"$prefix/(.*?)/(.*)",  "/document/read?repo=$1&path=$2"),
    )

    override def matchAndApply(target: String, request: HttpServletRequest, response: HttpServletResponse): String =
      RequestRuleMapping.get(request.getMethod)
        .map(_.matchAndApply(target, request, response))
        .orNull

    override def applyURI(request: Request, oldURI: String, newURI: String): Unit =
      RequestRuleMapping.get(request.getMethod)
        .foreach(_.applyURI(request, oldURI, newURI))

  }

}
