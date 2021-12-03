package org.dbpedia.databus


import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.function.Consumer

import com.mchange.v2.c3p0.ComboPooledDataSource
import org.apache.jena.graph.Node
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.jena.shacl.{ShaclValidator, Shapes}
import org.apache.jena.shacl.validation.ReportEntry
import org.apache.jena.sparql.graph.GraphFactory
import sttp.client3.{DigestAuthenticationBackend, HttpURLConnectionBackend, basicRequest}
import sttp.model.Uri

import scala.util.{Failure, Success, Try}


trait SparqlClient {

  def executeUpdates[T](q1: String, qX: String*)(execInTransaction: => Try[T]): Try[T]

}

class HttpVirtClient(virtUri: Uri, virtUser: String, virtPass: String) extends SparqlClient {

  import HttpVirtClient._

  private lazy val backend = new DigestAuthenticationBackend(HttpURLConnectionBackend())

  def executeUpdates[T](q: String, qX: String*)(trans: => Try[T]): Try[T] = {
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
    re.flatMap(_ => trans)
  }

}

object HttpVirtClient {

  private[databus] def virtuosoRequest(request: String, virtuosoUri: Uri, un: String, pass: String) =
    basicRequest
      .post(virtuosoUri)
      .body("query" -> request)
      .auth.digest(un, pass)

}

class JdbcCLient(host: String, port: Int, user: String, pass: String) extends SparqlClient {

  private lazy val ds = {
    val cpds = new ComboPooledDataSource()
    cpds.setJdbcUrl(s"jdbc:virtuoso://$host:$port/charset=UTF-8");
    cpds.setUser(user)
    cpds.setPassword(pass)
    cpds.setMinPoolSize(5)
    cpds.setAcquireIncrement(5)
    cpds.setMaxPoolSize(20)
    cpds
  }

  def executeUpdates[T](q: String, qX: String*)(trans: => Try[T]): Try[T] = {
    val conn = ds.getConnection
    val upds = Seq(q) ++ qX
    Try {
      val stms = upds.map(s => conn.prepareStatement("sparql\n" + s))
      conn.setAutoCommit(false)
      stms.foreach(s => s.executeUpdate())
    }
      .flatMap(_ => trans)
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
      case _ => "application/ld+json"
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
      case _ => Lang.JSONLD
    }

  import org.apache.jena.graph.Triple

  def generateGraphId(user: String, path: String): String =
    s"/$user/$path"

  def clearGraphSparqlQuery(graphId: String) =
    s"CLEAR SILENT GRAPH <$graphId>"

  def dropGraphSparqlQuery(graphId: String) =
    s"DROP SILENT GRAPH <$graphId>"

  def makeInsertSparqlQuery(triples: Seq[Triple], graphId: String): String = {
    val bld = StringBuilder.newBuilder
    bld.append("INSERT IN GRAPH ")
    wrapWithAngleBracketsQuote(bld, graphId)
    bld.append(" {\n")
    generateQueryTriples(bld, triples)
    bld.append("}").toString()
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

}


