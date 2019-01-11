package org.dbpedia.databus.dataidrepo

import org.dbpedia.databus.shared.rdf.vocab._
import org.dbpedia.databus.shared.helpers.conversions.TapableW

import better.files._
import com.typesafe.scalalogging.LazyLogging
import org.apache.jena.datatypes.xsd.{XSDDatatype, XSDDateTime}
import org.apache.jena.rdf.model.{Literal, ModelFactory}
import org.apache.jena.riot.{RDFDataMgr, RDFFormat}
import org.scalatest.{FlatSpec, Matchers}
import org.scalactic.Snapshots._


import scala.collection.JavaConverters._

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Calendar

class JenaTest extends FlatSpec with Matchers with LazyLogging {

  "Apache Jena" should "make is easy to create datatime literals" in {

    implicit val model = ModelFactory.createDefaultModel()

    model.createResource().addLiteral(dcterms.modified, Calendar.getInstance())

    logger.info(model.listStatements().asScala.mkString("All statements:\n", "\n", ""))

    val datetimes = model.listStatements().asScala.map(_.getObject).collect({

      case datetime: Literal if datetime.getDatatypeURI.toLowerCase.contains("datetime") => datetime
    }).toList

    datetimes should have size (1)

    val calendars = (datetimes.map(_.getValue).collect({ case dt: XSDDateTime => dt.asCalendar }))

    logger.info(calendars.mkString("Calendar objects from statements:\n", "\n", ""))

    calendars.head shouldBe a[Calendar]
  }

  "The scalaz disjunction" should "show some behaviour we try out" in {

    import scalaz._
    import Scalaz._

    val a: String \/ Int = 4.right
    val b: String \/ Int = 3.right

    (a |@| b) apply { case (x, y) =>
      logger.info(s"side-effect: ${snap(x,y)}")
      x + y
    }
  }
}
