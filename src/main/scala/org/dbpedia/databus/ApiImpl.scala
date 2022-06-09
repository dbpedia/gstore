package org.dbpedia.databus

import java.io.FileNotFoundException
import java.net.URL
import java.nio.file.{NoSuchFileException, Path, Paths}

import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.Lang
import org.apache.jena.shared.JenaException
import org.dbpedia.databus.ApiImpl.Config
import org.dbpedia.databus.RdfConversions.{contextUri, generateGraphId, graphToBytes, mapContentType, readModel}
import org.dbpedia.databus.swagger.api.DatabusApi
import org.dbpedia.databus.swagger.model.{OperationFailure, OperationSuccess}
import sttp.model.Uri
import virtuoso.jdbc4.VirtuosoException

import scala.util.{Failure, Success, Try}
import scala.xml.Node
import collection.JavaConverters._


class ApiImpl(config: Config) extends DatabusApi {

  import ApiImpl._

  private val client: GitClient = initGitClient(config)
  private val defaultLang = Lang.JSONLD
  private lazy val sparqlClient: SparqlClient = SparqlClient.get(config)


  override def dataidSubgraph(body: String)(request: HttpServletRequest): Try[String] =
    readModel(body.getBytes, defaultLang, contextUri(body.getBytes, defaultLang))
      .flatMap(m => Tractate.extract(m.getGraph, TractateV1.Version))
      .map(_.stringForSigning)

  override def deleteFile(username: String, path: String, prefix: Option[String])(request: HttpServletRequest): Try[OperationSuccess] = {
    val gid = generateGraphId(prefix.getOrElse(getPrefix(request)), username, path)
    sparqlClient.executeUpdates(
      RdfConversions.dropGraphSparqlQuery(gid)
    )(m => {
      if (m.map(_._2).sum > 0) {
        deleteFileFromGit(username, path)(request)
          .map(hash => OperationSuccess(gid, hash))
      } else {
        Failure(new GraphDoesNotExistException(gid))
      }
    })
  }

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
    val ctxUri = contextUri(body.getBytes, lang)
    readModel(body.getBytes, lang, ctxUri)
      .flatMap(model => {
        saveToVirtuoso(model, graphId)({
          graphToBytes(model.getGraph, defaultLang, ctxUri)
            .flatMap(a => saveFiles(username, Map(
              pa -> a
            )).map(hash => OperationSuccess(graphId, hash)))
        })
      })
  }

  override def shaclValidate(dataid: String, shacl: String)(request: HttpServletRequest): Try[String] = {
    val lang = getLangFromAcceptHeader(request)
    setResponseHeaders(Map("Content-Type" -> lang.getContentType.toHeaderString))(request)
    RdfConversions.validateWithShacl(
      dataid.getBytes,
      shacl.getBytes,
      defaultLang
    ).flatMap(r => RdfConversions.graphToBytes(r.getGraph, lang, None))
      .map(new String(_))
  }

  override def deleteFileMapException400(e: Throwable)(request: HttpServletRequest): Option[OperationFailure] = e match {
    case _: GraphDoesNotExistException => Some(OperationFailure(e.getMessage))
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

  private def getPrefix(request: HttpServletRequest): String = {
    val url = new URL(request.getRequestURL.toString)
    s"${url.getProtocol}://${url.getHost}:${url.getPort}${config.defaultGraphIdPrefix}/"
  }

  private def readFile(username: String, path: String)(request: HttpServletRequest): Try[String] = {
    val p = gitPath(path)
    val lang = getLangFromAcceptHeader(request)
    setResponseHeaders(Map("Content-Type" -> lang.getContentType.toHeaderString))(request)
    client.readFile(username, p)
      .flatMap(body => {
        val ctxUri = contextUri(body, defaultLang)
        readModel(body, defaultLang, ctxUri)
          .flatMap(m =>
            graphToBytes(m.getGraph, lang, ctxUri)
          )
      })
      .map(new String(_))
  }

  private def getLangFromAcceptHeader(request: HttpServletRequest) =
    Option(request.getHeader("Accept"))
      .map(RdfConversions.mapContentType(_, defaultLang))
      .getOrElse(defaultLang)

  private def gitPath(path: String): String = {
    val pa = Paths.get(path)
    if (pa.isAbsolute) {
      Paths.get("/").relativize(pa).toString
    } else {
      path
    }
  }

  private[databus] def saveToVirtuoso[T](data: Array[Byte], lang: Lang, graphId: String)(execInTransaction: => Try[T]): Try[T] =
    readModel(data, lang, contextUri(data, lang))
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
    )(_ => execInTransaction)
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

  class GraphDoesNotExistException(id: String) extends Exception(s"Graph $id does not exist")

  case class Config(storageSparqlEndpointUri: Uri,
                    storageUser: String,
                    storagePass: String,
                    storageJdbcPort: Option[Int],
                    storageClass: String,
                    storageDbName: Option[String],
                    defaultGraphIdPrefix: String,
                    // that is for using local jgit git provider
                    gitLocalDir: Option[Path],
                    // props below are for using gitlab as a git provider
                    gitApiUser: Option[String],
                    gitApiPass: Option[String],
                    // TODO isn't URI enough here
                    gitApiSchema: Option[String],
                    gitApiHost: Option[String],
                    gitApiPort: Option[Int],
                    restrictEditsToLocalhost: Boolean)

  object Config {

    def default: Config = fromMapper(SystemMapper)

    def fromWebXml(xml: Node): Config = fromMapper(xml)

    def fromServletContext(ctx: ServletContext): Config = fromMapper(ctx)

    private def fromMapper(mapper: Mapper): Config = {
      implicit val mp = mapper

      val defaultGraphIdPrefix = getParam("defaultGraphIdPrefix").get

      val storageSparqlEndpointUri = getParam("storageSparqlEndpointUri").get
      val stUri = if (storageSparqlEndpointUri.endsWith("/")) storageSparqlEndpointUri.dropRight(1) else storageSparqlEndpointUri
      val storageUser = getParam("storageUser").get
      val storagePass = getParam("storagePass").get
      val storageJdbcPort = getParam("storageJdbcPort").map(_.toInt)
      val storageClass = getParam("storageClass").get
      val storageDbName = getParam("storageDbName")

      val gitLocalDir: Option[Path] = getParam("gitLocalDir").map(Paths.get(_))

      val gitApiUser = getParam("gitApiUser")
      val gitApiPass = getParam("gitApiPass")

      // TODO isn't URI enough here
      val gitApiSchema = getParam("gitApiSchema").orElse(Some("http"))
      val gitApiHost = getParam("gitApiHost").orElse(Some("localhost"))
      val gitApiPort = getParam("gitApiPort").map(_.toInt)
      val restrictEditsToLocalhost = getParam("restrictEditsToLocalhost")
        .map(_.toBoolean)
        .getOrElse(false)

      ApiImpl.Config(
        Uri.parse(stUri).right.get,
        storageUser,
        storagePass,
        storageJdbcPort,
        storageClass,
        storageDbName,
        defaultGraphIdPrefix,
        gitLocalDir,
        gitApiUser,
        gitApiPass,
        // TODO isn't URI enough here
        gitApiSchema,
        gitApiHost,
        gitApiPort,
        restrictEditsToLocalhost
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


