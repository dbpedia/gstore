package org.dbpedia.databus.dataidrepo

import org.dbpedia.databus.dataidrepo.handlers.DataIdUploadHandler

import javax.servlet.annotation.MultipartConfig
import org.scalatra._
import org.scalatra.servlet.FileUploadSupport
import org.scalatra.util.MapQueryString
import resource._

import scala.collection.JavaConverters._
import scala.io.{Codec, Source}
import scala.util.{Failure, Success}


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

    import DataIdRepo.UploadPartNames._

    def notMultiPart = request.contentType.fold(true) { ct => !(ct startsWith "multipart/") }

    if(notMultiPart) {
      halt(BadRequest(s"Multi-part request expected."))
    }

    if(!(expectedPartsForUpload subsetOf fileParams.keySet)) {
      halt(BadRequest(s"Missing required part(s): ${(expectedPartsForUpload -- fileParams.keySet).mkString(", ")}"))
    }

     val clientCert = authentication.getSingleCertFromContainer(request) match {

      case Failure(ex) => halt(BadRequest(s"Provision of a X509 client certificate expected, but so such certificate " +
        "could be retrieved due to:\n" + ex.toString))

      case Success(cert) => cert
    }

    val dataIdStream = managed(fileParams(dataId).getInputStream)

    val signature = fileParams(dataIdSignature).get()

    val uploadParamsMap = managed(fileParams(uploadParams).getInputStream) apply { is =>

      def paramsQueryString = Source.fromInputStream(is)(Codec.UTF8).mkString

      MapQueryString.parseString(paramsQueryString)
    }

    val handler = new DataIdUploadHandler(clientCert, dataIdStream, signature, uploadParamsMap)

    handler.response
  }
}

object DataIdRepo {

  lazy val expectedPartsForUpload = {

    import UploadPartNames._

    Set(dataId, dataIdSignature, uploadParams)
  }

  object UploadPartNames {

    val (dataId, dataIdSignature, uploadParams) = ("dataid", "dataid-signature", "upload-params")
  }

  object UploadParams {

    val (dataIdLocation, allowOverwrite) = ("DataIdLocation", "AllowOverwrite")
  }
}
