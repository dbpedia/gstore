package org.dbpedia.databus.dataidrepo

import com.typesafe.scalalogging.LazyLogging
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.x509.{Extension, GeneralNames}
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.openssl.PEMParser
import org.scalatra.test.scalatest._

import java.io.StringReader

class DataIdRepoTests extends ScalatraFunSuite with LazyLogging {

  addServlet(classOf[DataIdRepo], "/*")

  test("GET / on DataIdRepo should return status 200") {
    get("/") {
      status should equal (200)
    }
  }

  test("PEM parsing") {

    val pemCert =
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

    val certHolder = new PEMParser(new StringReader(pemCert)).readObject().asInstanceOf[X509CertificateHolder]

    val subjAltNameExt = certHolder.getExtension(Extension.subjectAlternativeName)

    val altNames = GeneralNames.fromExtensions(certHolder.getExtensions, Extension.subjectAlternativeName).getNames

    certHolder should not be null

    altNames should have size 1

    val altName = altNames.head

    altName.getTagNo shouldBe 6

    altName.toString.drop(3) shouldBe "https://neradis.github.io/webid.ttl#this"

    DERIA5String.getInstance(altName.getName).getString() shouldBe "https://neradis.github.io/webid.ttl#this"
  }
}
