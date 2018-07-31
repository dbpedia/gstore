package org.dbpedia.databus.dataidrepo.authentication

import org.dbpedia.databus.dataidrepo.authentication._

import com.typesafe.scalalogging.LazyLogging
import org.bouncycastle.cert.X509CertificateHolder
import org.scalatest.{FunSuite, Matchers}

import scala.collection.JavaConverters._
import scala.util.Success

import java.security.Security
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey

class authenticationTest extends FunSuite with Matchers with LazyLogging {

  val pemCertString =
    """
      |-----BEGIN CERTIFICATE-----
      |MIIEKDCCAxCgAwIBAgIJANVoZJRXoO7IMA0GCSqGSIb3DQEBCwUAMIGqMQswCQYD
      |VQQGEwJERTEQMA4GA1UECAwHU2F4b25pYTEQMA4GA1UEBwwHTGVpcHppZzEcMBoG
      |A1UECgwTREJwZWRpYSBBc3NvY2lhdGlvbjEKMAgGA1UECwwBLjEyMDAGCSqGSIb3
      |DQEJARYjYWNrZXJtYW5uQGluZm9ybWF0aWsudW5pLWxlaXB6aWcuZGUxGTAXBgNV
      |BAMMEE1hcmt1cyBBY2tlcm1hbm4wHhcNMTgwNzE5MTQwOTMyWhcNMjgwNzE2MTQw
      |OTMyWjCBqjELMAkGA1UEBhMCREUxEDAOBgNVBAgMB1NheG9uaWExEDAOBgNVBAcM
      |B0xlaXB6aWcxHDAaBgNVBAoME0RCcGVkaWEgQXNzb2NpYXRpb24xCjAIBgNVBAsM
      |AS4xMjAwBgkqhkiG9w0BCQEWI2Fja2VybWFubkBpbmZvcm1hdGlrLnVuaS1sZWlw
      |emlnLmRlMRkwFwYDVQQDDBBNYXJrdXMgQWNrZXJtYW5uMIIBIjANBgkqhkiG9w0B
      |AQEFAAOCAQ8AMIIBCgKCAQEAyhVZyXxJHqZIdEYtVLoVaO4HUN90imoLxSRLbak3
      |yxehWWHkI6PaNKQH8NuwSkEeHHrMGh/v5mneLG7/temO9UmLJFW75OmTidiqCKGU
      |90i21+lSr0Rxbz20wl2xaWqPSg4pcq5UE/pOtcLowL7RU9IC0CyGG+1bY0zSp5st
      |zcaJ10GJ0WenfMK+btm847ONYLs+5sTTgeNfm/ugZXUmMWaXIgbWD5K2BbZDhLjn
      |U3EUVAVlv74q3U42y+mmIW6tziUW/lB0Q2i1mYBK8cCNPi6JaoqIZsNPqU8Gg6oT
      |ds7iF7SMNPEPz0iOYZOvGmD5oYRU5lz931k7PN8WkT8nRQIDAQABo08wTTAJBgNV
      |HRMEAjAAMAsGA1UdDwQEAwIF4DAzBgNVHREELDAqhihodHRwczovL25lcmFkaXMu
      |Z2l0aHViLmlvL3dlYmlkLnR0bCN0aGlzMA0GCSqGSIb3DQEBCwUAA4IBAQAFCc68
      |FsJIBbseyIhcGwSKI3c+I8h0SS5Q+3TbJkKhkKC1LVRGrno6YxWf4N/g1JLE1l5Q
      |4T2BDLITh4bi4+kJXtR/EcAFHiRVlgQvDWHwiF0g5kOQCktSi8uT6ulPIgUVsyPi
      |Q4U6+y2rskrklSsj/iv2Xjsat6qcBNivuXneycHa2ONC75himbjjdkmeHkWBpHPf
      |U5/2XnikltQGRb/HBMkjI/R8zlCRgSgBBdvkzkeFN0OeR262KDphUB+0O1OwBaLZ
      |ixd9s9Kf99ZHc5sXVtFEf30jbuUdlK919zg1vNY7qgV7Jm4z2bSdm5SHlvmYYiYI
      |Ztd7wFOqPRZzIdwg
      |-----END CERTIFICATE-----
    """.stripMargin

  lazy val parsedCertBouncyCastle = bouncyCastle.parseSingleX059Cert(pemCertString)

  lazy val parsedCertJCA = jca.parseSingleX059Cert(pemCertString)

  test("testParseSingleX059Cert [Bouncy Castle]") {

    def cert = parsedCertBouncyCastle

    cert shouldBe a[Success[_]]

    cert.get shouldBe a[X509CertificateHolder]
  }

  test("getAlternativeNameURIs [Bouncy Castle]") {

    val altNameURIs = bouncyCastle.getAlternativeNameURIs(parsedCertBouncyCastle.get)

    altNameURIs shouldEqual List("https://neradis.github.io/webid.ttl#this")
  }

  test("parseSingleX059Cert [JCA]") {

    def cert = parsedCertJCA

    cert shouldBe a[Success[_]]

    cert.get shouldBe a[X509Certificate]
  }

  test("getAlternativeNameURIs [JCA]") {

    val altNameURIs = jca.getAlternativeNameURIs(parsedCertJCA.get)

    altNameURIs shouldEqual List("https://neradis.github.io/webid.ttl#this")
  }

  test("getRSAModulusAndExponent [JCA]") {

    val modExp = parsedCertJCA.flatMap(jca.getRSAModulusAndExponent)

    modExp shouldBe a[Success[_]]

    modExp.get shouldBe a[RSAModulusAndExponent]
  }
}
