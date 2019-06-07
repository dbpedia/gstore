package org.dbpedia.databus.dataidrepo

import org.dbpedia.databus.dataidrepo.authentication.getAlternativeNameURIs
import org.dbpedia.databus.dataidrepo.config.DataIdRepoConfig
import org.dbpedia.databus.dataidrepo.handlers.DataIdUploadHandler
import org.dbpedia.databus.dataidrepo.rdf.Rdf
import org.dbpedia.databus.shared.DataIdUpload
import org.dbpedia.databus.shared.authentification.AccountHelpers

import javax.servlet.annotation.MultipartConfig
import org.apache.jena.rdf.model.Resource
import org.scalatra._
import org.scalatra.servlet.FileUploadSupport
import org.scalatra.util.MapQueryString
import resource._

import scala.collection.JavaConverters._
import scala.io.{Codec, Source}
import scala.util.{Failure, Success}


@MultipartConfig(maxFileSize = 10 * 1024 * 1024)
class DataIdRepo(implicit repoConfig: DataIdRepoConfig) extends ScalatraServlet with FileUploadSupport {

  implicit lazy val rdf = new Rdf()

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

    lazy val fromContainer = authentication.getSingleCertFromContainer(request)
      .map(authentication.describeX059Cert)

    lazy val fromHeader = authentication.getSingleCertFromHeader(request)
      .map(authentication.describeX059Cert)

    s"""
       |client certificate from Servlet container:
       |$fromContainer
    """.stripMargin
  }

  post("/dataid/upload") {

    import DataIdUpload.UploadPartNames._
    import DataIdUpload.expectedPartsForUpload

    def notMultiPart = request.contentType.fold(true) { ct => !(ct startsWith "multipart/") }

    if (notMultiPart) {
      val msg: Any = s"Multi-part request expected, but received ${request.contentType}.\n"
      halt(BadRequest(msg))
    }

    if (!(expectedPartsForUpload subsetOf fileParams.keySet)) {
      val msg: Any = s"Missing required part(s): ${(expectedPartsForUpload -- fileParams.keySet).mkString(", ")}\n"
      halt(BadRequest(msg))
    }


    val clientCert = authentication.getSingleCertFromContainer(request) match {

      case Failure(ex) => {
        val msg: Any = s".X509 client certificate expected, but no such certificate " +
          "could be retrieved, exception was:\n" + ex.toString + "\n" + request.toString + "\n"
        halt(BadRequest(msg))
      }

      case Success(cert) => cert
    }


    // validate DBpedia account
    //TODO take first element for now....
    val altNameDesc = try {
      getAlternativeNameURIs(clientCert).head
    } catch {
      case nse: NoSuchElementException => {
        val msg: Any = s"Method cert.getSubjectAlternativeNames.head (SAN) produced a NoSuchElementException\n" +
          s"The .X509 is probably missing the Webid URL in the SAN field\n" +
          s"fix with: regenerate the cert and put the full WebID (including #this) in the SubjectAlternativeName field\n"
        halt(BadRequest(msg))
      }
    }


    val account: Resource = AccountHelpers.getAccountOption(altNameDesc).getOrElse(
      if (repoConfig.requireDBpediaAccount) {

        val msg: Any = s"No DBpedia Account for ${altNameDesc} found (Origin: SAN field of .X509)\n" +
          s"fix with: register at https://github.com/dbpedia/accounts#how-to-get-an-account " +
          s"or switch to the testrepo at https://databus.dbpedia.org/repo-test\n"

        //NOTE this was a 401 at first, but 401 is not correct, also 400 does not return a body
        halt(Forbidden(msg))

        null
      } else {
        null
      }
    )


    val dataIdStream = managed(fileParams(dataId).getInputStream)

    val signature = fileParams(dataIdSignature).get()

    //todo: simplify this using `new String(fileParams(uploadParams).get(), StandardCharsets.UTF_8)`
    val uploadParamsMap = managed(fileParams(uploadParams).getInputStream) apply { is =>

      def paramsQueryString = Source.fromInputStream(is)(Codec.UTF8).mkString

      MapQueryString.parseString(paramsQueryString)
    }

    val handler = new DataIdUploadHandler(clientCert, dataIdStream, signature, uploadParamsMap, account.getURI)

    handler.response
  }
}

}
