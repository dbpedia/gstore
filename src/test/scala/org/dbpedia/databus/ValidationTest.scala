package org.dbpedia.databus

import java.nio.file.{Files, Paths}
import org.apache.jena.riot.Lang
import org.apache.jena.sys.JenaSystem
import org.dbpedia.databus.RdfConversions.{contextUrl, jenaJsonLdContextWithFallbackForLocalhost}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

class ValidationTest extends FlatSpec with Matchers with BeforeAndAfter {

  before {
    JenaSystem.init()
  }
  after {
    JenaSystem.shutdown()
  }

  val lang = Lang.JSONLD10

  "SHACL validation" should "work for version" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    val ctxU = contextUrl(bytes, lang)
    val ctx = ctxU.map(cu => jenaJsonLdContextWithFallbackForLocalhost(cu, "random").get)

    val re = RdfConversions.validateWithShacl(bytes, ctx, shacl, lang)
    re.get.conforms() should be(true)
  }

  "SHACL validation" should "not work for wrong version" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "version_wrong.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    val ctxU = contextUrl(bytes, lang)
    val ctx = ctxU.map(cu => jenaJsonLdContextWithFallbackForLocalhost(cu, "random").get)

    val re = RdfConversions.validateWithShacl(bytes, ctx, shacl, lang)
    re.get.conforms() should be(false)
  }

  "SHACL validation" should "work for group" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "group.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    val ctxU = contextUrl(bytes, lang)
    val ctx = ctxU.map(cu => jenaJsonLdContextWithFallbackForLocalhost(cu, "random").get)

    val re = RdfConversions.validateWithShacl(bytes, ctx, shacl, lang)
    re.get.conforms() should be(true)
  }

  "Validation" should "work" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    val ctxU = contextUrl(bytes, lang)
    val ctx = ctxU.map(cu => jenaJsonLdContextWithFallbackForLocalhost(cu, "random").get)

    val re = RdfConversions.validateWithShacl(bytes, ctx, shacl, lang)
    re.get.conforms() should be(true)
  }

  "Shacl validation" should "work with both files" in {
    val shaclFn = "test.shacl"
    val shacl = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(shaclFn).getFile))
    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))

    val ctxU = contextUrl(bytes, lang)
    val ctx = ctxU.map(cu => jenaJsonLdContextWithFallbackForLocalhost(cu, "random").get)

    val shaclU = contextUrl(shacl, RdfConversions.DefaultShaclLang)
    val shaclCtx = shaclU.map(cu => jenaJsonLdContextWithFallbackForLocalhost(cu, "random").get)

    val re = RdfConversions.validateWithShacl(bytes, shacl, ctx, shaclCtx, lang)
    re.get.conforms() should be(true)
  }

}
