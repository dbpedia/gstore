package org.dbpedia.databus


import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.{InetAddress, URL}
import com.github.jsonldjava.core
import com.github.jsonldjava.core.{JsonLdConsts, JsonLdOptions}
import com.github.jsonldjava.utils.JsonUtils
import com.mchange.v2.c3p0.ComboPooledDataSource
import org.apache.jena.atlas.json.JsonString
import org.apache.jena.graph.{Graph, Node}
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.lang.JsonLDReader
import org.apache.jena.riot.system.{ErrorHandler, ErrorHandlerFactory, StreamRDFLib}
import org.apache.jena.riot.writer.JsonLDWriter
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFFormat, RDFLanguages, RDFParserBuilder, RDFWriter, RIOT}
import org.apache.jena.shacl.{ShaclValidator, Shapes, ValidationReport}
import org.apache.jena.sparql.util
import org.dbpedia.databus.ApiImpl.Config
import org.slf4j.LoggerFactory
import sttp.client3.{DigestAuthenticationBackend, HttpURLConnectionBackend, basicRequest}
import sttp.model.Uri

import scala.util.{Failure, Success, Try}


trait SparqlClient {

  def executeUpdates[T](q1: String, qX: String*)(execInTransaction: Map[String, Int] => Try[T]): Try[T]

}


object SparqlClient {

  // todo not a perfect solution (names of classes hardcoded as strings), needs improvement
  def get(config: Config): SparqlClient = config.storageClass match {
    case "org.dbpedia.databus.HttpVirtClient" =>
      new HttpVirtClient(
        config.storageSparqlEndpointUri,
        config.storageUser,
        config.storagePass)
    case "org.dbpedia.databus.VirtuosoJDBCClient" =>
      new VirtuosoJDBCClient(
        config.storageSparqlEndpointUri.host,
        config.storageJdbcPort.get,
        config.storageUser,
        config.storagePass
      )
    case "org.dbpedia.databus.FusekiJDBCClient" =>
      new FusekiJDBCClient(
        config.storageSparqlEndpointUri.host,
        config.storageJdbcPort.get,
        config.storageUser,
        config.storagePass,
        config.storageDbName.get
      )
  }

}

class HttpVirtClient(virtUri: Uri, virtUser: String, virtPass: String) extends SparqlClient {

  import HttpVirtClient._

  private lazy val backend = new DigestAuthenticationBackend(HttpURLConnectionBackend())

  //todo this wont work for now, because the number of lines processed is not returned
  def executeUpdates[T](q: String, qX: String*)(trans: Map[String, Int] => Try[T]): Try[T] = {
    val fq = qX.foldLeft(q)((l, r) => l + ";\n" + r)
    val vr = virtuosoRequest(
      fq,
      virtUri,
      virtUser,
      virtPass
    )
    val re = backend.send(vr).body match {
      case Left(s) =>
        Failure(new RuntimeException(s))
      case Right(s) =>
        Success(s)
    }
    re.flatMap(_ => trans(Map.empty))
  }

}

object HttpVirtClient {

  private[databus] def virtuosoRequest(request: String, virtuosoUri: Uri, un: String, pass: String) =
    basicRequest
      .post(virtuosoUri)
      .body("query" -> request)
      .auth.digest(un, pass)

}


abstract class JdbcCLient(connectionString: String, user: String, pass: String) extends SparqlClient {

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  private lazy val ds = {
    val cpds = new ComboPooledDataSource()
    cpds.setJdbcUrl(connectionString)
    cpds.setUser(user)
    cpds.setPassword(pass)
    cpds.setMinPoolSize(5)
    cpds.setAcquireIncrement(5)
    cpds.setMaxPoolSize(20)
    cpds
  }

  def preprocessQuery(query: String): String

