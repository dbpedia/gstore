package org.dbpedia.databus

import java.io.FileNotFoundException
import java.nio.file.{NoSuchFileException, Path, Paths}

import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.Lang
import org.apache.jena.shared.JenaException
import org.dbpedia.databus.ApiImpl.Config
import org.dbpedia.databus.RdfConversions.{generateGraphId, getPrefix, mapContentType, modelToBytes, readModel}
import org.dbpedia.databus.swagger.api.DatabusApi
import org.dbpedia.databus.swagger.model.{OperationFailure, OperationSuccess}
import sttp.model.Uri
import virtuoso.jdbc4.VirtuosoException

import scala.util.{Success, Try}
import scala.xml.Node
import collection.JavaConverters._


class ApiImpl(config: Config) extends DatabusApi {

  import ApiImpl._
  import config._

  private val client: GitClient = initGitClient(config)
  private lazy val writeVirtUri = Uri.unsafeParse(s"$virtuosoUri/sparql-auth")
  private val defaultLang = Lang.JSONLD
  private lazy val sparqlClient: SparqlClient =
    if (virtuosoOverHttp)
      new HttpVirtClient(writeVirtUri, virtuosoUser, virtuosoPass)
    else
      new JdbcCLient(writeVirtUri.host, virtuosoJdbcPort, virtuosoUser, virtuosoPass)

  override def dataidSubgraph(body: String)(request: HttpServletRequest): Try[String] =
    readModel(body.getBytes, defaultLang)
      .flatMap(m => Tractate.extract(m.getGraph, TractateV1.Version))
      .map(_.stringForSigning)

  override def deleteFile(username: String, path: String)(request: HttpServletRequest): Try[OperationSuccess] =
    deleteFileFromGit(username, path)(request)
      .map(hash => OperationSuccess(None, hash))

  override def getFile(username: String, path: String)(request: HttpServletRequest): Try[String] =
    readFile(username, path)(request)

  override def saveFile(username: String,
                        path: String,
                        body: String,
                        prefix: Option[String])
                       (request: HttpServletRequest): Try[OperationSuccess] = {
    val pa = gitPath(path)
    val graphId = generateGraphId(prefix.getOrElse(getPrefix(request)), username, pa)
    val ct = Option(request.getContentType)
      .map(_.toLowerCase)
      .getOrElse("")
    val lang = mapContentType(ct, defaultLang)
    readModel(body.getBytes, lang)
      .flatMap(model => {
        saveToVirtuoso(model, graphId)({
          modelToBytes(model, defaultLang)
            .flatMap(a => saveFiles(username, Map(
              pa -> a
            )).map(hash => OperationSuccess(Some(graphId), hash)))
        })
      })
  }

  override def shaclValidate(dataid: String, shacl: String)(request: HttpServletRequest): Try[Unit] =
    RdfConversions.validateWithShacl(
      dataid.getBytes,
      shacl.getBytes(),
      defaultLang
    ).map(_ => ())

  override def deleteFileMapException404(e: Throwable)(request: HttpServletRequest): Option[OperationFailure] = e match {
    case _: FileNotFoundException => Some(OperationFailure(e.getMessage))
    case _ => None
  }

  override def getFileMapException404(e: Throwable)(request: HttpServletRequest): Option[OperationFailure] = e match {
    case _: FileNotFoundException => Some(OperationFailure(e.getMessage))
    case _: NoSuchFileException => Some(OperationFailure(e.getMessage))
    case _ => None
  }

  override def saveFileMapException400(e: Throwable)(request: HttpServletRequest): Option[OperationFailure] = e match {
    case _: JenaException => Some(OperationFailure(e.getMessage))
    case _: VirtuosoException if e.getMessage.contains("SQ200") => Some(OperationFailure(s"Wrong value for type. ${e.getMessage}"))
    case _ => None
  }

  override def shaclValidateMapException400(e: Throwable)(request: HttpServletRequest): Option[String] = e match {
    case _ => Some(e.getMessage)
  }

