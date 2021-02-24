package org.dbpedia.databus

import java.io.ByteArrayInputStream
import java.nio.file.{Files, Paths}

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.scalatest.{FlatSpec, Matchers}

class TractateTest extends FlatSpec with Matchers {

  "Tractate" should "be extracted from dataid" in {

    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val model = ModelFactory.createDefaultModel()
    val dataStream = new ByteArrayInputStream(bytes)
    RDFDataMgr.read(model, dataStream, Lang.JSONLD)
    val t = Tractate.extract(model.getGraph, TractateV1.Version)
    val expected =
      """Databus Tractate V1
        |https://webid.dbpedia.org/webid.ttl#this
        |https://databus.dbpedia.org/kuckuck/nest/eier/2020.10.10
        |http://purl.oclc.org/NET/rdflicense/cc-0
        |2020-12-06T00:00:00Z
        |1be509fb64371dcf5fc7df334964753da4f2d33ba2d86b8e10150dbf64beef27
        |2be509fb64371dcf5fc7df334964753da4f2d33ba2d86b8e10150dbf64beef27
        |""".stripMargin
    t.get.stringForSigning should be(expected)
  }

}
