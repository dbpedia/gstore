package org.dbpedia.databus // remember this package in the sbt project definition

import java.net.URL
import java.nio.file.{Files, Paths}

import org.eclipse.jetty.server.{Handler, NCSARequestLog, Server}
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.slf4j.LoggerFactory

import scala.util.Try
import scala.xml.XML

object JettyLauncher { // this is my entry object as specified in sbt project definition
  import JettyHelpers._

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]) {
    val port = if (System.getenv("GSTORE_JETTY_PORT") != null) System.getenv("GSTORE_JETTY_PORT").toInt else 8080
    val logBaseProp = "logsFolder"
    val logBase = Paths.get(Option(System.getProperty(logBaseProp))
      .getOrElse({
        System.setProperty(logBaseProp, "./logs/")
        System.getProperty(logBaseProp)
      }))
      .normalize()
      .toAbsolutePath
    if (!logBase.toFile.exists()) Files.createDirectories(logBase)

    log.info(s"Starting the service on port $port")

    val server = new Server(port)

    val webXml = getClass.getResource("/WEB-INF/web.xml")

    val config = Option(webXml)
      .map(XML.load)
      .map(ApiImpl.Config.fromWebXml)
      .getOrElse(ApiImpl.Config.default)

    val scalatraCtx = scalatraContext(webXml, config.restrictEditsToLocalhost)

    val browserPath = "/file"
    val fileListHandler = config.gitLocalDir
      .flatMap(p => Try(fileBrowserContext(p, browserPath)).toOption)

    val sparqlPath = "/sparql"
    val contexts = new ContextHandlerCollection
    val proxyCtx = proxyContext(contexts, config.storageSparqlEndpointUri.toString(), sparqlPath)
    val fhdl: List[Handler] = fileListHandler.map(List(_)).getOrElse(List.empty)
    val handlers: List[Handler] = fhdl ++ List[Handler](proxyCtx, scalatraCtx)

    contexts.setHandlers(handlers.toArray)

    val rewriteHandler = locationRewriteHandler(config.defaultGraphIdPrefix)
    rewriteHandler.setHandler(contexts)
    server.setHandler(rewriteHandler)

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
        |api is available under: http://localhost:${port}/
        |git repo: http://localhost:${port}${browserPath}
        |sparql endpoint: http://localhost:${port}${sparqlPath}
        |""".stripMargin)
    server.join
  }

  def scalatraContext(webXml: URL, restrictLocalhost: Boolean) = {
    val context = new WebAppContext()
    val webappDirLocation =
      if (webXml != null) {
        webXml.toString.replaceFirst("/WEB-INF/web.xml$", "/")
      } else {
        "src/main/webapp/"
      }

    //TODO may be a better solution, but works for now
    if (restrictLocalhost){
      import org.eclipse.jetty.server.handler.IPAccessHandler
      val ipaccess = new IPAccessHandler()
      ipaccess.setWhiteListByPath(true)
      ipaccess.addWhite("127.0.0.1|/graph/save")
      ipaccess.addWhite("127.0.0.1|/graph/delete")
      ipaccess.setHandler(context.getHandler)
      context.setHandler(ipaccess)
    }

    context.setContextPath("/")
    context.setResourceBase(webappDirLocation)
    context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    context.addEventListener(new ScalatraListener)
    context.addServlet(classOf[DefaultServlet], "/")
    context
  }

}