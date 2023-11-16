package org.dbpedia.databus

import java.nio.file.{Files, Paths}
import org.apache.jena.query.ARQ
import org.apache.jena.riot.Lang
import org.dbpedia.databus.RdfConversions.{contextUrl, jenaJsonLdContextWithFallbackForLocalhost}
import org.scalatest.{FlatSpec, Matchers}

class ValidationTest extends FlatSpec with Matchers {

  ARQ.init()
  val lang = Lang.JSONLD

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
