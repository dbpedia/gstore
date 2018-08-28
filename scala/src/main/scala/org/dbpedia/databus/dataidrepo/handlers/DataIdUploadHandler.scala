package org.dbpedia.databus.dataidrepo.handlers

import org.dbpedia.databus.dataidrepo.DataIdRepo.UploadParams
import org.dbpedia.databus.shared.signing

import com.google.common.hash.Hashing
import org.scalatra.{Http => _, _}
import resource.ManagedResource
import scalaj.http.{Http, HttpConstants, HttpOptions}

import scalaz.Scalaz._
import scalaz._

import java.io.InputStream
import java.security.cert.X509Certificate

class DataIdUploadHandler(clientCert: X509Certificate, dataId: ManagedResource[InputStream],
  dataIdSignature: Array[Byte], uploadParams: Map[String, List[String]]) {

  lazy val verifyDataId: \/[ActionResult, String] = {

    wrapExceptionIntoInternalServerError("verifying signature") {
      dataId apply { dataIdStream =>

        signing.verifyInputStream(clientCert.getPublicKey, dataIdSignature, dataIdStream)
      }
    } flatMap {

      case false => BadRequest(s"The submitted DataId could not be verified against the provided signature").left

      case true => "DataId could be verified against provided signature".right
    }
  }

  def retrieveSingleUploadParam(paramName: String) = {

    uploadParams.get(paramName) match {

      case None => BadRequest(s"Missing upload parameter '$paramName'").left

      case Some(value :: Nil) => value.right

      case Some(values) if values.size > 1  => BadRequest(s"Multiple values for upload parameter '$paramName'").left
    }


  }

  lazy val dataIdWebLocation = retrieveSingleUploadParam(UploadParams.dataIdLocation)

  def wrapExceptionIntoInternalServerError[T](processDesc: String)(work: => T) = {
    \/.fromTryCatchNonFatal(work).leftMap { th =>
      InternalServerError(s"Error while $processDesc:\n$th")
    }
  }


  lazy val dataIdAccessibleAndMatches: \/[ActionResult, String] = {

    dataIdWebLocation flatMap { dataIdWebURL =>
      wrapExceptionIntoInternalServerError("testing accessibility of the DataId") {

        val request = Http(dataIdWebURL).options(HttpOptions.allowUnsafeSSL, HttpOptions.followRedirects(true))
          .header("Accept", "text/turtle")

        def readResponseBodyAsString(respHeaders: Map[String, IndexedSeq[String]], bodyStream: InputStream) = {

          val reqCharset: String = respHeaders.get("content-type").flatMap(_.headOption).flatMap(ct => {
            HttpConstants.CharsetRegex.findFirstMatchIn(ct).map(_.group(1))
          }).getOrElse(request.charset)

          HttpConstants.readString(bodyStream, reqCharset)
        }

        request.exec {
          case (200, _, is) => signing.hashInputStream(is, Hashing.sha256()).right
          case (errorCode, headers, is) if errorCode % 100 > 3 => readResponseBodyAsString(headers, is).left

        }
      }
    } flatMap { resp =>

      // we do not risk a match error here, since we would not enter this flatMap is dataIdWebLocation was -\/
      val \/-(dataIdWebURL) = dataIdWebLocation

      resp.body match {

        case -\/(errorMsg) => Conflict(s"DataId not accessible at location '$dataIdWebURL'.\n" +
          s"A test request yielded this error response:\n${resp.code}: $errorMsg").left

        case \/-(webLocationHashCode) => {

          val inRequestHashCode = dataId apply { signing.hashInputStream(_, Hashing.sha256()) }

          if(webLocationHashCode == inRequestHashCode) {
            s"Submitted DataId matches with the one available at '$dataIdWebURL'".right
          } else {
            Conflict(
              s"""
                 |Content mismatch between DataId submitted and the one available at '$dataIdWebURL'.
                 |sha256sum of submitted DataId: ${inRequestHashCode.toString}
                 |sha256sum of DataId at web location: ${webLocationHashCode.toString}
               """.stripMargin).left
          }
        }
      }
    }
  }

  lazy val response: ActionResult = {

    val run = for {
      verifyMsg <- verifyDataId
      webMatchMsg <- dataIdAccessibleAndMatches
    } yield Ok(Seq(verifyMsg, webMatchMsg).mkString("DataId successfully submitted.\n\n", "\n", "\n"))

    run.fold(identity, identity)
  }
}
