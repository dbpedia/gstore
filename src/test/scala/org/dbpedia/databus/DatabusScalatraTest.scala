package org.dbpedia.databus

import java.io.ByteArrayInputStream
import java.nio.file.{Files, Paths}
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.dbpedia.databus.ApiImpl.Config
import org.dbpedia.databus.RdfConversions.{ErrorHandlerWithWarnings, JsonLDSerialiser}
import org.dbpedia.databus.swagger.DatabusSwagger
import org.dbpedia.databus.swagger.api.DefaultApi
import org.scalatest.BeforeAndAfter
import org.scalatra.test.scalatest.ScalatraFlatSpec
import sttp.model.Uri

import scala.reflect.io.{Directory, Path}

class DatabusScalatraTest extends ScalatraFlatSpec with BeforeAndAfter {

  override def port = 55388

  val dir = Files.createDirectories(Paths.get("target", "test_dir-git"))

  before {
    impl.init()
    Files.createDirectories(Paths.get("target", "test_dir-git"))
  }
  after {
    impl.stop()
    Directory(Path.jfile2path(dir.toFile)).deleteRecursively()
  }

  val config = Config(
    Uri.parse(s"http://localhost:${port}/virtu/oso").right.get,
    "u",
    "p",
    Some(1111),
    "org.dbpedia.databus.HttpVirtClient",
    Some("sdcsdc"),
    "/g",
    Some(dir.toAbsolutePath),
    Some("u"),
    Some("p"),
    Some("http"),
    Some("localhost"),
    Some(port),
    false,
    Some(""),
    Some("")
  )

  implicit val sw = new DatabusSwagger
  implicit val impl = new ApiImpl(config)

  addServlet(new DefaultApi(), "/databus/*")
  addServlet(new ExternalApiEmul, "/*")
  servletContextHandler.setMaxFormContentSize(5870342)


  "File save" should "work" in {

    val file = "group.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    post("/databus/document/save?repo=kuckuck&path=pa/fl.jsonld&author_name=BlaBla&author_email=wrong", bytes) {
      status should equal(400)
    }

    post("/databus/document/save?repo=kuckuck&path=pa/fl.jsonld", bytes) {
      status should equal(200)
    }

    post("/databus/document/save?repo=kuckuck&path=pa/fl.jsonld&author_name=BlaBla", bytes) {
      status should equal(200)
    }

    post("/databus/document/save?repo=kuckuck&path=pa/fl.jsonld&author_name=BlaBla&author_email=bla@bla.com", bytes) {
      status should equal(200)
    }

    post("/databus/document/save?repo=kuckuck&path=pa/fl.jsonld&&author_email=bla@bla.com", bytes) {
      status should equal(200)
    }

    (ErrorHandlerWithWarnings.WarningsInterceptor.JenaErrorHandlers.size() < 3) should equal(true)

    get("/databus/document/history?repo=kuckuck&limit=2") {
      status should equal(200)
      body should include("author_name")
      body should include("bla@bla.com")
      body should include("BlaBla")
      println(body)
    }

    get("/databus/graph/read?repo=kuckuck&path=pa/fl.jsonld") {
      status should equal(200)
      body.contains("This a short abstract for the dataset. Since this") should be(true)
    }

