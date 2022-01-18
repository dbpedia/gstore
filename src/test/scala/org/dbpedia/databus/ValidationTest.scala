package org.dbpedia.databus

import java.nio.file.{Files, Paths}

import org.apache.jena.query.ARQ
import org.apache.jena.rdf.model.Model
import org.apache.jena.riot.Lang
import org.scalatest.{FlatSpec, Matchers}

import scala.util.{Failure, Success}

class ValidationTest extends FlatSpec with Matchers {

  ARQ.init()
  val lang = Lang.JSONLD

  "SHACL validation" should "work for version" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val re = RdfConversions.validateWithShacl(bytes, shacl, lang)
    re.get.conforms() should be(true)
  }

  "SHACL validation" should "not work for wrong version" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "version_wrong.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val re = RdfConversions.validateWithShacl(bytes, shacl, lang)
    re.get.conforms() should be(false)
  }

  "SHACL validation" should "work for group" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "group.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val re = RdfConversions.validateWithShacl(bytes, shacl, lang)
    re.get.conforms() should be(true)
  }

  "Validation" should "work" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val re = RdfConversions.validateWithShacl(bytes, shacl, lang)
    re.get.conforms() should be(true)
  }

  "Shacl validation" should "work with both files" in {
    val shaclFn = "test.shacl"
    val shacl = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(shaclFn).getFile))
    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val re = RdfConversions.validateWithShacl(bytes, shacl, lang)
    re.get.conforms() should be(true)
  }

}
