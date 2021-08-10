package org.dbpedia.databus

import java.io.ByteArrayInputStream
import java.nio.file.{Files, Paths}

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.scalatest.{FlatSpec, Matchers}
import sttp.client3.{DigestAuthenticationBackend, HttpURLConnectionBackend}
import sttp.model.Uri

class VirtuosoQueriesTest extends FlatSpec with Matchers {

  "Generator" should "work" in {
    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val model = ModelFactory.createDefaultModel()
    val dataStream = new ByteArrayInputStream(bytes)
    RDFDataMgr.read(model, dataStream, Lang.JSONLD)

    val bld = RdfConversions.makeInsertSparqlQuery(model.getGraph, "http://randomGraphId")

    println(bld.toString)
    bld.toString()
  }

  "Generator" should "save uri with #this" in {
    val file = "this-test.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val model = ModelFactory.createDefaultModel()
    val dataStream = new ByteArrayInputStream(bytes)
    RDFDataMgr.read(model, dataStream, Lang.JSONLD)

    val bld = RdfConversions.makeInsertSparqlQuery(model.getGraph, "http://randomGraphId")

    println(bld.toString)
    bld.toString()
  }

  ignore should "save to virtuoso" in {
    val backend = new DigestAuthenticationBackend(HttpURLConnectionBackend())
    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    val re = ApiImpl.saveToVirtuoso(backend,
      bytes,
      "/sdc.jsonld",
      "http://fromthecode",
      Uri.parse("https://dbpedia-generic.tib.eu/sparql-auth/").right.get,
      "tester",
      "test")

    re should equal(true)
  }


}
