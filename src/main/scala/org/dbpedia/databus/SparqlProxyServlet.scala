package org.dbpedia.databus

import java.io.{InputStream, OutputStream}
import java.net.{HttpURLConnection, URL}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class SparqlProxyServlet extends HttpServlet {
  private val log = LoggerFactory.getLogger(getClass)
  private val connectTimeoutMs = 5000
  private var endpointBase: String = _

  private val hopByHopHeaders = Set(
    "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
    "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length"
  )

  override def init(): Unit = {
    endpointBase = Option(getInitParameter("sparqlEndpointUri"))
      .orElse(Option(getInitParameter("proxyTo")))
      .map(_.stripSuffix("/"))
      .getOrElse(throw new javax.servlet.ServletException("sparqlEndpointUri is required"))
  }

  override def service(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    val contextPath = req.getContextPath
    val requestUri = Option(req.getRequestURI).getOrElse("")
    val extraPath =
      if (requestUri.startsWith(contextPath)) requestUri.substring(contextPath.length) else ""
    val query = Option(req.getQueryString).map("?" + _).getOrElse("")
    val targetUrl = endpointBase + extraPath + query

    var conn: HttpURLConnection = null
    try {
      conn = new URL(targetUrl).openConnection().asInstanceOf[HttpURLConnection]
      conn.setConnectTimeout(connectTimeoutMs)
      conn.setReadTimeout(JettyHelpers.DefaultTimeout.toMillis.toInt)
      conn.setInstanceFollowRedirects(false)
      conn.setRequestMethod(req.getMethod)
      conn.setDoInput(true)

      copyRequestHeaders(req, conn)

      if (req.getMethod == "POST" || req.getMethod == "PUT" || req.getMethod == "PATCH") {
        conn.setDoOutput(true)
        copyStream(req.getInputStream, conn.getOutputStream)
      }

      val status = conn.getResponseCode
      copyResponseHeaders(conn, resp)
      resp.setStatus(status)

      val body = Option(if (status >= 400) conn.getErrorStream else conn.getInputStream)
      body.foreach(stream => copyStream(stream, resp.getOutputStream))
    } catch {
      case e: Exception =>
        log.warn(s"SPARQL proxy failed for ${req.getMethod} $targetUrl: ${e.getMessage}")
        if (!resp.isCommitted) resp.sendError(HttpServletResponse.SC_BAD_GATEWAY, e.getMessage)
    } finally {
      if (conn != null) conn.disconnect()
    }
  }

  private def copyRequestHeaders(req: HttpServletRequest, conn: HttpURLConnection): Unit = {
    req.getHeaderNames.asScala.foreach { name =>
      if (!hopByHopHeaders.contains(name.toLowerCase)) {
        req.getHeaders(name).asScala.foreach(value => conn.addRequestProperty(name, value))
      }
    }
  }

  private def copyResponseHeaders(conn: HttpURLConnection, resp: HttpServletResponse): Unit = {
    var i = 1
    var continueLoop = true
    while (continueLoop) {
      val key = conn.getHeaderFieldKey(i)
      val value = conn.getHeaderField(i)
      if (value != null) {
        Option(key).filter(_.nonEmpty).foreach { headerName =>
          if (!hopByHopHeaders.contains(headerName.toLowerCase)) {
            resp.addHeader(headerName, value)
          }
        }
        i += 1
      } else {
        continueLoop = false
      }
    }
  }

  private def copyStream(in: InputStream, out: OutputStream): Unit = {
    val buffer = new Array[Byte](8192)
    var read = in.read(buffer)
    while (read != -1) {
      out.write(buffer, 0, read)
      read = in.read(buffer)
    }
    out.flush()
  }
}
