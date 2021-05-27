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
        |https://yum-yab.github.io/webid.ttl#this
        |https://databus.dbpedia.org/denis/testgroup/testartifact/2021-04-28
        |http://this.is.a.license.uri.com/test
        |2021-04-28T14:26:20Z
        |1c69ff99c105ab0f3459a4cd928f14284c996702148a2f62637df69f3e1a01ab
        |af7b3594156ae9753eab55fa9dacbd2b352b8e75af2ec5068a09473014859816
        |b71592685053db4171d2eaacb1eb1084d927fd6831f7ee8c9329903f90c72763
        |""".stripMargin
    t.get.stringForSigning should be(expected)

  }

}
