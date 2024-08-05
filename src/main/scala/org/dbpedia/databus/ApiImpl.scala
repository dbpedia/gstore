package org.dbpedia.databus

import java.io.FileNotFoundException
import java.net.URL
import java.nio.file.{NoSuchFileException, Path, Paths}
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import org.apache.jena.rdf.model.Model
import org.apache.jena.shared.JenaException
import org.apache.jena.sys.JenaSystem
import org.dbpedia.databus.ApiImpl.Config
import org.dbpedia.databus.RdfConversions.ContentFormat.rdf
import org.dbpedia.databus.RdfConversions.{ContentFormat, GraphBytesExtractor, JSONLD, RDFContentFormat, generateGraphId}
import org.dbpedia.databus.swagger.api.DatabusApi
import org.dbpedia.databus.swagger.model.{HistoryEntry, OperationFailure, OperationSuccess}
import org.eclipse.jgit.errors.{MissingObjectException, RepositoryNotFoundException}
import sttp.model.Uri
import virtuoso.jdbc4.VirtuosoException

import scala.util.{Failure, Success, Try}
import scala.xml.Node
import collection.JavaConverters._


class ApiImpl(config: Config, batchSize: Int = 1000) extends DatabusApi {

  val SPARQLBatchSize = batchSize

  import ApiImpl._

  private val client: GitClient = initGitClient(config)
  private val DefaultFormat = JSONLD
  private lazy val sparqlClient: SparqlClient = SparqlClient.get(config)
  init()

  def init(): Unit = JenaSystem.init()

  def stop(): Unit = JenaSystem.shutdown()

  // todo NOTICE! this may fail with relative URIS, NOT TESTED!
  override def dataidSubgraph(body: Array[Byte])(request: HttpServletRequest): Try[String] =
    RdfConversions.RDFGraphSerialiser.init(DefaultFormat, DefaultFormat, body, None).graph
      .flatMap(m => Tractate.extract(m._1.getGraph, TractateV1.Version))
      .map(_.stringForSigning)

  override def deleteFile(username: String,
                          path: String,
                          prefix: Option[String],
                          author_name: Option[String],
                          author_email: Option[String])(request: HttpServletRequest): Try[OperationSuccess] = {
    val gid = generateGraphId(prefix.getOrElse(getPrefix(request)), username, path)
    wrapWithUnsupportedException(ContentFormat.fromPath(path), path).flatMap { format =>
      validateEmail(author_email).flatMap { email =>
        val delete: () => Try[OperationSuccess] = () =>
          deleteFileFromGit(username, path, author_name, email)(request)
            .map(hash => OperationSuccess(gid, hash))
        rdf(format).map { _ =>
          sparqlClient.executeUpdates(
            RdfConversions.dropGraphSparqlQuery(gid)
          )(m => {
            if (m.values.sum > 0) {
              delete()
            } else {
              Failure(new GraphDoesNotExistException(gid))
            }
          })
        }.getOrElse(delete())
      }
    }
  }

  override def getFile(repo: String, path: String)(request: HttpServletRequest): Try[String] =
    wrapWithUnsupportedException(formatFromPath(path), path)
      .flatMap(_ =>
        client.readFile(repo, gitPath(path))
          .map(new String(_)))


