package org.dbpedia.databus

import java.io.FileNotFoundException
import java.net.URL
import java.nio.file.{NoSuchFileException, Path, Paths}
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.Lang
import org.apache.jena.shared.JenaException
import org.apache.jena.sys.JenaSystem
import org.dbpedia.databus.ApiImpl.Config
import org.dbpedia.databus.RdfConversions.{contextUrl, generateGraphId, graphToBytes, jenaJsonLdContextWithFallbackForLocalhost, mapContentType, readModel}
import org.dbpedia.databus.swagger.api.DatabusApi
import org.dbpedia.databus.swagger.model.{HistoryEntry, OperationFailure, OperationSuccess}
import org.eclipse.jgit.errors.{MissingObjectException, RepositoryNotFoundException}
import sttp.model.Uri
import virtuoso.jdbc4.VirtuosoException

import scala.util.{Failure, Success, Try}
import scala.xml.Node
import collection.JavaConverters._


class ApiImpl(config: Config) extends DatabusApi {

  import ApiImpl._

  private val client: GitClient = initGitClient(config)
  private val defaultLang = Lang.JSONLD10
  private lazy val sparqlClient: SparqlClient = SparqlClient.get(config)
  init()

  def init() = JenaSystem.init()

  def stop() = JenaSystem.shutdown()


  override def dataidSubgraph(body: String)(request: HttpServletRequest): Try[String] =
    readModel(
      body.getBytes,
      defaultLang,
      null,
      contextUrl(body.getBytes, defaultLang)
        .map(jenaJsonLdContextWithFallbackForLocalhost(_, request.getRemoteHost).get)
    )
      .flatMap(m => Tractate.extract(m._1.getGraph, TractateV1.Version))
      .map(_.stringForSigning)

  override def deleteFile(username: String,
                          path: String,
                          prefix: Option[String],
                          author_name: Option[String],
                          author_email: Option[String])(request: HttpServletRequest): Try[OperationSuccess] = {
    val gid = generateGraphId(prefix.getOrElse(getPrefix(request)), username, path)
    validateEmail(author_email)
      .flatMap(email =>
        sparqlClient.executeUpdates(
          RdfConversions.dropGraphSparqlQuery(gid)
        )(m => {
          if (m.map(_._2).sum > 0) {
            deleteFileFromGit(username, path, author_name, email)(request)
              .map(hash => OperationSuccess(gid, hash))
          } else {
            Failure(new GraphDoesNotExistException(gid))
          }
        })
      )
  }

  override def getFile(repo: String, path: String)(request: HttpServletRequest): Try[String] =
    readFile(repo, path)(request)


  override def saveFile(repo: String,
                        path: String,
                        body: String,
                        prefix: Option[String],
                        author_name: Option[String],
                        author_email: Option[String])
                       (request: HttpServletRequest): Try[OperationSuccess] = {

    val pa = gitPath(path)
    val graphId = generateGraphId(prefix.getOrElse(getPrefix(request)), repo, pa)
    val ct = Option(request.getContentType)
      .map(_.toLowerCase)
      .getOrElse("")
    val lang = mapContentType(ct, defaultLang)
    val ctxU = contextUrl(body.getBytes, lang)
    val ctx = ctxU.map(cu => jenaJsonLdContextWithFallbackForLocalhost(cu, request.getRemoteHost).get)
    val baseUrl = getPrefix(request) + gitPath(path)
    validateEmail(author_email).flatMap(email =>
      readModel(body.getBytes, lang, baseUrl, ctx)
        .flatMap(model => {
          saveToVirtuoso(model._1, graphId)({
            saveFiles(repo, Map(pa -> body.getBytes), author_name, email)
                .map(hash => OperationSuccess(graphId, hash))
          }).transform(Success(_), e =>
            if (model._2.isEmpty) {
              Failure(e)
            } else {
              val ee = new RuntimeException(
                s"Error saving data, potentially caused by: ${model._2.map(_.message).fold("")((l, r) => l + '\n' + r)}",
                e)
              ee.setStackTrace(Array.empty)
              Failure(ee)
            })
        })
    )
  }

  override def shaclValidate(dataid: String, shacl: String)(request: HttpServletRequest): Try[String] = {
    val lang = getLangFromAcceptHeader(request)
    setResponseHeaders(Map("Content-Type" -> lang.getContentType.toHeaderString))(request)
    val ctxU = contextUrl(dataid.getBytes, lang)
    val ctx = ctxU.map(cu => jenaJsonLdContextWithFallbackForLocalhost(cu, request.getRemoteHost).get)

    val shaclU = contextUrl(shacl.getBytes, RdfConversions.DefaultShaclLang)
    val shaclCtx = shaclU.map(cu => jenaJsonLdContextWithFallbackForLocalhost(cu, request.getRemoteHost).get)

    RdfConversions.validateWithShacl(
      dataid.getBytes,
      shacl.getBytes,
      ctx,
      shaclCtx,
      defaultLang
    ).flatMap(r => RdfConversions.graphToBytes(r.getGraph, lang, None))
      .map(new String(_))
  }

