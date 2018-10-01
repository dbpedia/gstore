package org.dbpedia.databus.dataidrepo.handlers

import org.dbpedia.databus.dataidrepo.config.PersistenceStrategy
import org.dbpedia.databus.dataidrepo.helpers._
import org.dbpedia.databus.dataidrepo.helpers.conversions._
import org.dbpedia.databus.dataidrepo.models.DataIdMetadata
import org.dbpedia.databus.dataidrepo.rdf._
import org.dbpedia.databus.dataidrepo.rdf.conversions._
import org.dbpedia.databus.dataidrepo.{config, models}
import org.dbpedia.databus.shared.helpers.conversions.TapableW
import org.dbpedia.databus.shared.signing
import org.dbpedia.databus.shared.DataIdUpload.UploadParams

import better.files._
import com.google.common.hash.Hashing
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.scalactic.Snapshots._
import org.scalatra.{Http => _, _}
import resource.{ManagedResource, _}
import scalaj.http.{Http, HttpConstants, HttpOptions}
import scalaz.Scalaz._
import scalaz._

import scala.util.Try

import java.io.InputStream
import java.nio.file.attribute.PosixFilePermission._
import java.security.cert.X509Certificate

class DataIdUploadHandler(clientCert: X509Certificate, dataId: ManagedResource[InputStream],
  dataIdSignature: Array[Byte], uploadParams: Map[String, List[String]]) extends LazyLogging {

  trait Technical

  type TechnicalError = ActionResult @@ Technical

  def Technical[A](a: A) = Tag[A, Technical](a)

  def dataIdWebLocation = retrieveSingleUploadParam(UploadParams.dataIdLocation)

  def dataIdIdentifier = retrieveSingleUploadParam(UploadParams.dataIdIdentifier)

  def dataIdVersion = retrieveSingleUploadParam(UploadParams.dataIdVersion)

  def dataIdSignatureVerified: TechnicalError \/ ValidationResult = {

    wrapExceptionIntoInternalServerError("verifying signature") {
      dataId apply { dataIdStream =>

        signing.verifyInputStream(clientCert.getPublicKey, dataIdSignature, dataIdStream)
      }
    } flatMap {

      case false => Error(s"The submitted DataId could not be verified against the provided signature").right

      case true => Pass("DataId could be verified against provided signature").right
    }
  }

  def dataIdAccessibleAndMatches: TechnicalError \/ ValidationResult = {

    dataIdWebLocation flatMap { dataIdWebURL =>
      wrapExceptionIntoInternalServerError("testing accessibility of the DataId") {

        val request = Http(dataIdWebURL)
          .options(HttpOptions.allowUnsafeSSL, HttpOptions.followRedirects(true))
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

        case -\/(errorMsg) => Warning(s"DataId not accessible at location '$dataIdWebURL'.",
          s"A test request yielded this error response:\n${resp.code}: $errorMsg").right

        case \/-(webLocationHashCode) => {

          val inRequestHashCode = dataId apply {
            signing.hashInputStream(_, Hashing.sha256())
          }

          if(webLocationHashCode == inRequestHashCode) {
            Pass(s"Submitted DataId matches with the one available at '$dataIdWebURL'").right
          } else {
            Warning(s"Content mismatch between DataId submitted and the one available at '$dataIdWebURL'.",
              s"""
                 |sha256sum of submitted DataId: ${inRequestHashCode.toString}
                 |sha256sum of DataId at web location: ${webLocationHashCode.toString}
               """.stripMargin).right
          }
        }
      }
    }
  }

  def validations = Validations(dataIdSignatureVerified, dataIdAccessibleAndMatches, dataIdIsWellformedRDF)

  def dataIdIsWellformedRDF: TechnicalError \/ ValidationResult = {

    wrapExceptionIntoInternalServerError("reading submitted DataId RDF") {

      dataId.apply { dataIdStream =>

        Try({
          ModelFactory.createDefaultModel() tap { model =>
            RDFDataMgr.read(model, dataIdStream, Lang.TURTLE)
          }
        }).map(PassWithResult("Submitted DataId is well-formed RDF", _))
          .recover({
            //todo: match more specifically for the exceptions from Jena that are parse exceptions
            case th: Throwable => Error("Error parsing DataID RDF",
              s"An error occurred while parsing the RDF data of the submitted DataId:\n$th")
          }).get
      }
    }
  }


  def response: ActionResult = {

    val validations = this.validations //memoize to ensure single pass of validations

    validations.list.sequenceU.fold({ case te: ActionResult => te }, { validationResults =>

      validationResults.find(_.isBlocker) match {

        case Some(blockingResult) => {

          val optionalDetails = blockingResult.details.fold("")("\n" + _)

          BadRequest(
            s"${blockingResult.shortMessage}$optionalDetails")
        }

        case None => {

          saveDataId(validations).map({ _ =>

            def shortMessages = validationResults.map(_.shortMessage)

            Ok(shortMessages.mkString("DataId successfully submitted.\n\n", "\n", "\n"))
          }).fold({ case te: ActionResult => te }, identity)
        }
      }
    })
  }

  protected def saveDataId(validations: Validations): TechnicalError \/ Unit = {

    def tdbSave(validations: Validations): TechnicalError \/ Model = repoTDB.writeTransaction({ implicit dataset =>

      logger.debug("in write transaction for save")


      (dataIdIdentifier |@| dataIdVersion |@| dataIdWebLocation |@| validations.wellformed) apply {

        case (identifier, version, location, PassWithResult(_, model: Model, _)) => {

          logger.debug(s"saving uploaded DataId:\n${snap(identifier, version, location)}")

          DataIdMetadata.getOrCreate(identifier, location, version)

          dataset.getNamedModel(models.dataIdIdentifierToIRI(identifier))
            .removeAll()
            .add(model)
        }

        case unavailable => sys.error("illegal state: validation results not available:\n" + unavailable)
      }
    })

    def storeToFilesystem(validations: Validations): TechnicalError \/ Unit = {

      import config.persistence._

      val othersRWPerms = Set(OTHERS_READ, OTHERS_WRITE)

      dataIdWebLocation map { dataIdWebURL =>

        fileSystemStorageLocation.createIfNotExists(asDirectory = true)

        (othersRWPerms + OTHERS_EXECUTE) foreach (fileSystemStorageLocation.addPermission)

        val lockFile = fileSystemStorageLocation / "dataid-repo.lock"

        val filename = urlEncode(dataIdWebURL.stripSuffix(".ttl"))

        val ttlFile = (fileSystemStorageLocation / s"$filename.ttl").touch()

        val graphFile = (fileSystemStorageLocation / s"$filename.graph").touch()


        othersRWPerms foreach { perm =>

          ttlFile.addPermission(perm)
          graphFile.addPermission(perm)
        }

        val writing = for {
          lock <- lockFile.asLockFile
          deletesOnError <- ttlFile.deleteOnError and graphFile.deleteOnError
          dataidInputStream <- dataId
          dataidOutputStream <- managed(ttlFile.newOutputStream)
        } yield {

          // streaming copy dataid from request
          dataidInputStream pipeTo dataidOutputStream

          graphFile write (dataIdWebURL + "\n")
        }

        writing.apply(identity)
      }
    }

    def persist() = config.persistence.strategy match {

      case PersistenceStrategy.TDB => tdbSave(validations)

      case PersistenceStrategy.Filesystem => storeToFilesystem(validations)
    }

    \/.fromTryCatchNonFatal(persist()).leftMap { th: Throwable =>

      Technical(InternalServerError(s"Error while saving the DataId:\n$th\n" +
        s"${ExceptionUtils.getStackTrace(th)}"))
    } match {

      case -\/(te) => te.left

      case \/-(-\/(te)) => te.left

      case \/-(\/-(_)) => ().right
    }
  }


  def retrieveSingleUploadParam(paramName: String): TechnicalError \/ String = {

    uploadParams.get(paramName) match {

      case None => Technical(BadRequest(s"Missing upload parameter '$paramName'")).left

      case Some(value :: Nil) => value.right

      case Some(values) if values.size > 1 =>
        Technical(BadRequest(s"Multiple values for upload parameter '$paramName'")).left
    }
  }

  def wrapExceptionIntoInternalServerError[T](processDesc: String)(work: => T): TechnicalError \/ T = {
    \/.fromTryCatchNonFatal(work).leftMap { th =>
      Technical(InternalServerError(s"Error while $processDesc:\n$th"))
    }
  }

  case class Validations(signature: TechnicalError \/ ValidationResult,
    locationMatches: TechnicalError \/ ValidationResult, wellformed: TechnicalError \/ ValidationResult) {

    def list = List(signature, wellformed, locationMatches)
  }

}
