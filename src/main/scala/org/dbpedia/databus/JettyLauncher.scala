package org.dbpedia.databus // remember this package in the sbt project definition

import java.net.URL

import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import org.eclipse.jetty.server.{Handler, Server}
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.handler.ResourceHandler

import scala.util.Try
import scala.xml.XML
import org.slf4j.LoggerFactory

object JettyLauncher { // this is my entry object as specified in sbt project definition

  val LOGGER: Logger =  LoggerFactory.getLogger(this.getClass).asInstanceOf[Logger]

  def main(args: Array[String]) {

    // handle config options
    val port = if (System.getProperty("PORT") != null) System.getProperty("PORT").toInt else 3002

    // locating webxml, allowing a local one
    val webXml: URL = getClass.getResource("/WEB-INF/web.xml")
    val webappDirLocation =
      if (webXml != null) {
        webXml.toString.replaceFirst("/WEB-INF/web.xml$", "/")
      } else {
        "src/main/webapp/"
      }


    // reading config
    // Priority
    // 1. read from CLI/System params
    // 2. else take webxml
    val config: ApiImpl.Config = Option(webXml)
      .map(XML.load)
      .map(ApiImpl.Config.fromWebXml)
      .getOrElse(ApiImpl.Config.default)

    // init server
    val server = new Server(port)
    val contextScalatra: WebAppContext = new WebAppContext()
    contextScalatra.setContextPath("/")
    contextScalatra.setResourceBase(webappDirLocation)
    contextScalatra.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false")
    contextScalatra.addEventListener(new ScalatraListener)
    contextScalatra.addServlet(classOf[DefaultServlet], "/")


    val fileListHandler: Option[ContextHandler] = config.gitLocalDir
      .flatMap(p => Try {
        val resourceHandler = new ResourceHandler
        resourceHandler.setResourceBase(p.toAbsolutePath.toString)
        resourceHandler.setDirectoriesListed(true)

        val contextHandler = new ContextHandler("/git")
        contextHandler.setHandler(resourceHandler)
        contextHandler
      }.toOption)


    val contexts = new ContextHandlerCollection
    // if successful, take both contexts, else only scalatra
    val handlers = fileListHandler
        .map(h => Array[Handler](contextScalatra, h))
        .getOrElse(Array[Handler](contextScalatra))
    contexts.setHandlers(handlers)
    server.setHandler(contexts)

    server.start
    LOGGER.info(
      s"""
         |The service has been started at port $port.
         |Useful URIs
         |* http://localhost:$port/git  <- fileviewer
         |* http://localhost:$port/
         |""".stripMargin)
    server.join
  }
}