  def executeUpdates[T](q: String, qX: String*)(trans: Map[String, Int] => Try[T]): Try[T] = {
    val conn = ds.getConnection
    val upds = Seq(q) ++ qX
    val batch_size = upds.length
    Try {
      upds
        .map(s => (s, conn.prepareStatement(preprocessQuery(s))))
        .zipWithIndex
        .map {
          case ((str, stmt), index) =>
            if (log.isDebugEnabled) {
              log.debug(s"Preparing to execute in transaction query ${index + 1} of ${batch_size}:\n${str}")
            }
            (str, stmt.executeUpdate())
        }.toMap
    }
      .flatMap(r => trans(r))
      .flatMap(r => Try {
        conn.commit()
        conn.close()
        r
      })
      .recoverWith {
        case err =>
          Try(conn.rollback())
            .flatMap(_ => Try(conn.close()))
            .flatMap(_ => Failure(err))
      }
  }

}

class VirtuosoJDBCClient(host: String, port: Int, user: String, pass: String) extends JdbcCLient(s"jdbc:virtuoso://$host:$port/charset=UTF-8", user, pass) {
  override def preprocessQuery(query: String): String = "sparql\n" + query
}

class FusekiJDBCClient(host: String, port: Int, user: String, pass: String, dataset: String) extends JdbcCLient(s"jdbc:jena:remote:query=http://$host:$port/$dataset/query&update=http://$host:$port/$dataset/update", user, pass) {
  override def preprocessQuery(query: String): String = query
}

object RdfConversions {

  private lazy val CachingContext = initCachingContext()

  val DefaultShaclLang = Lang.TTL

  def readModel(data: Array[Byte], lang: Lang, context: Option[util.Context]): Try[(Model, List[Warning])] = Try {
    val model = ModelFactory.createDefaultModel()
    val dataStream = new ByteArrayInputStream(data)
    val dest = StreamRDFLib.graph(model.getGraph)
    val parser = RDFParserBuilder.create()
      .source(dataStream)
      .base(null)
      .lang(lang)

    val eh = newErrorHandlerWithWarnings
    parser.errorHandler(eh)

    context.foreach(cs =>
      parser.context(cs))

    parser.parse(dest)
    (model, eh.warningsList)
  }

  def graphToBytes(model: Graph, outputLang: Lang, context: Option[URL]): Try[Array[Byte]] = Try {
    val str = new ByteArrayOutputStream()
    val builder = RDFWriter.create.format(langToFormat(outputLang))
      .source(model)

    context.foreach(ctx => {
      val jctx = jenaContext(CachingContext.parse(ctx.toString))
      builder.context(jctx)
      builder.set(JsonLDWriter.JSONLD_CONTEXT_SUBSTITUTION, new JsonString(ctx.toString))
    })

    builder
      .build()
      .output(str)
    str.toByteArray
  }

  def validateWithShacl(model: Model, shacl: Graph): Try[ValidationReport] =
    Try(
      ShaclValidator.get()
        .validate(Shapes.parse(shacl), model.getGraph)
    )

  def validateWithShacl(file: Array[Byte], shaclData: Array[Byte], fileCtx: Option[util.Context], shaclCtx: Option[util.Context], modelLang: Lang): Try[ValidationReport] =
    for {
      (shaclGra, _) <- readModel(shaclData, DefaultShaclLang, shaclCtx)
      (model, _) <- readModel(file, modelLang, fileCtx)
      re <- validateWithShacl(model, shaclGra.getGraph)
    } yield re

  def validateWithShacl(file: Array[Byte], fileCtx: Option[util.Context], shaclUri: String, modelLang: Lang): Try[ValidationReport] =
    for {
      shaclGra <- Try(RDFDataMgr.loadGraph(shaclUri))
      (model, _) <- readModel(file, modelLang, fileCtx)
      re <- validateWithShacl(model, shaclGra)
    } yield re

