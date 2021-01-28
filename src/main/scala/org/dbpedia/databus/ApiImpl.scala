package org.dbpedia.databus

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.function.Consumer

import javax.servlet.http.HttpServletRequest
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.jena.shacl.validation.ReportEntry
import org.apache.jena.shacl.{ShaclValidator, Shapes}
import org.dbpedia.databus.ApiImpl.Config
import org.dbpedia.databus.swagger.api.DatabusApi
import org.dbpedia.databus.swagger.model.{ApiResponse, BinaryBody, DataIdSignatureMeta, DataidFileUpload}
import scalaj.http.Base64
import sttp.client3._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import sttp.model.Uri

import scala.util.{Failure, Success, Try}


class ApiImpl(config: Config) extends DatabusApi {

  import ApiImpl._
  import config._

  private lazy val backend = HttpURLConnectionBackend()
  private val client = new RemoteGitlabHttpClient(accessToken, gitScheme, gitHostname, gitPort)

  override def dataidSubgraph(body: BinaryBody)(request: HttpServletRequest): Try[BinaryBody] = ???

  override def dataidSubgraphHash(body: BinaryBody)(request: HttpServletRequest): Try[DataIdSignatureMeta] = ???

  override def dataidUpload(body: DataidFileUpload, xClientCert: String)(request: HttpServletRequest): Try[ApiResponse] = Try {
    ApiResponse(Some(200), Some(new String("test, noop method")), Some("data!"))
  }

  override def createGroup(groupId: String, username: String, body: BinaryBody)(request: HttpServletRequest): Try[ApiResponse] = {
    val data = Base64.decode(body.dataBase64)
    RdfConversions.validateWithShacl(data, shaclUri)
      .flatMap(_ => saveFile(username, s"$groupId/$DefaultGroupFn", data)(request))
  }

  override def deleteGroup(groupId: String, username: String)(request: HttpServletRequest): Try[ApiResponse] =
    deleteFile(username, s"$groupId/$DefaultGroupFn")(request)

  override def getGroup(groupId: String, username: String)(request: HttpServletRequest): Try[String] =
    readFile(username, s"$groupId/$DefaultGroupFn")(request)

  override def createVersion(versionId: String,
                             groupId: String,
                             username: String,
                             artifactId: String,
                             body: BinaryBody)
                            (request: HttpServletRequest): Try[ApiResponse] = {
    val data = Base64.decode(body.dataBase64)
    RdfConversions.validateWithShacl(data, shaclUri)
      .flatMap(_ => saveFile(username, s"$groupId/$artifactId/$versionId/$DefaultVersionFn", data)(request))
  }

  override def deleteVersion(versionId: String,
                             groupId: String,
                             username: String,
                             artifactId: String)
                            (request: HttpServletRequest): Try[ApiResponse] =
    deleteFile(username, s"$groupId/$artifactId/$versionId/$DefaultVersionFn")(request)

  override def getVersion(versionId: String,
                          groupId: String,
                          username: String,
                          artifactId: String)
                         (request: HttpServletRequest): Try[String] =
    readFile(username, s"$groupId/$artifactId/$versionId/$DefaultVersionFn")(request)

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

  private def readFile(username: String, path: String)(request: HttpServletRequest): Try[String] =
    if (!checkAuth(username, request)) {
      Failure(new RuntimeException("authorization failed"))
    } else {
      client.readFile(username, path)
        .map(
          RdfConversions.processFile(
            path,
            _,
            Option(request.getHeader("Accept")).map(RdfConversions.mapContentType)))
        .map(new String(_))
    }


  private def saveFile(username: String, path: String, data: Array[Byte])(request: HttpServletRequest): Try[ApiResponse] =
    if (!checkAuth(username, request)) {
      Failure(new RuntimeException("authorization failed"))
    } else {
      if (!client.projectExists(username)) {
        client.createProject(username)
      }
      client.commitFileContent(username, path, data)
        .map(s => ApiResponse(Some(200), None, Some(s)))
    }

  private def deleteFile(username: String, path: String)(request: HttpServletRequest): Try[ApiResponse] =
    if (!checkAuth(username, request)) {
      Failure(new RuntimeException("authorization failed"))
    } else {
      client.commitFileDelete(username, path)
        .map(s => ApiResponse(Some(200), None, Some(s)))
    }


}


object ApiImpl {

  val DefaultGroupFn = "group.jsonld"
  val DefaultVersionFn = "dataid.jsonld"

  case class Config(
                     accessToken: String,
                     gitScheme: String,
                     gitHostname: String,
                     gitPort: Option[Int],
                     tokenCheckUri: String,
                     shaclUri: String
                   )

}


object RdfConversions {

  def validateWithShacl(file: Array[Byte], shaclUri: String): Try[Unit] = {
    val graph = RDFDataMgr.loadGraph(shaclUri)
    val model = ModelFactory.createDefaultModel()
    val dataStream = new ByteArrayInputStream(file)
    RDFDataMgr.read(model, dataStream, Lang.JSONLD)
    val shape = Shapes.parse(graph)
    val report = ShaclValidator.get().validate(shape, model.getGraph)
    if(report.conforms()){
      Success(Unit)
    }else{
      val msg = new StringBuilder
      report.getEntries.forEach(new Consumer[ReportEntry] {
        override def accept(t: ReportEntry): Unit =
          msg.append(t.message())
      })
      Failure(new RuntimeException(msg.toString()))
    }
  }

  def processFile(path: String, fileData: Array[Byte], outpuLang: Option[Lang] = None): Array[Byte] = {
    val model = ModelFactory.createDefaultModel()
    val dataStream = new ByteArrayInputStream(fileData)
    val lang = mapContentType(mapFilenameToContentType(path))
    RDFDataMgr.read(model, dataStream, lang)
    val str = new ByteArrayOutputStream()
    RDFDataMgr.write(str, model, outpuLang.getOrElse(Lang.TURTLE))
    str.toByteArray
  }

  def mapFilenameToContentType(fn: String): String =
    fn.split('.').last match {
      case "ttl" => "text/turtle"
      case "rdf" => "application/rdf+xml"
      case "nt" => "application/n-triples"
      case "jsonld" => "application/ld+json"
      case "trig" => "text/trig"
      case "nq" => "application/n-quads"
      case "trix" => "application/trix+xml"
      case "trdf" => "application/rdf+thrift"
      case _ => "text/turtle"
    }

  def mapContentType(cn: String): Lang =
    cn match {
      case "text/turtle" => Lang.TURTLE
      case "application/rdf+xml" => Lang.RDFXML
      case "application/n-triples" => Lang.NTRIPLES
      case "application/ld+json" => Lang.JSONLD
      case "text/trig" => Lang.TRIG
      case "application/n-quads" => Lang.NQUADS
      case "application/trix+xml" => Lang.TRIX
      case "application/rdf+thrift" => Lang.RDFTHRIFT
      case _ => Lang.TURTLE
    }

}