    get("/databus/document/read?repo=kuckuck&path=pa/fl.jsonld") {
      status should equal(200)
      bodyBytes should equal(bytes)
    }

  }

  "Preloading context for localhost" should "work" in {

    val file = "group_with_localhostcontext.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val localhostContext = JsonLDSerialiser.contextUrl(bytes).get.toString
    val vfile = "group.jsonld"
    val vbytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(vfile).getFile))
    val validContext = JsonLDSerialiser.contextUrl(vbytes).get.toString

    RdfConversions.JsonLDSerialiser.preloadContextFromAnotherUri(localhostContext, validContext)

    post(s"/databus/document/save?repo=kuckuck&path=pa/$file", bytes) {
      status should equal(200)
    }

    get(s"/databus/graph/read?repo=kuckuck&path=pa/$file") {
      status should equal(200)
      body.contains("This a short abstract for the dataset. Since this") should be(true)
    }

  }

  "File save" should "save and return original jsonld with json fields" in {

    val file = "group_with_json.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    post(s"/databus/document/save?repo=kuckuck&path=pa/$file", bytes) {
      status should equal(200)
    }
    get(s"/databus/document/read?repo=kuckuck&path=pa/$file") {
      status should equal(200)
      bodyBytes should equal(bytes)
    }
    get(s"/databus/graph/read?repo=kuckuck&path=pa/$file") {
      status should equal(200)
      bodyBytes should not equal (bytes)
    }

  }

  "File save" should "work with a file with expanded context" in {

    val file = "output.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    val mod = ModelFactory.createDefaultModel();
    RDFDataMgr.read(mod, new ByteArrayInputStream(bytes), Lang.JSONLD)

    post("/databus/document/save?repo=kuckuck&path=pa/rel_test.jsonld", bytes) {
      status should equal(200)
    }

    get("/databus/document/read?repo=kuckuck&path=pa/rel_test.jsonld") {
      status should equal(200)
      body should equal(new String(bytes))
    }

  }

  "File save" should "save and retrieve jsonlds with relative uris" in {

    val file = "test-relative.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    post("/databus/document/save?repo=kuckuck&path=pa/rel_test.jsonld", bytes) {
      status should equal(200)
    }

    get("/databus/graph/read?repo=kuckuck&path=pa/rel_test.jsonld") {
      status should equal(200)
      body.contains("#generated\"") should equal(true)
    }

  }

  "File save" should "report problems in input" in {

    val file = "space_in_iri.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    post("/databus/document/save?repo=kuckuck&path=pa/syntax_err.jsonld", bytes) {
      status should equal(400)
      body should include("Wrong IRI")
      body should include("https://metadata.coypu.org/dataset/wikidata-distributionWikidata Query Service")
    }

    (ErrorHandlerWithWarnings.WarningsInterceptor.JenaErrorHandlers.size() < 3) should equal(true)

  }

  "File save" should "work with @nest keyword" in {

    val file = "nest.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    post("/databus/document/save?repo=kuckuck&path=pa/nest.jsonld", bytes) {
      status should equal(200)
    }

    get("/databus/graph/read?repo=kuckuck&path=pa/nest.jsonld") {
      status should equal(200)
      body.contains("\"dct:description\": \"Example table used to illustrate") should equal(true)
      body.contains("\"dct:spatial\": {\n                \"@id\": \"http://sws.geonames.org/6252001/\"") should equal(true)
    }

  }

  "Shacl validation" should "report problems in input with spaces in IRIs" in {

    val file = "space_in_iri.jsonld"
    val sha = "test.shacl"
    val bytes = Paths.get(getClass.getClassLoader.getResource(file).getFile).toFile
    val shacl = Paths.get(getClass.getClassLoader.getResource(sha).getFile).toFile

    post("/databus/shacl/validate", Map.empty, Map("shacl" -> shacl, "graph" -> bytes)) {
      status should equal(400)
      body should include("Wrong IRI")
      body should include("https://metadata.coypu.org/dataset/wikidata-distributionWikidata Query Service")
    }

  }

  "File delete" should "work" in {

    val file = "group.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    post(s"/databus/document/save?repo=kuckuck&path=pa/for_delete/$file", bytes) {
      status should equal(200)
    }

    get(s"/databus/document/read?repo=kuckuck&path=pa/for_delete/$file") {
      bodyBytes should equal(bytes)
      status should equal(200)
    }

    delete(s"/databus/document/delete?repo=kuckuck&path=pa/for_delete/$file") {
      status should equal(200)
    }

    get(s"/databus/document/read?repo=kuckuck&path=pa/for_delete/$file") {
      status should equal(404)
    }

    delete(s"/databus/document/delete?repo=kuckuck&path=pa/for_delete/$file") {
      status should equal(200)
    }

  }

  "Shacl validation" should "report problems in input with newlines in IRIs" in {

    val file = "newline_in_iri.jsonld"
    val sha = "test.shacl"
    val bytes = Paths.get(getClass.getClassLoader.getResource(file).getFile).toFile
    val shacl = Paths.get(getClass.getClassLoader.getResource(sha).getFile).toFile

    post("/databus/shacl/validate", Map.empty, Map("shacl" -> shacl, "graph" -> bytes)) {
      (status == 400) should equal(true)
      body should include("Wrong IRI")
      body should include("hTtps://metadata.coypu.org/dataset/wikidata-distribution\\nWikidataQueryService\\n")
    }

  }

  "File read" should "return 404" in {

    get("/databus/graph/read?repo=kuckuck&path=pa/not_existing.jsonld") {
      status should equal(404)
    }
  }

  "Shacl" should "validate" in {
    val file = "group.jsonld"
    val sha = "test.shacl"
    val bytes = Paths.get(getClass.getClassLoader.getResource(file).getFile).toFile
    val shacl = Paths.get(getClass.getClassLoader.getResource(sha).getFile).toFile

    val errFl = "version_wrong.jsonld"
    val err = Paths.get(getClass.getClassLoader.getResource(errFl).getFile).toFile

    post("/databus/shacl/validate", Map.empty, Map("shacl" -> shacl, "graph" -> bytes)) {
      status should equal(200)
      body should include("\"sh:conforms\": {\n        \"@value\": \"true\",")
    }

    post("/databus/shacl/validate", Map.empty, Map("shacl" -> shacl, "graph" -> err)) {
      status should equal(200)
      body should include("\"sh:conforms\": {\n                \"@value\": \"false\",")
    }
  }

  "Api" should "generate tractate" in {
    val fl = "version_wrong.jsonld"
    val version = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(fl).getFile))

    post("/databus/dataid/tractate", version) {
      status should equal(200)
      body should equal(
        """Databus Tractate V1
          |https://webid.dbpedia.org/webid.ttl#this
          |http://databus.dbpedia.org/kuckuck/nest/eier/2020.10.10
          |http://purl.oclc.org/NET/rdflicense/cc-0
          |2020-12-06T00:00:00Z
          |1be509fb64371dcf5fc7df334964753da4f2d33ba2d86b8e10150dbf64beef27
          |""".stripMargin)

      val model = ModelFactory.createDefaultModel()
      val dataStream = new ByteArrayInputStream(version)
      RDFDataMgr.read(model, dataStream, Lang.JSONLD11)
      val tr = Tractate.extract(model.getGraph, TractateV1.Version)
      body should equal(tr.get.stringForSigning)
    }
  }

  "Number of handlers" should "be close to 0" in {
    (ErrorHandlerWithWarnings.WarningsInterceptor.JenaErrorHandlers.size() < 3) should equal(true)
  }

}
