package org.dbpedia.databus


import java.io.ByteArrayInputStream
import java.nio.file.{Files, Paths}

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.dbpedia.databus.ApiImpl.Config
import org.dbpedia.databus.swagger.DatabusSwagger
import org.dbpedia.databus.swagger.api.DefaultApi
import org.scalatra.test.scalatest.ScalatraFlatSpec
import sttp.model.Uri

class DatabusScalatraTest extends ScalatraFlatSpec {

  override def port = 55388

  val config = Config(
    "u",
    "p",
    "http",
    "localhost",
    Some(port),
    Uri.parse(s"http://localhost:${port}/virtu/oso").right.get,
    "u",
    "p")

  implicit val sw = new DatabusSwagger
  implicit val impl = new ApiImpl(config)

  addServlet(new DefaultApi(), "/databus/*")
  addServlet(new ExternalApiEmul, "/*")

  "File save" should "work" in {

    val file = "group.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    post("/databus/file/save?username=kuckuck&path=pa/fl.jsonld", bytes){
      status should equal(200)
    }
  }

  "Shacl" should "validate" in {
    val file = "group.jsonld"
    val sha = "test.shacl"
    val bytes = Paths.get(getClass.getClassLoader.getResource(file).getFile).toFile
    val shacl = Paths.get(getClass.getClassLoader.getResource(sha).getFile).toFile

    val errFl = "version_wrong.jsonld"
    val err = Paths.get(getClass.getClassLoader.getResource(errFl).getFile).toFile

    post("/databus/shacl/validate", Map.empty, Map("shacl" -> shacl, "graph" -> bytes)){
      status should equal(200)
    }

    post("/databus/shacl/validate", Map.empty, Map("shacl" -> shacl, "graph" -> err)){
      //todo change to 400 or 200 but with error
      status should equal(500)
    }
  }

  "Api" should "generate tractate" in {
    val fl = "version_wrong.jsonld"
    val version = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(fl).getFile))

    post("/databus/dataid/tractate", version){
      status should equal(200)
      body should equal("""Databus Tractate V1
                          |https://webid.dbpedia.org/webid.ttl#this
                          |http://databus.dbpedia.org/kuckuck/nest/eier/2020.10.10
                          |http://purl.oclc.org/NET/rdflicense/cc-0
                          |2020-12-06T00:00:00Z
                          |1be509fb64371dcf5fc7df334964753da4f2d33ba2d86b8e10150dbf64beef27
                          |""".stripMargin)

      val model = ModelFactory.createDefaultModel()
      val dataStream = new ByteArrayInputStream(version)
      RDFDataMgr.read(model, dataStream, Lang.JSONLD)
      val tr = Tractate.extract(model.getGraph, TractateV1.Version)
      body should equal(tr.get.stringForSigning)
    }
  }



}
