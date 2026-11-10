package org.dbpedia.databus

import org.apache.jena.sys.JenaSystem
import org.dbpedia.databus.ApiImpl.Config
import org.dbpedia.databus.RdfConversions.{JSONLD, JsonLDSerialiser, RDFGraphSerialiser}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}
import sttp.model.Uri

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

class JsonldGraphContextTest extends FlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  private val localContext = "http://localhost:3000/res/context.jsonld"

  private val contextProp = "defaultJsonldLocalhostContext"
  private val locationProp = "defaultJsonldLocalhostContextLocation"

  private lazy val contextBytes =
    Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource("databus_context.jsonld").toURI))

  private lazy val metadataBytes =
    Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource("metadata_testerman.jsonld").toURI))

  private var contextServer: JsonldContextHttpServer = _
  private var savedProperties: Map[String, Option[String]] = Map.empty

  override def beforeAll(): Unit = JenaSystem.init()

  override def afterAll(): Unit = JenaSystem.shutdown()

  override def afterEach(): Unit = {
    if (contextServer != null) {
      contextServer.stop()
      contextServer = null
    }
    savedProperties.foreach {
      case (key, None) => System.clearProperty(key)
      case (key, Some(value)) => System.setProperty(key, value)
    }
    savedProperties = Map.empty
  }

  private def startContextServer(): JsonldContextHttpServer = {
    contextServer = new JsonldContextHttpServer(contextBytes)
    contextServer
  }

  private def saveAndSetProperty(key: String, value: String): Unit = {
    if (!savedProperties.contains(key)) {
      savedProperties = savedProperties + (key -> Option(System.getProperty(key)))
    }
    System.setProperty(key, value)
  }

  private def configureFromSystemProperties(): Unit = {
    JsonLDSerialiser.configure(
      minimalConfig(
        Option(System.getProperty(contextProp)),
        Option(System.getProperty(locationProp))
      )
    )
  }

  private def minimalConfig(
    defaultJsonldLocalhostContext: Option[String],
    defaultJsonldLocalhostContextLocation: Option[String]
  ): Config =
    Config(
      Uri.parse("http://localhost:8080/sparql").right.get,
      "u",
      "p",
      None,
      "org.dbpedia.databus.HttpVirtClient",
      None,
      "/g",
      None,
      None,
      None,
      None,
      None,
      None,
      false,
      defaultJsonldLocalhostContext,
      defaultJsonldLocalhostContextLocation,
      900000L,
      None
    )

  private def parseMetadata(base: Option[String] = Some("http://localhost:3000/testerman")): Unit = {
    val result = RDFGraphSerialiser.init(JSONLD, JSONLD, metadataBytes, base).graph
    result.isSuccess should be(true)
    result.get._1.size() should be > 0L
  }

  "JsonLDSerialiser" should "fetch context from mock HTTP server via configured location URL" in {
    val server = startContextServer()
    saveAndSetProperty(contextProp, localContext)
    saveAndSetProperty(locationProp, server.contextUrl)
    configureFromSystemProperties()

    noException should be thrownBy parseMetadata()

    server.requests should be(1)
  }

  it should "match the @context URL from metadata_testerman.jsonld" in {
    JsonLDSerialiser.contextUrl(metadataBytes).map(_.toString).get should be(localContext)
  }

  it should "preserve aliases when configure is later called without context properties" in {
    val server = startContextServer()
    saveAndSetProperty(contextProp, localContext)
    saveAndSetProperty(locationProp, server.contextUrl)
    configureFromSystemProperties()
    JsonLDSerialiser.configure(minimalConfig(None, None))

    noException should be thrownBy parseMetadata()

    server.requests should be(1)
  }

  it should "rewrite double-wrapped inline @context and fetch aliased context URL" in {
    val server = startContextServer()
    saveAndSetProperty(contextProp, localContext)
    saveAndSetProperty(locationProp, server.contextUrl)
    configureFromSystemProperties()

    val contextJson = new String(contextBytes, StandardCharsets.UTF_8).trim
    val metadataJson = new String(metadataBytes, StandardCharsets.UTF_8)
      .replace(
        s""""@context": "$localContext"""",
        s""""@context": $contextJson"""
      )
    val doubleWrapped = metadataJson.getBytes(StandardCharsets.UTF_8)

    val result = RDFGraphSerialiser.init(JSONLD, JSONLD, doubleWrapped, Some("http://localhost:3000/testerman")).graph
    result.isSuccess should be(true)
    result.get._1.size() should be > 0L
    server.requests should be(1)
  }

  it should "parse with document/save style prefix base URL" in {
    val server = startContextServer()
    saveAndSetProperty(contextProp, localContext)
    saveAndSetProperty(locationProp, server.contextUrl)
    configureFromSystemProperties()

    noException should be thrownBy parseMetadata(Some("http://localhost:3000/"))

    server.requests should be(1)
  }

}
