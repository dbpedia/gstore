package org.dbpedia.databus

import java.io.ByteArrayInputStream
import java.nio.file.{Path, Paths}

import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.dbpedia.databus.ApiImpl.Config
import org.dbpedia.databus.RdfConversions.{generateGraphId, mapContentType, mapFilenameToContentType, readModel}
import org.dbpedia.databus.swagger.api.DatabusApi
import org.dbpedia.databus.swagger.model.ApiResponse
import sttp.model.Uri

import scala.util.Try
import scala.xml.Node
import collection.JavaConverters._


class ApiImpl(config: Config) extends DatabusApi {

  import ApiImpl._
  import config._

  private val client: GitClient = initGitClient(config)
  private lazy val writeVirtUri = Uri.unsafeParse(s"$virtuosoUri/sparql-auth")
  private lazy val sparqlClient: SparqlClient =
    if (virtuosoOverHttp)
      new HttpVirtClient(writeVirtUri, virtuosoUser, virtuosoPass)
    else
      new JdbcCLient(writeVirtUri.host, virtuosoJdbcPort, virtuosoUser, virtuosoPass)

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
    val pa = gitPath(path)
    saveToVirtuoso(data, path, username)(saveFiles(username, Map(
      pa -> data
    )))
  }

  override def shaclValidate(dataid: String, shacl: String)(request: HttpServletRequest): Try[ApiResponse] =
    RdfConversions.validateWithShacl(
      dataid.getBytes,
      shacl.getBytes()
    ).map(_ => ApiResponse(Some(200), None, None))

  private def readFile(username: String, path: String)(request: HttpServletRequest): Try[String] = {
    val p = gitPath(path)
    client.readFile(username, p)
      .map(
        RdfConversions.processFile(
          p,
          _,
          Option(request.getHeader("Accept")).map(RdfConversions.mapContentType)))
      .map(new String(_))
  }

  private def gitPath(path: String): String = {
    val pa = Paths.get(path)
    if (pa.isAbsolute) {
      Paths.get("/").relativize(pa).toString
    } else {
      path
    }
  }

  private[databus] def saveToVirtuoso[T](data: Array[Byte], path: String, repo: String)(execInTransaction: => Try[T]): Try[T] = {
    val model = ModelFactory.createDefaultModel()
    val dataStream = new ByteArrayInputStream(data)
    val lang = mapContentType(mapFilenameToContentType(path))
    val graphId = generateGraphId(repo, path)
    RDFDataMgr.read(model, dataStream, lang)

    val rqsts = model.getGraph.find().asScala
      .grouped(1000)
      .map(tpls => RdfConversions.makeInsertSparqlQuery(tpls, graphId))
      .toSeq
    val fRs = Seq(RdfConversions.dropGraphSparqlQuery(graphId)) ++ rqsts

    sparqlClient.executeUpdates(
      RdfConversions.clearGraphSparqlQuery(graphId),
      fRs: _*
    )(execInTransaction)
  }

  private def saveFileToGit(username: String, path: String, data: Array[Byte]): Try[ApiResponse] =
    saveFiles(username, Map(path -> data))

  private def saveFiles(username: String, fullFilenamesAndData: Map[String, Array[Byte]]): Try[ApiResponse] = {
    if (!client.projectExists(username)) {
      client.createProject(username)
    }
    client.commitSeveralFiles(username, fullFilenamesAndData)
      .map(s => ApiResponse(Some(200), None, Some(s)))
  }

  private def deleteFileFromGit(username: String, path: String)(request: HttpServletRequest): Try[ApiResponse] = {
    val p = gitPath(path)
    deleteFiles(username, Seq(p))(request)
  }

  private def deleteFiles(username: String, paths: Seq[String])(request: HttpServletRequest): Try[ApiResponse] =
    client.deleteSeveralFiles(username, paths)
      .map(s => ApiResponse(Some(200), None, Some(s)))

  private def initGitClient(config: Config): GitClient = {
    import config._
    gitLocalDir.map(new LocalGitClient(_))
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
                     baseDir: Option[Path],
                     gitLocalDir: Option[Path],

                     virtuosoUri: Uri,
                     virtuosoUser: String,
                     virtuosoPass: String,
                     virtuosoJdbcPort: Int,
                     virtuosoOverHttp: Boolean,


                     gitApiUser: Option[String],
                     gitApiPass: Option[String],
                     // TODO isn't URI enough here
                     gitApiScheme: Option[String],
                     gitApiHostname: Option[String],
                     gitApiPort: Option[Int]


                   )

  object Config {

    def default: Config = fromMapper(SystemMapper)

    def fromWebXml(xml: Node): Config = fromMapper(xml)

    def fromServletContext(ctx: ServletContext): Config = fromMapper(ctx)

    private def fromMapper(mapper: Mapper): Config = {
      implicit val mp = mapper

      val virtUri = getParam("virtuosoUri").get
      val vUri = if (virtUri.endsWith("/")) virtUri.dropRight(1) else virtUri
      val virtUser = getParam("virtuosoUser").get
      val virtPass = getParam("virtuosoPass").get
      val virtuosoJdbcPort = getParam("virtuosoJdbcPort").map(_.toInt).get
      val virtuosoOverHttp = getParam("virtuosoOverHttp").map(_.toBoolean).get

      val gitLocalDir: Option[Path] = getParam("gitLocalDir").map(Paths.get(_))

      val gitApiUser = getParam("gitApiUser")
      val gitApiPass = getParam("gitApiPass")

      // TODO isn't URI enough here
      val gitApiSchema = getParam("gitSchema").orElse(Some("http"))
      val gitApiHost = getParam("gitHost").orElse(Some("localhost"))
      val gitApiPort = getParam("gitPort").map(_.toInt)

      ApiImpl.Config(
        gitLocalDir,
        Uri.parse(vUri).right.get,
        virtUser,
        virtPass,
        virtuosoJdbcPort,
        virtuosoOverHttp
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

}


