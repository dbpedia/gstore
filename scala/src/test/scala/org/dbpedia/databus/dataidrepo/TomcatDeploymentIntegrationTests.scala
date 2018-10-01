package org.dbpedia.databus.dataidrepo

import org.dbpedia.databus.shared.DataIdUpload
import org.dbpedia.databus.shared.helpers._

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

    for {
      response <- managed(client.execute(request))
      contentStream <- managed(response.getEntity.getContent)
      contentLines = Source.fromInputStream(contentStream).getLines.toList
    } {
      response.getStatusLine.getStatusCode should equal(200)
      logger.info(contentLines mkString "\n")

      contentLines.exists(_ contains "CN=Test Identity") should be(true)
    }
  }

  it should "show some reaction we test incrementally for /dataid/upload" in {

    val (dataIdResourceName, testCertResourceName) = ("mammals-1.0.0_dataid.ttl", "test-cert-bundle.p12")

    val dataIdTargetLocation = s"http://databus.dbpedia.org/test/$dataIdResourceName"


    val resp = DataIdUpload.upload(s"$deploymentBaseIRI/dataid/upload", resourceAsStream(dataIdResourceName),
      resourceAsStream(testCertResourceName), testCertResourceName, dataIdTargetLocation, true)

    logger.debug("response meta: " + resp.toString)
    logger.debug("response body: " + resp.body)

    resp.code should equal(200)
    resp.body should include("successfully submitted")
  }
}

object TomcatDeploymentIntegrationTests {

  lazy val deploymentBaseIRI = "https://databus.dbpedia.org/repo"
}
