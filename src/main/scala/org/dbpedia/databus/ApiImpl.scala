package org.dbpedia.databus

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.file.{Path, Paths}
import java.util.function.Consumer

import javax.servlet.ServletContext
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
import org.eclipse.jetty.xml.XmlConfiguration
import sttp.client3._
import sttp.model.Uri

import scala.util.{Failure, Success, Try}
import scala.xml.{Document, Node}


class ApiImpl(config: Config) extends DatabusApi {

  import ApiImpl._
  import config._

  private lazy val backend = new DigestAuthenticationBackend(HttpURLConnectionBackend())
  private val client: GitClient = initGitClient(config)

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

  private def initGitClient(config: Config): GitClient = {
    import config._
    localGitPath.map(new LocalGitClient(_))
      .getOrElse({
        val scheme = gitApiScheme.getOrElse("https")
        val cl = for {
          user <- gitApiUser
          pass <- gitApiPass
          host <- gitApiHostname
        } yield new RemoteGitlabHttpClient(user, pass, scheme, host, gitApiPort)
        cl.getOrElse(throw new RuntimeException("Wrong remote git client configuration"))
      })
  }

}


object ApiImpl {

  case class Config(
                     localGitPath: Option[Path],

                     virtuosoUri: Uri,
                     virtuosoUser: String,
                     virtuosoPass: String,

                     gitApiUser: Option[String],
                     gitApiPass: Option[String],
                     // TODO isn't URI enough here
                     gitApiScheme: Option[String],
                     gitApiHostname: Option[String],
                     gitApiPort: Option[Int]


                   ){
    override def toString: String =
      s"""
         |virtuosoUri: $virtuosoUri
         |virtuosoUser: $virtuosoUser
         |virtuosoPass: hidden, length ${virtuosoPass.length}
         |localGitPath: ${localGitPath.get}
         |""".stripMargin
  }


  object Config {

    def default: Config = fromMapper(SystemMapper)
    def fromWebXml(xml: Node): Config = fromMapper(xml)
    def fromServletContext(ctx: ServletContext): Config = fromMapper(ctx)



    private def fromMapper(mapper: Mapper): Config = {
      implicit val mp = mapper
      val port = getParam("port").map(_.toInt)


      val virtUri = getParam("virtuosoUri").get
      val virtUser = getParam("virtuosoUser").get
      val virtPass = getParam("virtuosoPass").get

      // folder
      val localGitPath: Option[Path] = getParam("localGitPath").map(Paths.get(_))

      val gitApiUser = getParam("gitApiUser")
      val gitApiPass = getParam("gitApiPass")

      // TODO isn't URI enough here
      val gitApiSchema = getParam("gitSchema").orElse(Some("http"))
      val gitApiHost = getParam("gitHost").orElse(Some("localhost"))
      val gitApiPort = getParam("gitPort").map(_.toInt)

      ApiImpl.Config(

        localGitPath,
        Uri.parse(virtUri).right.get,
        virtUser,
        virtPass,
        gitApiUser,
        gitApiPass,
        // TODO isn't URI enough here
        gitApiSchema,
        gitApiHost,
        gitApiPort
      )
    }

    implicit class ServletContextToMapper(ctx: ServletContext) extends Mapper {
      override def getKeyValue(name: String): String = ctx.getInitParameter(name)
    }

    implicit class XmlToMapper(xml: Node) extends Mapper {
      override def getKeyValue(name: String): String = {
        val p = (xml \\ "context-param")
          .filter(p => (p \ "param-name").text == name)
        p.map(_ \ "param-value").headOption.map(_.head.text).orNull
      }
    }

    object SystemMapper extends Mapper {
      override def getKeyValue(name: String): String = System.getProperty(name)
    }

    trait Mapper {
      def getKeyValue(name: String): String
    }

    private def getParam(name: String)(implicit mapper: Mapper): Option[String] =
      Option(System.getProperty(name))
        .map(_.trim)
        .filter(_.nonEmpty)
        .orElse(Option(mapper.getKeyValue(name)))

  }

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

    val dropNinsertRequest = RdfConversions.dropGraphSparqlQuery(graphId) + ";\n" + RdfConversions.makeInsertSparqlQuery(model.getGraph, graphId)
    val dropAndInsert = virtuosoRequest(
      dropNinsertRequest,
      viruosoUri,
      virtuosoUsername,
      virtuosoPass
    )

    backend.send(dropAndInsert)
      .isSuccess
  }

}


object RdfConversions {

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

  def generateGraphId(user: String, path: String): String =
    s"/$user/$path"

  def dropGraphSparqlQuery(graphId: String) =
    s"DROP SILENT GRAPH <$graphId>"

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
          bld.append(escapeChars(t.getObject.getLiteral.getLexicalForm))
          bld.append("\"")
          bld.append("^^")
          wrapWithAngleBracketsQuote(bld, t.getObject.getLiteralDatatypeURI)
        } else {
          wrapWithAngleBracketsQuote(bld, escapeChars(t.getObject.toString()))
        }
        bld.append(" ")
        bld.append(".")
        bld.append("\n")
      }
    })
    bld
  }

  def escapeChars(s: String) = s
    .replaceAllLiterally("\"", "\\\"")
    .replaceAllLiterally("\n", "\\n")

  def wrapWithAngleBracketsQuote(bld: StringBuilder, s: String) = {
    bld.append("<")
    bld.append(s)
    bld.append(">")
  }

}


