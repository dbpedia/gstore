package org.dbpedia.databus

import org.scalatest.{FlatSpec, Matchers}

class JettyHelpersTest extends FlatSpec with Matchers {

  "normalizeSparqlEndpoint" should "preserve Docker Compose hostnames with underscores" in {
    JettyHelpers.normalizeSparqlEndpoint(
      "http://moss-mods-dbpedia-org_virtuoso:8890/sparql"
    ) shouldBe "http://moss-mods-dbpedia-org_virtuoso:8890/sparql"
  }

  it should "append /sparql when path is missing" in {
    JettyHelpers.normalizeSparqlEndpoint(
      "http://virtuoso:8890"
    ) shouldBe "http://virtuoso:8890/sparql"
  }

  it should "strip a trailing slash" in {
    JettyHelpers.normalizeSparqlEndpoint(
      "http://virtuoso:8890/sparql/"
    ) shouldBe "http://virtuoso:8890/sparql"
  }

}