  def langToFormat(lang: Lang): RDFFormat = lang match {
    case RDFLanguages.TURTLE => RDFFormat.TURTLE_PRETTY
    case RDFLanguages.TTL => RDFFormat.TTL
    case RDFLanguages.JSONLD => RDFFormat.JSONLD_FLATTEN_PRETTY
    case RDFLanguages.TRIG => RDFFormat.TRIG_PRETTY
    case RDFLanguages.RDFXML => RDFFormat.RDFXML_PRETTY
    case RDFLanguages.RDFTHRIFT => RDFFormat.RDF_THRIFT
    case RDFLanguages.NTRIPLES => RDFFormat.NTRIPLES
    case RDFLanguages.NQUADS => RDFFormat.NQUADS
    case RDFLanguages.TRIX => RDFFormat.TRIX
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
      case _ => "application/ld+json"
    }

  def mapContentType(cn: String, default: Lang): Lang =
    cn match {
      case "text/turtle" => Lang.TURTLE
      case "application/rdf+xml" => Lang.RDFXML
      case "application/n-triples" => Lang.NTRIPLES
      case "application/ld+json" => Lang.JSONLD
      case "text/trig" => Lang.TRIG
      case "application/n-quads" => Lang.NQUADS
      case "application/trix+xml" => Lang.TRIX
      case "application/rdf+thrift" => Lang.RDFTHRIFT
      case _ => default
    }

  import org.apache.jena.graph.Triple

  def generateGraphId(prefix: String, user: String, path: String): String =
    s"$prefix$user/$path"

  def clearGraphSparqlQuery(graphId: String) =
    s"CLEAR SILENT GRAPH <$graphId>"

  def dropGraphSparqlQuery(graphId: String) =
    s"DROP SILENT GRAPH <$graphId>"

  def makeInsertSparqlQuery(triples: Seq[Triple], graphId: String): String = {
    val bld = StringBuilder.newBuilder
    bld.append("INSERT DATA { GRAPH ")
    wrapWithAngleBracketsQuote(bld, graphId)
    bld.append(" {\n")
    generateQueryTriples(bld, triples)
    bld.append("}").append("}").toString()
  }

  def generateQueryTriples(bld: StringBuilder, triples: Seq[Triple]): StringBuilder = {
    triples.foreach(t => {
      wrapWithAngleBracketsQuote(bld, t.getSubject.toString())
      bld.append(" ")
      wrapWithAngleBracketsQuote(bld, t.getPredicate.toString())
      bld.append(" ")
      if (t.getObject.isLiteral) {
        bld.append(getStrFromLiteral(t.getObject))
      } else {
        wrapWithAngleBracketsQuote(bld, escapeString(t.getObject.toString()))
      }
      bld.append(" ")
      bld.append(".")
      bld.append("\n")
    })
    bld
  }

  def wrapWithAngleBracketsQuote(bld: StringBuilder, s: String) = {
    bld.append("<")
    bld.append(s)
    bld.append(">")
  }

  def contextUrl(data: Array[Byte], lang: Lang): Option[URL] =
    if (lang == Lang.JSONLD) {
      jsonLdContextUrl(data)
        .get
    } else {
      None
    }

  def jenaJsonLdContextWithFallbackForLocalhost(jsonLdContextUrl: URL, requestHost: String): Try[util.Context] =
    jsonLdContextWithFallbackForLocalhost(jsonLdContextUrl, requestHost)
      .map(jenaContext)

  private def jsonLdContextUrl(data: Array[Byte]): Try[Option[URL]] =
    Try(
      JsonUtils.fromString(new String(data))
    )
      .map(j =>
        Try(j.asInstanceOf[java.util.Map[String, Object]]).toOption)
      .map(_.flatMap(c =>
        Option(c.get(JsonLdConsts.CONTEXT))
          .map(_.toString)
          .flatMap(ctx =>
            Try(new URL(ctx)) match {
              case Failure(_) => None
              case Success(uri) => Some(uri)
            })))

  private def jsonLdContextWithFallbackForLocalhost(jsonLdContextUrl: URL, requestHost: String): Try[core.Context] =
    Try(CachingContext.parse(jsonLdContextUrl.toString))
      .recoverWith {
        case e =>
          if (InetAddress.getByName(jsonLdContextUrl.getHost).isLoopbackAddress) {
            preloadLocalhostContextFromRequestHost(jsonLdContextUrl.toString, requestHost)
          } else {
            Failure(e)
          }
      }

