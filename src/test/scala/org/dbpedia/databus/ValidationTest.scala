package org.dbpedia.databus

import java.nio.file.{Files, Paths}

import org.apache.jena.query.ARQ
import org.scalatest.{FlatSpec, Matchers}

import scala.util.{Failure, Success}

class ValidationTest extends FlatSpec with Matchers {

  ARQ.init()

  "SHACL validation" should "work for version" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val re = RdfConversions.validateWithShacl(bytes, shacl)
    re shouldBe a[Success[Unit]]
  }

  "SHACL validation" should "not work for wrong version" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "version_wrong.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val re = RdfConversions.validateWithShacl(bytes, shacl)
    re shouldBe a[Failure[Unit]]
  }

  "SHACL validation" should "work for group" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "group.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val re = RdfConversions.validateWithShacl(bytes, shacl)
    re shouldBe a[Success[Unit]]
  }

  "Version validation" should "work" in {
    val toCheck1 = "https://databus.dbpedia.org/kuckuck/nest/eier/2020.10.10"
    val result1 = RdfConversions.validateVersion(toCheck1, "kuckuck", "nest", "eier", "2020.10.10")
    result1.isSuccess should be (true)
    val result2 = RdfConversions.validateVersion(toCheck1, "kuckuc", "nest", "eier", "2020.10.10")
    result2.isSuccess should be(false)
    val result3 = RdfConversions.validateVersion(toCheck1, "kuckuck", "nestt", "eier", "2020.10.10")
    result3.isSuccess should be(false)
    val result4 = RdfConversions.validateVersion(toCheck1, "kuckuck", "nest", "eierr", "2020.10.10")
    result4.isSuccess should be(false)
    val result5 = RdfConversions.validateVersion(toCheck1, "kuckuck", "nest", "eier", "2020.10.11")
    result5.isSuccess should be(false)
  }

  "Validation" should "work" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val re = RdfConversions.validateWithShacl(bytes, shacl)
      .flatMap(m => RdfConversions.validateVersion(m, "denis", "testgroup", "testartifact", "2021-04-28"))
    re shouldBe a[Success[Unit]]
  }

  "Validation" should "fail" in {
    val shacl = "https://raw.githubusercontent.com/dbpedia/databus-git-mockup/main/dev/dataid-shacl.ttl"
    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val re = RdfConversions.validateWithShacl(bytes, shacl)
      .flatMap(m => RdfConversions.validateVersion(m, "kucuck", "nest", "eier", "2020.10.10"))
    re shouldBe a[Failure[Unit]]
  }

  "Shacl validation" should "work with both files" in {
    val shaclFn = "test.shacl"
    val shacl = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(shaclFn).getFile))
    val file = "version.jsonld"
    val bytes = Files.readAllBytes(Paths.get(getClass.getClassLoader.getResource(file).getFile))
    val re = RdfConversions.validateWithShacl(bytes, shacl)
      .flatMap(m => RdfConversions.validateVersion(m, "kucuck", "nest", "eier", "2020.10.10"))
    re shouldBe a[Failure[Unit]]
  }

}
