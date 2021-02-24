package org.dbpedia.databus

import org.apache.jena.graph.Graph
import org.apache.jena.graph.Triple

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.util.{Failure, Success, Try}

trait Tractate {

  def stringForSigning: String

}

case class TractateV1(stringForSigning: String) extends Tractate

object TractateV1 {

  val Version = "Databus Tractate V1"
  val SignatureAlgo = "SHA256withRSA"

  private val orderedProperties = Seq(
    "http://purl.org/dc/terms/publisher",
    "http://dataid.dbpedia.org/ns/core#version",
    "http://purl.org/dc/terms/license",
    "http://purl.org/dc/terms/issued",
    "http://dataid.dbpedia.org/ns/core#sha256sum"
  )

  private def extractProp(t: Triple): String = t.getPredicate.getURI

  def fromGraph(graph: Graph): Try[TractateV1] = {
    val iterator = graph.find().asScala
    val re = iterator
      .filter(s => {
        val v = extractProp(s)
        orderedProperties.contains(v)
      })
      .map(tr => {
        val o = tr.getObject
        val v = if (o.isLiteral) {
          o.getLiteralValue.toString
        } else {
          o.getURI
        }
        (extractProp(tr), v)
      })
      .foldLeft(Map[String, Seq[String]]())(
        (m, p) => m + ((p._1, m.getOrElse(p._1, Seq.empty) :+ p._2))
      )

    val ordVals = orderedProperties.map(re(_).sorted)
    if (ordVals.forall(_.nonEmpty)) {
      val out = Seq(Version) ++ ordVals.flatten
      val tractateData = out.foldLeft(StringBuilder.newBuilder)(
        (sb, s) => sb.append(s).append("\n")
      ).toString()

      Success(TractateV1(tractateData))
    } else {
      Failure(new RuntimeException("The graph doesn't contain all the necessary triples"))
    }
  }

  def fromString(s: String) = TractateV1(s)

}

object Tractate {

  def extract(graph: Graph, version: String): Try[Tractate] =
    version match {
      case TractateV1.Version => TractateV1.fromGraph(graph)
    }

  import java.security.PrivateKey

  def sign(tractate: Tractate, key: PrivateKey): String =
    Crypto.sign(tractate.stringForSigning, key, tractateToAlgoVersion(tractate))

  private def tractateToAlgoVersion(tractate: Tractate): String =
    tractate match {
      case _: TractateV1 => TractateV1.SignatureAlgo
      case _ => "SHA256withRSA"
    }

}