package org.dbpedia.databus.dataidrepo

import org.bouncycastle.openssl.PEMParser
import org.scalatra._

import scala.collection.JavaConverters._
import scala.util.Try

import java.io.StringReader


class DataIdRepo extends ScalatraServlet {

  get("/") {
    views.html.hello()
  }

  get("/headers") {

    val headerLines = request.getHeaderNames.asScala.toSeq.sorted.map { headerName =>

      def headerValues = request.getHeaders(headerName).asScala

      s"$headerName (${headerValues.size}): ${headerValues mkString "; "}"
    }

    headerLines.mkString("received headers:\n", "\n", "")
  }

  get("/client-cert-info") {

    val fromContainer = authentication.jca.getSingleCertFromContainer(request)
      .map(authentication.jca.describeX059Cert)

    val fromHeader = authentication.jca.getSingleCertFromHeader(request)
      .map(authentication.jca.describeX059Cert)

    s"""
      |client certificate from Servlet container:
      |$fromContainer
      |
      |client certificate extracted from HTTP Header "${settings.clientCertHeaderName}":
      |$fromHeader
    """.stripMargin
  }
}