  override def saveFile(body: Array[Byte],
                        repo: String,
                        path: String,
                        prefix: Option[String],
                        author_name: Option[String],
                        author_email: Option[String])
                       (request: HttpServletRequest): Try[OperationSuccess] = {
    val pa = gitPath(path)
    val graphId = generateGraphId(prefix.getOrElse(getPrefix(request)), repo, pa)
    validateEmail(author_email).flatMap(email => {
      wrapWithUnsupportedException(formatFromPath(path), path).flatMap { format =>
        val saveRawBody: () => Try[OperationSuccess] = () => saveFiles(
          repo,
          Map(
            pa -> body
          ),
          author_name,
          email)
          .map(hash => OperationSuccess(graphId, hash))
        rdfExtractor(format).map { extr =>
          RdfConversions.RDFGraphSerialiser.init(extr._2, extr._1, body, Some(graphId)).graph
            .flatMap(model => {
              saveToVirtuoso(model._1, graphId)(saveRawBody())
                .transform(Success(_), e =>
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
        }.getOrElse(saveRawBody())
      }
    })
  }


  override def getGraph(repo: String, path: String, prefix: Option[String])(request: javax.servlet.http.HttpServletRequest): scala.util.Try[String] =
    readGraph(repo, path, prefix)(request)

  override def getGraphMapException404(e: Throwable)(request: javax.servlet.http.HttpServletRequest): Option[org.dbpedia.databus.swagger.model.OperationFailure] =
    getFileMapException404(e)(request)
  override def shaclValidate(dataid: Array[Byte], shacl: Array[Byte])(request: HttpServletRequest): Try[String] = {
    val outLang = getLangFromAcceptHeader(request).flatMap(rdf).getOrElse(DefaultFormat)
    setResponseHeaders(Map("Content-Type" -> outLang.lang.getContentType.toHeaderString))(request)

    RdfConversions.validateWithShacl(
      dataid,
      shacl,
      DefaultFormat
    ).flatMap(r => RdfConversions.RDFGraphSerialiser.serialiseGraph(r.getGraph, outLang, None, None))
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
    case _: ArrayIndexOutOfBoundsException => Some(OperationFailure("File not found"))
    case _: MissingObjectException => Some(OperationFailure("File not found."))
    case _: UnsupportedFormatException => Some(OperationFailure(e.getMessage))
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
    case _: UnsupportedFormatException => Some(OperationFailure(e.getMessage))
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

  private def readGraph(username: String, path: String, prefix: Option[String])(request: HttpServletRequest): Try[String] = {
    val p = gitPath(path)
    val outFormat = getLangFromAcceptHeader(request)
      .collect { case c: RDFContentFormat => c }
      .getOrElse(DefaultFormat)
    val graphId = generateGraphId(prefix.getOrElse(getPrefix(request)), username, path)
    wrapWithUnsupportedException(formatFromPath(path)
      .flatMap(rdfExtractor),
      path)
      .flatMap(format => {
        setResponseHeaders(Map("Content-Type" -> outFormat.lang.getContentType.toHeaderString))(request)
        client.readFile(username, p)
          .flatMap(body => {
            RdfConversions.RDFGraphSerialiser
              .init(format._2, format._1, body, Some(graphId))
              .graphBytes(outFormat)
          })
          .map(new String(_))
      })
  }


  private def wrapWithUnsupportedException[T](a: Option[T], path: String): Try[T] = a match {
    case None => Failure(new UnsupportedOperationException(path))
    case Some(c) => Success(c)
  }

  private def formatFromPath(path: String) =
    ContentFormat.fromPath(path)

  private def rdfExtractor(format: ContentFormat) =
    ContentFormat.rdf(format).map((format, _))
      .flatMap(c => GraphBytesExtractor.fromContent(c._1).map((_, c._2)))

  private def getLangFromAcceptHeader(request: HttpServletRequest) =
    Option(request.getHeader("Accept"))
      .flatMap(RdfConversions.ContentFormat.fromContentType)

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
      .grouped(SPARQLBatchSize)
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

  class UnsupportedFormatException(path: String) extends Exception(s"The file of $path has an extension which is not supported.")

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
                    restrictEditsToLocalhost: Boolean,
                    defaultJsonldLocalhostContext: Option[String],
                    defaultJsonldLocalhostContextLocation: Option[String])



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

      val defaultJsonldLocalhostContext = getParam("defaultJsonldLocalhostContext")
      val defaultJsonldLocalhostContextLocation = getParam("defaultJsonldLocalhostContextLocation")

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
        restrictEditsToLocalhost,
        defaultJsonldLocalhostContext,
        defaultJsonldLocalhostContextLocation
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


