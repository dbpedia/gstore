package org.dbpedia.databus // remember this package in the sbt project definition

import org.eclipse.jetty.server.{Handler, Server}
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.handler.ResourceHandler

import scala.util.Try
import scala.xml.XML

object JettyLauncher { // this is my entry object as specified in sbt project definition
  def main(args: Array[String]) {
    val port = if (System.getenv("PORT") != null) System.getenv("PORT").toInt else 8080

    println(s"Starting the service on port $port")

    val server = new Server(port)
    val context = new WebAppContext()

    val webXml = getClass.getResource("/WEB-INF/web.xml")
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


    val config = Option(webXml)
      .map(XML.load)
      .map(ApiImpl.Config.fromWebXml)
      .getOrElse(ApiImpl.Config.default)

    val fileListHandler = config.localGitReposRoot
      .flatMap(p => Try {
        val resourceHandler = new ResourceHandler
        resourceHandler.setResourceBase(p.toAbsolutePath.toString)
        resourceHandler.setDirectoriesListed(true)

        val contextHandler = new ContextHandler("/git")
        contextHandler.setHandler(resourceHandler)
        contextHandler
      }.toOption)


    val contexts = new ContextHandlerCollection
    val handlers = fileListHandler
        .map(h => Array[Handler](context, h))
        .getOrElse(Array[Handler](context))
    contexts.setHandlers(handlers)
    server.setHandler(contexts)

    server.start
    println("The service has been started.")
    server.join
  }
}