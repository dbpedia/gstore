package org.dbpedia.databus

import java.nio.file.Path

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.eclipse.jetty.proxy.ProxyServlet
import org.eclipse.jetty.rewrite.handler.Rule.ApplyURI
import org.eclipse.jetty.rewrite.handler.{RewriteHandler, RewriteRegexRule, Rule}
import org.eclipse.jetty.server.{HandlerContainer, Request}
import org.eclipse.jetty.server.handler.{ContextHandler, ResourceHandler}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHandler}

object JettyHelpers {

  def proxyContext(parent: HandlerContainer, virtUri: String, contextPath: String) = {
    val proxyContext = new ServletContextHandler(parent, contextPath, ServletContextHandler.SESSIONS)
    val handler = new ServletHandler
    val holder = handler.addServletWithMapping(classOf[ProxyServlet.Transparent], "/*")
    holder.setInitParameter("proxyTo", s"$virtUri$contextPath")
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
      "GET" -> new RewriteRegexRule(s"$prefix/(.*?)/(.*)",  "/graph/read?repo=$1&path=$2"),
      "POST" -> new RewriteRegexRule(s"$prefix/(.*?)/(.*)",  "/graph/save?repo=$1&path=$2"),
      "DELETE" -> new RewriteRegexRule(s"$prefix/(.*?)/(.*)",  "/graph/delete?repo=$1&path=$2"),
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
