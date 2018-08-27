package org.dbpedia.databus.dataidrepo

import org.dbpedia.databus.shared.authentification.{PKCS12File, RSAKeyPair}
import org.dbpedia.databus.shared.helpers._
import org.dbpedia.databus.shared.signing

import com.typesafe.scalalogging.LazyLogging
import org.apache.http.client.methods.HttpGet
import org.scalatra.test.scalatest.ScalatraFlatSpec
import resource.managed
import scalaj.http.MultiPart

import scala.io.Source

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

  it should "show some reaction we test incrementally for /dataid/upload" in {

    val dataIdTargetLocation = "http://databus.dbpedia.org/test/animals-datatid.ttl"

    val dataIdSize = resourceAsStream("infobox-properties-1.0.0-dataid.ttl") acquireAndGet { is =>

      Stream.continually(is.read()).takeWhile(_ != -1).size
    }

    (resourceAsStream("infobox-properties-1.0.0-dataid.ttl")
      and resourceAsStream("infobox-properties-1.0.0-dataid.ttl")) apply { case (dataIdForSend, dataIdForSign) =>

      val sslContext = testhelpers.pkcsClientCertSslContext("test-cert-bundle.p12")

      val pkcs12 = resoucreAsFile("test-cert-bundle.p12") apply (PKCS12File(_))

      val RSAKeyPair(publicKey, privateKey) = pkcs12.rsaKeyPairs.head

      def dataIdPart = MultiPart("dataid", "dataid.ttl", "text/turtle", dataIdForSend, dataIdSize,
        bytesWritten => logger.debug(s"$bytesWritten bytes written"))

      def signaturePart = MultiPart("dataid-signature", "dataid.ttl.sig", "application/pkcs7-signature",
        signing.signInputStream(privateKey, dataIdForSign))

      val params = Map(
        "DataIdLocation" -> dataIdTargetLocation,
        "AllowOverwrite" -> true.toString
      )

      def encodedParamsQueryString = {

        def encode: String => String = URLEncoder.encode(_, StandardCharsets.UTF_8.name())

        params.map({case (k,v) => s"$k=${encode(v)}"}).mkString("&")
      }

      def paramsPart = MultiPart("params", "dataid.ttl.loc", "application/x-www-form-urlencoded",
        encodedParamsQueryString)

      val sslHttp = testhelpers.scalajHttpWithClientCert("test-cert-bundle.p12")

      val req = sslHttp(s"$deploymentBaseIRI/dataid/upload")
        .postMulti(dataIdPart, signaturePart, paramsPart)

      val serviceResp = req.asString

      logger.debug("response meta: " + serviceResp.toString)
      logger.debug("response body: " + serviceResp.body)

      serviceResp.code should equal (200)
      serviceResp.body should include ("so good")
    }
  }
}

object TomcatDeploymentIntegrationTests {

  lazy val deploymentBaseIRI = "https://databus.dbpedia.org/repo"

}
