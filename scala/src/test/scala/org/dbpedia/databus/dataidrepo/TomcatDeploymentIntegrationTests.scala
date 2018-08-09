package org.dbpedia.databus.dataidrepo

import com.typesafe.scalalogging.LazyLogging
import org.apache.http.client.methods.HttpGet
import org.scalatra.test.scalatest.ScalatraFlatSpec
import resource.managed

import scala.io.Source

class TomcatDeploymentIntegrationTests extends ScalatraFlatSpec with LazyLogging {

  import TomcatDeploymentIntegrationTests._

  "The Servlet, deployed behind Apache HTTPD and proxied via AJP" should
    "give the correct subject name for a deployed test client cert on /client-cert-info" in {

    val client = testhelpers.httpClientWithClientCert("test-cert-bundle.p12")

    val request = new HttpGet(s"$deploymentBaseIRI/client-cert-info")

    for{
      response <- managed(client.execute(request))
      contentStream <- managed(response.getEntity.getContent)
      contentLines = Source.fromInputStream(contentStream).getLines.toList
    } {
      response.getStatusLine.getStatusCode should equal (200)
      logger.info(contentLines mkString "\n")

      contentLines.exists(_ contains "CN=Test Identity") should be (true)
    }
  }
}

object TomcatDeploymentIntegrationTests {

  lazy val deploymentBaseIRI = "https://webid.dbpedia.org/tomcat"

}
