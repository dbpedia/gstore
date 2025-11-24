package org.dbpedia.databus

import java.nio.file.Path
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.eclipse.jetty.server.Handler

object FileBrowserContext {

  def apply(fileRoot: Path, contextPath: String): Handler = {
    val context = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath(contextPath)
    context.addServlet(new ServletHolder(new FileListingServlet(fileRoot)), "/*")
    context
  }
}
