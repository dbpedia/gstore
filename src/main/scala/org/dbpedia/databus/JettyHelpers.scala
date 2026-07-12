package org.dbpedia.databus

import java.nio.file.Path

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.eclipse.jetty.rewrite.handler.Rule.ApplyURI
import org.eclipse.jetty.rewrite.handler.{RewriteHandler, RewriteRegexRule, Rule}
import org.eclipse.jetty.server.{HandlerContainer, Request}
import org.eclipse.jetty.server.handler.{ContextHandler, ResourceHandler}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHandler}

import scala.concurrent.duration._

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
    holder.setInitParameter("proxyTo", endpoint)
    holder.setInitParameter("sparqlEndpointUri", endpoint)
    holder.setInitParameter("idleTimeout", DefaultTimeout.toMillis.toString)
    holder.setInitParameter("timeout", DefaultTimeout.toMillis.toString)
    proxyContext.setServletHandler(handler)
    proxyContext.setAllowNullPathInfo(true)
    proxyContext
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
