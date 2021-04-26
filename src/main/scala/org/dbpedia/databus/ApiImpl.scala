package org.dbpedia.databus

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.file.Paths
import java.util.function.Consumer

import javax.servlet.http.HttpServletRequest
import org.apache.jena.graph.Graph
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.jena.shacl.validation.ReportEntry
import org.apache.jena.shacl.{ShaclValidator, Shapes}
import org.apache.jena.sparql.graph.GraphFactory
import org.dbpedia.databus.ApiImpl.Config
import org.dbpedia.databus.RdfConversions.{generateGraphId, mapContentType, mapFilenameToContentType, readModel}
import org.dbpedia.databus.swagger.api.DatabusApi
import org.dbpedia.databus.swagger.model.ApiResponse
import sttp.client3._
import sttp.model.Uri

import scala.util.{Failure, Success, Try}


class ApiImpl(config: Config) extends DatabusApi {

  import ApiImpl._
  import config._

  private lazy val backend = new DigestAuthenticationBackend(HttpURLConnectionBackend())
  private val client = new RemoteGitlabHttpClient(accessToken, gitScheme, gitHostname, gitPort)

  override def dataidSubgraph(body: String)(request: HttpServletRequest): Try[String] =
    readModel(body.getBytes)
      .flatMap(m => Tractate.extract(m.getGraph, TractateV1.Version))
      .map(_.stringForSigning)

  override def deleteFile(username: String, path: String)(request: HttpServletRequest): Try[ApiResponse] =
    deleteFileFromGit(username, path)(request)

  override def getFile(username: String, path: String)(request: HttpServletRequest): Try[String] =
    readFile(username, path)(request)

  override def saveFile(username: String,
                        path: String,
                        body: String)
                       (request: HttpServletRequest): Try[ApiResponse] = {
    val data = body.getBytes
    val pa = gitlabPath(path)
    saveFiles(username, Map(
      pa -> data
    ))(request)
      .flatMap(a => {
        if (saveToVirtuoso(
          backend,
          data,
          path,
          generateGraphId(username, pa),
          virtuosoUri,
          virtuosoUser,
          virtuosoPass)) {
          Success(a)
        } else {
          Failure(new RuntimeException("Saving to virtuoso did not work."))
        }
      })
  }

  override def shaclValidate(dataid: String, shacl: String)(request: HttpServletRequest): Try[ApiResponse] =
    RdfConversions.validateWithShacl(
      dataid.getBytes,
      shacl.getBytes()
    ).map(_ => ApiResponse(Some(200), None, None))

  private def readFile(username: String, path: String)(request: HttpServletRequest): Try[String] = {
    val p = gitlabPath(path)
    client.readFile(username, p)
      .map(
        RdfConversions.processFile(
          p,
          _,
          Option(request.getHeader("Accept")).map(RdfConversions.mapContentType)))
      .map(new String(_))
  }

  private def gitlabPath(path: String): String = {
    val pa = Paths.get(path)
    if (pa.isAbsolute) {
      Paths.get("/").relativize(pa).toString
    } else {
      path
    }
  }

  private def saveFileToGit(username: String, path: String, data: Array[Byte])(request: HttpServletRequest): Try[ApiResponse] =
    saveFiles(username, Map(path -> data))(request)

  private def saveFiles(username: String, fullFilenamesAndData: Map[String, Array[Byte]])(request: HttpServletRequest): Try[ApiResponse] = {
    if (!client.projectExists(username)) {
      client.createProject(username)
    }
    client.commitSeveralFiles(username, fullFilenamesAndData)
      .map(s => ApiResponse(Some(200), None, Some(s)))
  }

  private def deleteFileFromGit(username: String, path: String)(request: HttpServletRequest): Try[ApiResponse] = {
    val p = gitlabPath(path)
    deleteFiles(username, Seq(p))(request)
  }

  private def deleteFiles(username: String, paths: Seq[String])(request: HttpServletRequest): Try[ApiResponse] =
    client.deleteSeveralFiles(username, paths)
      .map(s => ApiResponse(Some(200), None, Some(s)))

}


object ApiImpl {

  val DefaultGroupFn = "group.jsonld"
  val DefaultVersionFn = "dataid.jsonld"
  val DatabusSignatureFilename = "databus_signature"
  val DatabusTractateFilename = "databus_tractate"

  case class Config(
                     accessToken: String,
                     gitScheme: String,
                     gitHostname: String,
                     gitPort: Option[Int],
                     virtuosoUri: Uri,
                     virtuosoUser: String,
                     virtuosoPass: String
                   )

  private[databus] def virtuosoRequest(request: String, virtuosoUri: Uri, un: String, pass: String) =
    basicRequest
      .post(virtuosoUri)
      .body("query" -> request)
      .auth.digest(un, pass)

  private[databus] def saveToVirtuoso(backend: SttpBackend[Identity, Any],
                                      fileData: Array[Byte],
                                      path: String,
                                      graphId: String,
                                      viruosoUri: Uri,
                                      virtuosoUsername: String,
                                      virtuosoPass: String): Boolean = {
    val model = ModelFactory.createDefaultModel()
    val dataStream = new ByteArrayInputStream(fileData)
    val lang = mapContentType(mapFilenameToContentType(path))
    RDFDataMgr.read(model, dataStream, lang)
    val drop = virtuosoRequest(
      RdfConversions.dropGraphSparqlQuery(graphId),
      viruosoUri,
      virtuosoUsername,
      virtuosoPass
    )

    val insert = virtuosoRequest(
      RdfConversions.makeInsertSparqlQuery(model.getGraph, graphId),
      viruosoUri,
      virtuosoUsername,
      virtuosoPass
    )

    val re = backend.send(drop)
    val succ = re.body match {
      case Left(b) => b.contains("has not been explicitly created before")
      case Right(_) => true
    }

    if (succ) {
      backend.send(insert).isSuccess
    } else {
      false
    }
  }

}


