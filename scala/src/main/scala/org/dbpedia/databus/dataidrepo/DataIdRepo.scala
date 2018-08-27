package org.dbpedia.databus.dataidrepo

import org.dbpedia.databus.shared.signing

import javax.servlet.annotation.MultipartConfig
import org.scalatra._
import org.scalatra.servlet.FileUploadSupport
import org.scalatra.util.MapQueryString
import resource._

import scala.collection.JavaConverters._
import scala.io.{Codec, Source}


@MultipartConfig(maxFileSize=10*1024*1024)
class DataIdRepo extends ScalatraServlet with FileUploadSupport {

  import DataIdRepo._

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

    val fromContainer = authentication.getSingleCertFromContainer(request)
      .map(authentication.describeX059Cert)

    val fromHeader = authentication.getSingleCertFromHeader(request)
      .map(authentication.describeX059Cert)

    s"""
      |client certificate from Servlet container:
      |$fromContainer
      |
      |client certificate extracted from HTTP Header "${settings.clientCertHeaderName}":
      |$fromHeader
    """.stripMargin
  }

  post("/dataid/upload") {

    def notMultiPart = request.contentType.fold(true) { ct => !(ct startsWith "multipart/") }

    if(notMultiPart) {
      halt(BadRequest(s"Multi-part request expected."))
    }

    if(!(expectedPartsForUpload subsetOf fileParams.keySet)) {
      halt(BadRequest(s"Missing required part(s): ${(expectedPartsForUpload -- fileParams.keySet).mkString(", ")}"))
    }

    val dataIdPart = fileParams("dataid")
    val signaturePart = fileParams("dataid-signature")
    val paramsQueryString = managed(fileParams("params").getInputStream) apply { is =>

      Source.fromInputStream(is)(Codec.UTF8).mkString
    }

    val formParams = MapQueryString.parseString(paramsQueryString)

    val clientCert = authentication.getSingleCertFromContainer(request)

    val publicKey = clientCert.map(_.getPublicKey)

    val verification = publicKey map { pubKey =>

      managed(dataIdPart.getInputStream) apply { dataIdStream =>

        signing.verifyInputStream(pubKey, signaturePart.get(), dataIdStream)
       }
    }

    s"""
      |so far, so good
      |formParams:
      |${formParams mkString "\n"}
      |client cert:
      |${clientCert.map(authentication.describeX059Cert)}
      |verification: $verification
      |that's all, folks!
    """.stripMargin
  }
}

object DataIdRepo {

  lazy val expectedPartsForUpload = Set("dataid", "dataid-signature", "params")
}
