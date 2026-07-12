package org.dbpedia.databus

import org.eclipse.jetty.server.{Request, RequestLog, Response}
import org.slf4j.LoggerFactory

class RequestAccessLog extends RequestLog {
  private val log = LoggerFactory.getLogger("RequestLog")

  override def log(request: Request, response: Response): Unit = {
    val uri = request.getRequestURI
    val query = Option(request.getQueryString).filter(_.nonEmpty).map("?" + _).getOrElse("")
    val status = response.getStatus
    val bytes = response.getHttpChannel.getBytesWritten
    val latency = System.currentTimeMillis() - request.getTimeStamp
    log.info(s"${request.getMethod} $uri$query $status $bytes ${latency}ms")
  }
}