  private def jenaContext(jsonLdCtx: core.Context) = {
    val context: util.Context = RIOT.getContext.copy()
    jsonLdCtx.putAll(jsonLdCtx.getPrefixes(true))
    context.put(JsonLDWriter.JSONLD_CONTEXT, jsonLdCtx)
    context.put(JsonLDReader.JSONLD_CONTEXT, jsonLdCtx)
    context
  }

  private def preloadLocalhostContextFromRequestHost(localhostCtxUri: String, requestHost: String): Try[core.Context] = Try {
    val ctxUrl = new URL(localhostCtxUri)
    val addressWithIp = localhostCtxUri.replace(ctxUrl.getHost, requestHost)
    val ctx = CachingContext.parse(addressWithIp)
    CachingContext.putInCache(localhostCtxUri, ctx)
    ctx
  }

  private def initCachingContext() = {
    val opts = new JsonLdOptions(null)
    opts.useNamespaces = true
    new CachingJsonldContext(30, opts)
  }

  private def escapeString(s: String) = {
    val sb = new StringBuilder(s.length())
    val slen = s.length()
    for (i <- 0 until slen) {
      val c = s.charAt(i)
      if (c == '\\') {
        sb.append("\\\\")
      } else if (c == '"') {
        sb.append("\\\"")
      } else if (c == '\n') {
        sb.append("\\n")
      } else if (c == '\r') {
        sb.append("\\r")
      } else if (c == '\t') {
        sb.append("\\t")
      } else if (c >= 0 && c <= '\b' || c == 11 || c == '\f' || c >= 14 && c <= 31 || c >= 127 && c <= '\uffff') {
        sb.append("\\u")
        sb.append(toHexString(c, 4))
      } else if (c >= 65536 && c <= 1114111) {
        sb.append("\\U")
        sb.append(toHexString(c, 8))
      } else {
        sb.append(c)
      }
    }
    sb.toString()
  }

  private def getStrFromLiteral(n: Node): String = {
    var llang_exists = false
    val sb = new StringBuilder()
    sb.append("\"")
    sb.append(escapeString(n.getLiteralLexicalForm()))
    sb.append("\"")
    val llang = n.getLiteralLanguage()
    if (llang != null && llang.length() > 0) {
      sb.append("@")
      sb.append(llang)
      llang_exists = true
    }
    val ltype = n.getLiteralDatatypeURI()
    if (!llang_exists && ltype != null && ltype.length() > 0 && !ltype.equals("http://www.w3.org/2001/XMLSchema#string")) {
      sb.append("^^<")
      sb.append(ltype)
      sb.append(">")
    }
    sb.toString()
  }

  private def toHexString(decimal: Int, stringLength: Int) = {
    val sb = new StringBuilder(stringLength)
    val hexVal = Integer.toHexString(decimal).toUpperCase()
    val nofZeros = stringLength - hexVal.length()
    for (_ <- 0 until nofZeros) {
      sb.append('0')
    }
    sb.append(hexVal)
    sb.toString()
  }

  case class Warning(message: String)
  private class ErrorHandlerWithWarnings extends ErrorHandler {
    private val defaultEH = ErrorHandlerFactory.getDefaultErrorHandler

    private var warnings: List[Warning] = List.empty

    import org.apache.jena.riot.SysRIOT.fmtMessage

    override def warning(message: String, line: Long, col: Long): Unit = {
      warnings = warnings :+ Warning(fmtMessage(message, line, col))
      defaultEH.warning(message, line, col)
    }

    override def error(message: String, line: Long, col: Long): Unit =
      defaultEH.error(message, line, col)

    override def fatal(message: String, line: Long, col: Long): Unit =
      defaultEH.fatal(message, line, col)

    def warningsList: List[Warning] = warnings
  }

  private def newErrorHandlerWithWarnings: ErrorHandlerWithWarnings =
    new ErrorHandlerWithWarnings

}


