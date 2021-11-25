package org.dbpedia.databus // remember this package in the sbt project definition

import java.net.URL
import java.nio.file.{Files, Path, Paths}

import org.eclipse.jetty.proxy.ProxyServlet
import org.eclipse.jetty.server.{Handler, HandlerContainer, NCSARequestLog, Server}
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.handler.ResourceHandler
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHandler
import org.slf4j.LoggerFactory

import scala.util.Try
import scala.xml.XML

object JettyLauncher { // this is my entry object as specified in sbt project definition

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]) {
    val port = if (System.getenv("GSTORE_JETTY_PORT") != null) System.getenv("GSTORE_JETTY_PORT").toInt else 8080
    val logBaseProp = "logsFolder"
    val logBase = Paths.get(Option(System.getProperty(logBaseProp))
      .getOrElse(System.setProperty(logBaseProp, "./logs/")))
      .normalize()
      .toAbsolutePath
    if (!logBase.toFile.exists()) Files.createDirectories(logBase)

    log.info(s"Starting the service on port $port")

    val server = new Server(port)
    val webXml = getClass.getResource("/WEB-INF/web.xml")

    val scalatraCtx = scalatraContext(webXml)

    val config = Option(webXml)
      .map(XML.load)
      .map(ApiImpl.Config.fromWebXml)
      .getOrElse(ApiImpl.Config.default)

    val fileListHandler = config.gitLocalDir
      .flatMap(p => Try(fileBrowserContext(p)).toOption)

    val contexts = new ContextHandlerCollection
    val proxyCtx = proxyContext(contexts, config.virtuosoUri.toString())

    val handlers = fileListHandler
        .map(h => Array[Handler](scalatraCtx, proxyCtx, h))
        .getOrElse(Array[Handler](scalatraCtx, proxyCtx))
    contexts.setHandlers(handlers)
    server.setHandler(contexts)

    val requestLog = new NCSARequestLog(logBase.resolve("jetty-yyyy_mm_dd.request.log").toString)
    requestLog.setAppend(true)
    requestLog.setExtended(false)
    requestLog.setLogTimeZone("GMT")
    requestLog.setLogLatency(true)
    requestLog.setRetainDays(90)
    server.setRequestLog(requestLog)

    server.start
    log.info(
      s"""The service has been started.
        |api is available under: http://localhost:$port/
        |git repo: http://localhost:$port/git
        |sparql endpoint: http://localhost:$port/sparql
        |""".stripMargin)
    server.join
  }

  def proxyContext(parent: HandlerContainer, virtUri: String) = {
    val proxyContext = new ServletContextHandler(parent, "/sparql", ServletContextHandler.SESSIONS)
    val handler = new ServletHandler
    val holder = handler.addServletWithMapping(classOf[ProxyServlet.Transparent], "/*")
    holder.setInitParameter("proxyTo", s"$virtUri/sparql")
    proxyContext.setServletHandler(handler)
    proxyContext.setAllowNullPathInfo(true)
    proxyContext
  }

  def scalatraContext(webXml: URL) = {
    val context = new WebAppContext()
    val webappDirLocation =
      if (webXml != null) {
        webXml.toString.replaceFirst("/WEB-INF/web.xml$", "/")
      } else {
        "src/main/webapp/"
      }

    context.setContextPath("/")
    context.setResourceBase(webappDirLocation)
    context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    context.addEventListener(new ScalatraListener)
    context.addServlet(classOf[DefaultServlet], "/")
    context
  }

  def fileBrowserContext(fileRoot: Path) = {
    val resourceHandler = new ResourceHandler
    resourceHandler.setResourceBase(fileRoot.toAbsolutePath.toString)
    resourceHandler.setDirectoriesListed(true)

    val contextHandler = new ContextHandler("/git")
    contextHandler.setHandler(resourceHandler)
    contextHandler
  }

}