object RdfConversions {

  import collection.JavaConverters._

  def validateVersion(model: Model,
                      username: String,
                      groupId: String,
                      artifactId: String,
                      versionId: String): Try[Unit] =
    model.getGraph.find().toList.asScala
      .find(_.getPredicate.getURI == "http://dataid.dbpedia.org/ns/core#version")
      .map(_.getMatchObject.getURI)
      .map(v => RdfConversions.validateVersion(v, username, groupId, artifactId, versionId)) match {
      case Some(v) => v
      case None => Failure(new RuntimeException("Invalid version"))
    }


  def validateVersion(versionToCheck: String,
                      username: String,
                      groupId: String,
                      artifactId: String,
                      versionId: String): Try[Unit] = {
    val left = Uri.parse(versionToCheck)
    val right = Uri.parse(s"https://databus.dbpedia.org/$username/$groupId/$artifactId/$versionId")
    left.flatMap(l =>
      right
        .map(l == _)
        .map(r => if (r) {
          Success({})
        } else {
          Failure(new RuntimeException("Invalid version"))
        })) match {
      case Left(s) => Failure(new RuntimeException("Invalid version: " + s))
      case Right(value) => value
    }
  }

  def readModel(file: Array[Byte]) = Try {
    val model = ModelFactory.createDefaultModel()
    val dataStream = new ByteArrayInputStream(file)
    RDFDataMgr.read(model, dataStream, Lang.JSONLD)
    model
  }

  def validateWithShacl(file: Array[Byte], shaclData: Array[Byte]): Try[Model] = {
    val shaclGra = GraphFactory.createDefaultGraph()
    val shaDataStream = new ByteArrayInputStream(shaclData)
    RDFDataMgr.read(shaclGra, shaDataStream, Lang.TTL)
    readModel(file)
      .flatMap(model => Try {
        val shape = Shapes.parse(shaclGra)
        val report = ShaclValidator.get().validate(shape, model.getGraph)
        if (report.conforms()) {
          model
        } else {
          val msg = new StringBuilder
          report.getEntries.forEach(new Consumer[ReportEntry] {
            override def accept(t: ReportEntry): Unit =
              msg.append(t.message())
          })
          throw new RuntimeException(msg.toString())
        }
      })
  }

  def validateWithShacl(file: Array[Byte], shaclUri: String): Try[Model] = {
    val graph = RDFDataMgr.loadGraph(shaclUri)
    val model = ModelFactory.createDefaultModel()
    val dataStream = new ByteArrayInputStream(file)
    RDFDataMgr.read(model, dataStream, Lang.JSONLD)
    val shape = Shapes.parse(graph)
    val report = ShaclValidator.get().validate(shape, model.getGraph)
    if (report.conforms()) {
      Success(model)
    } else {
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

  import org.apache.jena.graph.Triple

  def generateVersionGraphId(user: String, group: String, artifact: String, version: String): String =
  // todo detect original uri from the request
    s"https://databus.dbpedia.org/$user/$group/$artifact/$version/dataid.ttl#Dataset"

  def generateGroupGraphId(user: String, group: String): String =
    s"https://databus.dbpedia.org/$user/$group/documentation.ttl"

  def generateGraphId(user: String, path: String): String =
    s"https://databus.dbpedia.org/$user/$path"

  def dropGraphSparqlQuery(graphId: String) =
    s"CLEAR GRAPH <$graphId>"

  def makeInsertSparqlQuery(graph: Graph, graphId: String): String = {
    val bld = StringBuilder.newBuilder
    bld.append("INSERT IN GRAPH ")
    wrapWithAngleBracketsQuote(bld, graphId)
    bld.append(" {\n")
    generateQueryTriples(bld, graph)
    bld.append("}").toString()
  }

  def generateQueryTriples(bld: StringBuilder, graph: Graph): StringBuilder = {
    graph.find().forEach(new Consumer[Triple] {
      override def accept(t: Triple): Unit = {
        wrapWithAngleBracketsQuote(bld, t.getSubject.toString())
        bld.append(" ")
        wrapWithAngleBracketsQuote(bld, t.getPredicate.toString())
        bld.append(" ")
        if (t.getObject.isLiteral) {
          bld.append("\"")
          bld.append(t.getObject.getLiteral.getLexicalForm)
          bld.append("\"")
          bld.append("^^")
          wrapWithAngleBracketsQuote(bld, t.getObject.getLiteralDatatypeURI)
        } else {
          wrapWithAngleBracketsQuote(bld, t.getObject.toString())
        }
        bld.append(" ")
        bld.append(".")
        bld.append("\n")
      }
    })
    bld
  }

  def wrapWithAngleBracketsQuote(bld: StringBuilder, s: String) = {
    bld.append("<")
    bld.append(s)
    bld.append(">")
  }

}


