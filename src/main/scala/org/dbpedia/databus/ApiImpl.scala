package org.dbpedia.databus

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import javax.servlet.http.HttpServletRequest
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.dbpedia.databus.ApiImpl.Config
import org.dbpedia.databus.swagger.api.DatabusApi
import org.dbpedia.databus.swagger.model.{ApiResponse, BinaryBody, DataIdSignatureMeta, DataidFileUpload}
import scalaj.http.Base64
import sttp.client3._
import sttp.model.Uri
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.util.{Failure, Try}


class ApiImpl(config: Config) extends DatabusApi {
  import config._

  private lazy val backend = HttpURLConnectionBackend()
  private val client = new RemoteGitlabHttpClient(accessToken, gitScheme, gitHostname, gitPort)

  override def dataidSubgraph(body: BinaryBody)(request: HttpServletRequest): Try[BinaryBody] = ???

  override def dataidSubgraphHash(body: BinaryBody)(request: HttpServletRequest): Try[DataIdSignatureMeta] = ???

  override def dataidUpload(body: DataidFileUpload, xClientCert: String)(request: HttpServletRequest): Try[ApiResponse] = Try {
    val data = RdfConversions.processFile(body.file.dataBase64)
    ApiResponse(Some(200), Some(new String(data)), Some("data!"))
  }

  override def createGroup(groupId: String, username: String, body: BinaryBody)(request: HttpServletRequest): Try[ApiResponse] = {
    if (!checkAuth(username, request)) {
      Failure(new RuntimeException("authorization failed"))
    } else {
      if (!client.projectExists(username)) {
        client.createProject(username)
      }
      client.commitFileContent(username, s"$groupId/group.jsonld", Base64.decode(body.dataBase64))
        .map(s => ApiResponse(Some(200), None, Some(s)))
    }
  }

  override def createVersion(versionId: String, body: BinaryBody)(request: HttpServletRequest): Try[ApiResponse] = ???

  override def deleteGroup(groupId: String, username: String)(request: HttpServletRequest): Try[ApiResponse] = ???

  override def deleteVersion(versionId: String)(request: HttpServletRequest): Try[ApiResponse] = ???

  override def getGroup(groupId: String, username: String)(request: HttpServletRequest): Try[Unit] = ???

  override def getVersion(versionId: String)(request: HttpServletRequest): Try[Unit] = ???


  private def checkAuth(username: String, request: HttpServletRequest): Boolean = {
    val header = request.getHeader("Authorization")
    val token = header.split("\\s")(1)
    val req = basicRequest.post(Uri.unsafeParse(tokenCheckUri)).header("Authorization", s"Bearer $token")
    val resp = req.send(backend)
    resp.body match {
      case Left(_) => false
      case Right(value) =>
        val n = for {
          JObject(chld) <- parse(value)
          JField("preferred_username", JString(un)) <- chld
          if un == username
        } yield un
        n.nonEmpty
    }
  }


}


object ApiImpl {

  case class Config(
                     accessToken: String,
                     gitScheme: String,
                     gitHostname: String,
                     gitPort: Option[Int],
                     tokenCheckUri: String
                   )

}


object RdfConversions {

  def processFile(fileBase64: String): Array[Byte] = {
    val model = ModelFactory.createDefaultModel()
    val dataStream = new ByteArrayInputStream(Base64.decode(fileBase64))
    Try(RDFDataMgr.read(model, dataStream, Lang.JSONLD))
      .getOrElse(RDFDataMgr.read(model, dataStream, Lang.TURTLE))

    val str = new ByteArrayOutputStream()
    RDFDataMgr.write(str, model, Lang.TURTLE)
    str.toByteArray
  }


}


