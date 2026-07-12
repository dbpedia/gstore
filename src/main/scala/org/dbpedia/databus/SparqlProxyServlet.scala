package org.dbpedia.databus

import javax.servlet.http.HttpServletRequest
import org.eclipse.jetty.proxy.ProxyServlet
import org.slf4j.LoggerFactory

class SparqlProxyServlet extends ProxyServlet.Transparent {
  private val log = LoggerFactory.getLogger(getClass)

  override protected def rewriteTarget(request: HttpServletRequest): String = {
    val endpoint = Option(getInitParameter("sparqlEndpointUri"))
      .orElse(Option(getInitParameter("proxyTo")))
      .map(_.stripSuffix("/"))
      .orNull
    if (endpoint == null) return null

    val contextPath = request.getContextPath
    val requestUri = request.getRequestURI
    if (requestUri == null || !requestUri.startsWith(contextPath)) return null

    val extraPath = requestUri.substring(contextPath.length)
    val query = Option(request.getQueryString).map("?" + _).getOrElse("")
    val target = endpoint + extraPath + query
    log.debug(s"Proxy ${request.getMethod} $requestUri -> $target")
    target
  }
}