  private def readFile(username: String, path: String)(request: HttpServletRequest): Try[String] = {
    val p = gitPath(path)
    val contentType = Option(request.getHeader("Accept"))
      .map(RdfConversions.mapContentType(_, defaultLang))
      .getOrElse(defaultLang)
    setResponseHeaders(Map("Content-Type" -> contentType.getContentType.toHeaderString))(request)
    client.readFile(username, p)
      .flatMap(
        RdfConversions.processFile(
          _,
          defaultLang,
          contentType))
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

  private[databus] def saveToVirtuoso[T](data: Array[Byte], lang: Lang, graphId: String)(execInTransaction: => Try[T]): Try[T] =
    readModel(data, lang)
      .flatMap(saveToVirtuoso(_, graphId)(execInTransaction))

  private[databus] def saveToVirtuoso[T](model: Model, graphId: String)(execInTransaction: => Try[T]): Try[T] = {
    val rqsts = model.getGraph.find().asScala
      .grouped(1000)
      .map(tpls => RdfConversions.makeInsertSparqlQuery(tpls, graphId))
      .toSeq
    // NOTE! here the order of concatenation is important!
    val fRs = Seq(RdfConversions.dropGraphSparqlQuery(graphId)) ++ rqsts

    sparqlClient.executeUpdates(
      RdfConversions.clearGraphSparqlQuery(graphId),
      fRs: _*
    )(execInTransaction)
  }

  private def saveFileToGit(username: String, path: String, data: Array[Byte]): Try[String] =
    saveFiles(username, Map(path -> data))

  private def saveFiles(username: String, fullFilenamesAndData: Map[String, Array[Byte]]): Try[String] =
    (if (!client.projectExists(username)) {
      client.createProject(username)
    } else {
      Success(Unit)
    }).flatMap(_ => client.commitSeveralFiles(username, fullFilenamesAndData))


  private def deleteFileFromGit(username: String, path: String)(request: HttpServletRequest): Try[String] = {
    val p = gitPath(path)
    deleteFiles(username, Seq(p))(request)
  }

  private def deleteFiles(username: String, paths: Seq[String])(request: HttpServletRequest): Try[String] =
    client.deleteSeveralFiles(username, paths)

  private def initGitClient(config: Config): GitClient = {
    import config._
    gitLocalDir.map(new LocalGitClient(_))
      .getOrElse({
        val scheme = gitApiSchema.getOrElse("https")
        val cl = for {
          user <- gitApiUser
          pass <- gitApiPass
          host <- gitApiHost
        } yield new RemoteGitlabHttpClient(user, pass, scheme, host, gitApiPort)
        cl.getOrElse(throw new RuntimeException("Wrong remote git client configuration"))
      })
  }

}


object ApiImpl {

  case class Config(virtuosoUri: Uri,
                    virtuosoUser: String,
                    virtuosoPass: String,
                    virtuosoJdbcPort: Int,
                    virtuosoOverHttp: Boolean,
                    // that is for using local jgit git provider
                    gitLocalDir: Option[Path],
                    // props below are for using gitlab as a git provider
                    gitApiUser: Option[String],
                    gitApiPass: Option[String],
                    // TODO isn't URI enough here
                    gitApiSchema: Option[String],
                    gitApiHost: Option[String],
                    gitApiPort: Option[Int])

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
      val gitApiSchema = getParam("gitApiSchema").orElse(Some("http"))
      val gitApiHost = getParam("gitApiHost").orElse(Some("localhost"))
      val gitApiPort = getParam("gitApiPort").map(_.toInt)

      ApiImpl.Config(
        Uri.parse(vUri).right.get,
        virtUser,
        virtPass,
        virtuosoJdbcPort,
        virtuosoOverHttp,
        gitLocalDir,
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
        .orElse(Option(System.getenv(name)))
        .map(_.trim)
        .filter(_.nonEmpty)
        .orElse(Option(mapper.getKeyValue(name)))
  }

}


