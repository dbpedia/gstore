package org.dbpedia.databus


import java.nio.file.{Files, Paths}

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

}
