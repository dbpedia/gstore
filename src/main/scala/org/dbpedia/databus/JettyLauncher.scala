package org.dbpedia.databus // remember this package in the sbt project definition

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener

object JettyLauncher { // this is my entry object as specified in sbt project definition
  def main(args: Array[String]) {
    val port = if (System.getenv("PORT") != null) System.getenv("PORT").toInt else 8080

    System.out.println(s"Starting the service on port $port")
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
    context.addEventListener(new ScalatraListener)
    context.addServlet(classOf[DefaultServlet], "/")

    server.setHandler(context)

    server.start
    System.out.println("The service has been started.")
    server.join
  }
}