  override def deleteFileMapException400(e: Throwable)(request: HttpServletRequest): Option[OperationFailure] = e match {
    case _: GraphDoesNotExistException => Some(OperationFailure(e.getMessage))
    case _: BadEmailException => Some(OperationFailure(e.getMessage))
    case _ => None
  }

  override def getFileMapException404(e: Throwable)(request: HttpServletRequest): Option[OperationFailure] = e match {
    case _: FileNotFoundException => Some(OperationFailure(e.getMessage))
    case _: NoSuchFileException => Some(OperationFailure(e.getMessage))
    case _: RepositoryNotFoundException => Some(OperationFailure("File not found."))
    case _: MissingObjectException => Some(OperationFailure("File not found."))
    case _ => None
  }

  override def saveFileMapException400(e: Throwable)(request: HttpServletRequest): Option[OperationFailure] = e match {
    case _: JenaException => Some(OperationFailure(e.getMessage))
    case _: VirtuosoException if e.getMessage.contains("SQ200") => Some(OperationFailure(s"Wrong value for type. ${e.getMessage}"))
    case _: RuntimeException if e.getCause.isInstanceOf[VirtuosoException] && e.getCause.getMessage.contains("SQ200") =>
      Some(OperationFailure(s"Wrong value for type. ${e.getCause.getMessage}. ${e.getMessage}"))
    case _: RuntimeException if e.getCause.isInstanceOf[VirtuosoException] && e.getCause.getMessage.contains("SQ074") =>
      Some(OperationFailure(s"Wrong input data. ${e.getCause.getMessage}. ${e.getMessage}"))
    case _: BadEmailException => Some(OperationFailure(e.getMessage))
    case _ => None
  }

  override def shaclValidateMapException400(e: Throwable)(request: HttpServletRequest): Option[OperationFailure] =
    e match {
      case _ => Some(OperationFailure(e.getMessage))
    }

  def history(repo: String, limit: Option[Int])(request: HttpServletRequest): scala.util.Try[List[HistoryEntry]] =
    client.getHistory(repo, limit.getOrElse(10)).map(_.map(c => HistoryEntry(c.hash, c.author, c.email)).toList)

  def historyMapException400(e: Throwable)(request: HttpServletRequest): Option[OperationFailure] =
    e match {
      case _ => Some(OperationFailure(e.getMessage))
    }


  private def getPrefix(request: HttpServletRequest): String = {
    val url = new URL(request.getRequestURL.toString)
    s"${url.getProtocol}://${url.getHost}:${url.getPort}${config.defaultGraphIdPrefix}/"
  }

  private def readFile(username: String, path: String)(request: HttpServletRequest): Try[String] = {
    val p = gitPath(path)
    val lang = getLangFromAcceptHeader(request)
    val baseUrl = getPrefix(request) + p
    setResponseHeaders(Map("Content-Type" -> lang.getContentType.toHeaderString))(request)
    client.readFile(username, p)
      .flatMap(body => {
        val ctxUri = contextUrl(body, defaultLang)
        readModel(
          body,
          defaultLang,
          baseUrl,
          contextUrl(body, defaultLang)
            .map(jenaJsonLdContextWithFallbackForLocalhost(_, request.getRemoteHost).get)
        )
          .flatMap(m =>
            graphToBytes(m._1.getGraph, lang, ctxUri)
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

  private def saveFiles(username: String,
                        fullFilenamesAndData: Map[String, Array[Byte]],
                        author_name: Option[String],
                        author_email: Option[String]): Try[String] =
    (if (!client.projectExists(username)) {
      client.createProject(username)
    } else {
      Success(Unit)
    }).flatMap(_ => client.commitSeveralFiles(username, fullFilenamesAndData, author_name, author_email))


  private def deleteFileFromGit(username: String,
                                path: String,
                                author_name: Option[String],
                                author_email: Option[String])(request: HttpServletRequest): Try[String] = {
    val p = gitPath(path)
    deleteFiles(username, Seq(p), author_name, author_email)(request)
  }

  private def deleteFiles(username: String,
                          paths: Seq[String],
                          author_name: Option[String],
                          author_email: Option[String])(request: HttpServletRequest): Try[String] =
    client.deleteSeveralFiles(username, paths, author_name, author_email)

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

  private final val Email_Pattern = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])".r

  def validateEmail(email: Option[String]): Try[Option[String]] =
    email match {
      case None => Success(None)
      case Some(e) => Try {
        if (Email_Pattern.unapplySeq(e).isDefined) {
          email
        } else {
          throw new BadEmailException(e);
        }
      }
    }

  class BadEmailException(email: String) extends Exception(s"The $email is not correct email.")

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


