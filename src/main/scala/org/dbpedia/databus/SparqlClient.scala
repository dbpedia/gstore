package org.dbpedia.databus


import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.URL
import com.github.jsonldjava.core
import com.github.jsonldjava.core.{JsonLdConsts, JsonLdOptions}
import com.github.jsonldjava.utils.JsonUtils
import com.mchange.v2.c3p0.ComboPooledDataSource
import org.apache.jena.atlas.json.JsonString
import org.apache.jena.graph.{Graph, Node}
import org.apache.jena.iri.ViolationCodes
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.lang.LangJSONLD10
import org.apache.jena.riot.system.{ErrorHandler, ErrorHandlerFactory, StreamRDFLib}
import org.apache.jena.riot.writer.JsonLD10Writer
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFFormat, RDFLanguages, RDFParser, RDFParserBuilder, RDFWriter, RDFWriterBuilder, RIOT}
import org.apache.jena.shacl.{ShaclValidator, Shapes, ValidationReport}
import org.apache.jena.sparql.util
import org.dbpedia.databus.ApiImpl.Config
import org.dbpedia.databus.RdfConversions.RDFGraphSerialiser.serialiseGraph
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
          log.error(s"Failed SPARQL request batch:\n${upds.fold("")((l, r) => l + "\n" + r)}")
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

  val DefaultShaclLang = Turtle

  sealed trait ContentFormat {
    def extensions: Set[String]
  }

  object ContentFormat {
    private val AllMembers: Set[ContentFormat] = Set(
      JSON,
      RDFThrift,
      JSONLD,
      Turtle,
      NTriples,
      NQuads,
      Trig,
      Trix,
      RDFXML
    )
    private val extToFmt = AllMembers.flatMap(f => f.extensions.map(e => (e.toLowerCase, f))).toMap
    private val ctToFmt = AllMembers.collect { case rdf: RDFContentFormat => rdf }
      .map(ct => (ct.lang.getContentType.toHeaderString.toLowerCase, ct)).toMap

    //todo make the same as with the extensions? make content type a field of ContentFormat?
    def fromContentType(cn: String): Option[ContentFormat] =
      Try {
        cn.toLowerCase match {
          case "application/json" => JSON
          case other => ctToFmt(other)
        }
      }.toOption

    def fromPath(path: String): Option[ContentFormat] = fromExt(path.split('.').last)

    def fromExt(ext: String): Option[ContentFormat] = extToFmt.get(ext.toLowerCase)

    /*
    * This is the main mapping function of the formats, new non-rdf formats with RDF content must be added here
    * */
    def rdf(cf: ContentFormat): Option[RDFContentFormat] = cf match {
      case JSON => Some(JSONLD)
      case other: RDFContentFormat => Some(other)
      case _ => None
    }

  }

  sealed trait RDFContentFormat extends ContentFormat {
    def lang: Lang

    def format: RDFFormat
  }

  case object JSON extends ContentFormat {
    override def extensions: Set[String] = Set("json")

  }

  case object RDFThrift extends RDFContentFormat {
    override def lang: Lang = Lang.RDFTHRIFT

    override def format: RDFFormat = RDFFormat.RDF_THRIFT

    override def extensions: Set[String] = Set("rt", "trdf")
  }

  case object JSONLD extends RDFContentFormat {
    override def lang: Lang = RDFLanguages.JSONLD10

    override def format: RDFFormat = RDFFormat.JSONLD10_COMPACT_PRETTY

    override def extensions: Set[String] = Set("jsonld")
  }

  case object Turtle extends RDFContentFormat {
    override def lang: Lang = Lang.TURTLE

    override def format: RDFFormat = RDFFormat.TURTLE_PRETTY

    override def extensions: Set[String] = Set("ttl")
  }

  case object NTriples extends RDFContentFormat {
    override def lang: Lang = Lang.NTRIPLES

    override def format: RDFFormat = RDFFormat.NTRIPLES

    override def extensions: Set[String] = Set("nt")
  }

  case object NQuads extends RDFContentFormat {
    override def lang: Lang = Lang.NQUADS

    override def format: RDFFormat = RDFFormat.NQUADS

    override def extensions: Set[String] = Set("nq")
  }

  case object Trix extends RDFContentFormat {
    override def lang: Lang = Lang.TRIX

    override def format: RDFFormat = RDFFormat.TRIX

    override def extensions: Set[String] = Set("trix")
  }

  case object RDFXML extends RDFContentFormat {
    override def lang: Lang = Lang.RDFXML

    override def format: RDFFormat = RDFFormat.RDFXML_PRETTY

    override def extensions: Set[String] = Set("rdf", "rdfs", "owl")
  }

  case object Trig extends RDFContentFormat {
    override def lang: Lang = Lang.TRIG

    override def format: RDFFormat = RDFFormat.TRIG_PRETTY

    override def extensions: Set[String] = Set("trig")
  }

  trait GraphBytesExtractor {
    def extractGraphBytes(data: Array[Byte]): Try[Array[Byte]]
  }

  object GraphBytesExtractor {
    implicit def fromRdfContent(ct: RDFContentFormat): GraphBytesExtractor = DefaultGraphBytesExtractor

    implicit def fromContent(ct: ContentFormat): Option[GraphBytesExtractor] = ct match {
      case c: RDFContentFormat => Some(c)
      case JSON => Some(DefaultGraphBytesExtractor)
      case _ => None
    }

  }

  object DefaultGraphBytesExtractor extends GraphBytesExtractor {
    override def extractGraphBytes(data: Array[Byte]): Try[Array[Byte]] = Success(data)
  }

  object RDFGraphSerialiser {
    def init(inputFormat: RDFContentFormat, extractor: GraphBytesExtractor, data: Array[Byte], base: Option[String]) = inputFormat match {
      case JSONLD => new JsonLDSerialiser(extractor, data, base)
      case other => new RDFGraphSerialiser(other, extractor, data, base)
    }

    def serialiseGraph(graph: Graph, outFormat: RDFContentFormat, base: Option[String], editBuilder: Option[RDFWriterBuilder => Unit]): Try[Array[Byte]] = Try {
      val str = new ByteArrayOutputStream()
      val builder = RDFWriter.create()
        .source(graph)
        .base(base.orNull)
        .format(outFormat.format)
      editBuilder.foreach(_(builder))
      builder
        .output(str)
      str.toByteArray
    }

  }

  // NOTE! Not thread safe!
  class RDFGraphSerialiser protected(inputFormat: RDFContentFormat, extractor: GraphBytesExtractor, data: Array[Byte], base: Option[String]) {

    private var parsed: Try[(Model, List[Warning])] = Failure(null)

    def graph: Try[(Model, List[Warning])] = parsed.orElse {
      extractor.extractGraphBytes(data).flatMap { bytes =>
        val re = Try {
          val model = ModelFactory.createDefaultModel()
          val parser = RDFParser.create()
            .source(new ByteArrayInputStream(bytes))
            .base(base.orNull)
            .lang(inputFormat.lang)
          val eh = newErrorHandlerWithWarnings
          parser.errorHandler(eh)
          modifyParser(parser)
          parser.parse(StreamRDFLib.graph(model.getGraph))
          (model, eh.warningsList)
        }
        parsed = re
        re
      }
    }

    def graphBytes(outFormat: RDFContentFormat): Try[Array[Byte]] =
      graph
        .map(_._1)
        .flatMap(m => serialiseGraph(m.getGraph, outFormat, base, Some(modifyWriter)))

    protected def modifyParser(parser: RDFParserBuilder): Try[Unit] = Success()

    protected def modifyWriter(writer: RDFWriterBuilder): Try[Unit] = Success()

  }

  // NOTE! Not thread safe!
  private class JsonLDSerialiser(extractor: GraphBytesExtractor, data: Array[Byte], base: Option[String]) extends RDFGraphSerialiser(JSONLD, extractor, data, base) {

    import JsonLDSerialiser._

    // NOTE! Not thread safe!
    private var remoteContext: Option[Try[(URL, util.Context)]] = None

    override def graph: Try[(Model, List[Warning])] = {
      parseContext(data, base)
      super.graph
    }

    override def modifyParser(parser: RDFParserBuilder) =
      remoteContext.get.map(cs =>
        parser.context(cs._2))

    override def modifyWriter(writer: RDFWriterBuilder) =
      remoteContext.get.map(ctx => {
        writer.context(ctx._2)
        writer.set(JsonLD10Writer.JSONLD_CONTEXT_SUBSTITUTION, new JsonString(ctx._1.toString))
      })


    private def parseContext(body: Array[Byte], base: Option[String]): Option[Try[(URL, util.Context)]] =
      remoteContext match {
        case None | Some(Failure(_)) =>
          val c = jsonLdContextUrl(body)
            .map(c => jenaJsonLdContext(c, base).map((c, _)))
          remoteContext = c
          c
        case other => other
      }

  }

  object JsonLDSerialiser {

    private lazy val CachingContext = new CachingJsonldContext(30, defaultJsonLdOpts(null))

    def preloadContextFromAnotherUri(ctxUri: String, downloadUri: String): Try[core.Context] = Try {
      val ctx = CachingContext.parse(downloadUri)
      CachingContext.putInCache(ctxUri, ctx)
      ctx
    }

    def preloadContextFromAnotherHost(ctxUri: String, downloadHost: String): Try[core.Context] = Try {
      val ctxUrl = new URL(ctxUri)
      ctxUri.replace(ctxUrl.getHost, downloadHost)
    }.flatMap(preloadContextFromAnotherUri(ctxUri, _))

    private def defaultJsonLdOpts(base: String) = {
      val opts = new JsonLdOptions(base)
      opts.useNamespaces = true
      opts
    }

    private def jenaJsonLdContext(jsonLdContextUrl: URL, baseUrl: Option[String]): Try[util.Context] =
      Try(CachingContext.parse(jsonLdContextUrl.toString))
        .map(ctx =>
          baseUrl
            .map(bu => {
              val c = ctx.clone()
              c.put("@base", bu)
              c
            })
            .getOrElse(ctx)
        )
        .map(jenaContext)

    private[databus] def jsonLdContextUrl(data: Array[Byte]): Option[URL] =
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
              }))).toOption.flatten

    private def jenaContext(jsonLdCtx: core.Context) = {
      val context: util.Context = RIOT.getContext.copy()
      jsonLdCtx.putAll(jsonLdCtx.getPrefixes(true))
      context.put(JsonLD10Writer.JSONLD_CONTEXT, jsonLdCtx)
      context.put(LangJSONLD10.JSONLD_CONTEXT, jsonLdCtx)
      context
    }

  }

  def validateWithShacl(model: Model, shacl: Graph): Try[ValidationReport] =
    Try(
      ShaclValidator.get()
        .validate(Shapes.parse(shacl), model.getGraph)
    )

  def validateWithShacl(file: Array[Byte], modelLang: RDFContentFormat, shaclGraph: Graph): Try[ValidationReport] =
    for {
      (model, _) <- RdfConversions.RDFGraphSerialiser.init(modelLang, modelLang, file, None).graph
      re <- validateWithShacl(model, shaclGraph)
    } yield re

  def validateWithShacl(file: Array[Byte], shaclData: Array[Byte], modelLang: RDFContentFormat): Try[ValidationReport] =
    for {
      (shaclGra, _) <- RdfConversions.RDFGraphSerialiser.init(DefaultShaclLang, DefaultShaclLang, shaclData, None).graph
      re <- validateWithShacl(file, modelLang, shaclGra.getGraph)
    } yield re

  def validateWithShacl(file: Array[Byte], shaclUri: String, modelLang: RDFContentFormat): Try[ValidationReport] =
    for {
      shaclGra <- Try(RDFDataMgr.loadGraph(shaclUri))
      re <- validateWithShacl(file, modelLang, shaclGra)
    } yield re


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

    private val reportAsError: Set[String] = List(
      ViolationCodes.ILLEGAL_CHARACTER,
      ViolationCodes.CONTROL_CHARACTER,
      ViolationCodes.NON_XML_CHARACTER,
      ViolationCodes.EMPTY_SCHEME,
      ViolationCodes.SCHEME_MUST_START_WITH_LETTER,
      ViolationCodes.BIDI_FORMATTING_CHARACTER,
      ViolationCodes.WHITESPACE,
      ViolationCodes.DOUBLE_WHITESPACE,
      ViolationCodes.NOT_XML_SCHEMA_WHITESPACE,
      ViolationCodes.NOT_DNS_NAME,
      ViolationCodes.ILLEGAL_PERCENT_ENCODING,
      ViolationCodes.LONE_SURROGATE,
      ViolationCodes.DNS_LABEL_DASH_START_OR_END,
      ViolationCodes.BAD_IDN,
      ViolationCodes.HAS_PASSWORD,
      ViolationCodes.UNREGISTERED_IANA_SCHEME,
      ViolationCodes.UNREGISTERED_NONIETF_SCHEME_TREE,
      ViolationCodes.DEPRECATED_UNICODE_CHARACTER,
      ViolationCodes.UNDEFINED_UNICODE_CHARACTER,
      ViolationCodes.PRIVATE_USE_CHARACTER,
      ViolationCodes.UNICODE_CONTROL_CHARACTER,
      ViolationCodes.UNICODE_WHITESPACE,
      ViolationCodes.COMPATIBILITY_CHARACTER,
      ViolationCodes.REQUIRED_COMPONENT_MISSING,
      ViolationCodes.PROHIBITED_COMPONENT_PRESENT,
      ViolationCodes.SCHEME_REQUIRES_LOWERCASE,
      ViolationCodes.SCHEME_PATTERN_MATCH_FAILED
    ).map(i => s"Code: $i/")
      // there is a weird additional URI check for spaces, so it does not return ViolationCode
      // for error with spaces, we need to tackle this separately.
      // see org.apache.jena.riot.system.ParserProfileStd method internalMakeIRI line 95
      // {@link org.apache.jena.riot.system.ParserProfileStd#internalMakeIRI}
      .:+("Spaces are not legal in URIs/IRIs.").toSet


    override def warning(message: String, line: Long, col: Long): Unit =
    // Fix for https://github.com/dbpedia/databus/issues/156, need to convert this to error
      if (reportAsError.exists(s => message.contains(s))) {
        error(message, line, col)
      } else {
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


