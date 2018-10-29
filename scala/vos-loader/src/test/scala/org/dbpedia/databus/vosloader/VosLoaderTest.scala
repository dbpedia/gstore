package org.dbpedia.databus.vosloader

import com.typesafe.scalalogging.LazyLogging
import monix.eval._
import monix.execution.Scheduler
import monix.execution.Scheduler.global
import org.apache.jena.query.ReadWrite
import org.apache.jena.graph.{NodeFactory, Triple => JenaTriple}
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.jena.vocabulary.RDF
import org.scalatest.{FlatSpec, Matchers}
import virtuoso.jena.driver.VirtDataset

import scala.concurrent.{Await, Promise}
import scala.util.control.NonFatal
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import java.time.Instant

class VosLoaderTest extends FlatSpec with Matchers with LazyLogging {

  def createVirtDs = new VirtDataset(config.virtuoso.host, config.virtuoso.user, config.virtuoso.password)


  lazy val mammalsModel = {

    val model = ModelFactory.createDefaultModel()
    RDFDataMgr.read(model, TestDocumentSubmitter.mammalsResource.toString, Lang.TURTLE)
    model
  }

  "The VOS Jena Driver" should "allow to save additional triples" in {

    val ds = createVirtDs
    try {
      ds.begin(ReadWrite.WRITE)
      try {

        val dm = ds.getNamedModel("urn:typings")

        dm.add(dm.createResource("urn:mammals"), RDF.`type`,
          dm.createResource("http://dataid.dbpedia.org/ns/core#DataId"))

        ds.addNamedModel("urn:mammals", mammalsModel, false)

        ds.getNamedModel("urn:mammals").add(
          dm.createResource("urn:mammals"), dm.createProperty("urn:writtenAt"), Instant.now().toString
        )

        ds.commit()
      } finally {
        ds.end()
      }
    } finally {
      ds.close()
    }
  }
}
