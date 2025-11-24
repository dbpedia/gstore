package org.dbpedia.databus

import java.nio.file.{Files, Path}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.eclipse.jetty.server.Request
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._
import scala.collection.JavaConverters._

case class FileEntry(name: String, isDir: Boolean)

class FileListingServlet(fileRoot: Path) extends HttpServlet {
  implicit val formats: Formats = DefaultFormats

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    val acceptHeader = Option(req.getHeader("Accept")).getOrElse("").toLowerCase
    val pathParam = Option(req.getPathInfo).getOrElse("/").stripPrefix("/")
    val target = fileRoot.resolve(pathParam).normalize()

    if (!Files.exists(target)) {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND)
      req.asInstanceOf[Request].setHandled(true)
      return
    }

    if (Files.isDirectory(target)) {
      val entries = Files.list(target).iterator().asScala
        .filterNot(p => p.getFileName.toString.startsWith("."))
        .toSeq
        .map { p =>
          FileEntry(p.getFileName.toString, Files.isDirectory(p))
        }

      if (acceptHeader.contains("application/json") || acceptHeader.contains("application/hal+json")) {
        val json = ("_links" -> ("self" -> ("href" -> req.getRequestURI))) ~
                  ("_embedded" -> ("items" -> entries.map { e =>
                      ("name" -> e.name) ~
                      ("type" -> (if (e.isDir) "folder" else "file")) ~
                      ("_links" -> ("self" -> ("href" -> s"${req.getRequestURI.stripSuffix("/")}/${e.name}")))
                    }))
        resp.setContentType("application/json")
        resp.getWriter.print(compact(render(json)))
      } else {
        // HTML fallback
        resp.setContentType("text/html;charset=UTF-8")
        val writer = resp.getWriter
        writer.println(s"<html><head><title>Index of ${req.getRequestURI}</title></head><body>")
        writer.println(s"<h1>Index of ${req.getRequestURI}</h1>")
        writer.println("<ul>")

        if (target.getParent != null && target.getParent.startsWith(fileRoot)) {
          val parentPath = fileRoot.relativize(target.getParent).toString.replace("\\", "/")
          writer.println(s"""<li><a href="${req.getContextPath}/${parentPath}">.. (parent)</a></li>""")
        }

        entries.sortBy(_.name.toLowerCase).foreach { e =>
          val href = req.getRequestURI.stripSuffix("/") + "/" + e.name
          val display = if (e.isDir) s"${e.name}/" else e.name
          writer.println(s"""<li><a href="$href">$display</a></li>""")
        }

        writer.println("</ul></body></html>")
      }
    } else {
      // It's a file, stream content
      val mime = Option(Files.probeContentType(target)).getOrElse("text/plain")
      resp.setContentType(mime)
      resp.setContentLengthLong(Files.size(target))
      
      val in = Files.newInputStream(target)
      try {
        val out = resp.getOutputStream
        val buffer = new Array[Byte](1024 * 8)
        var bytesRead = 0
        while ({ bytesRead = in.read(buffer); bytesRead != -1 }) {
          out.write(buffer, 0, bytesRead)
        }
        out.flush()
      } finally in.close()
    }

    req.asInstanceOf[Request].setHandled(true)
  }
}
