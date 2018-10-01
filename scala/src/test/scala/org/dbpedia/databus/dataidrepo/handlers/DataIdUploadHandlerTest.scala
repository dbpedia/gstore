package org.dbpedia.databus.dataidrepo.handlers

import org.dbpedia.databus.shared.DataIdUpload.UploadParams
import org.dbpedia.databus.shared.authentification.{PKCS12File, RSAKeyPair}
import org.dbpedia.databus.shared.helpers.{resoucreAsFile, resourceAsStream}
import org.dbpedia.databus.shared.signing

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}

import java.security.cert.X509Certificate

class DataIdUploadHandlerTest extends FlatSpec with Matchers with MockFactory with LazyLogging {

  "The DataIdUploadHandler" should "avoid deadlock situations with TDB transactions" in {

    val dataIdResourceName = "mammals-1.0.0_dataid.ttl"

    val dataIdTargetLocation = s"http://databus.dbpedia.org/test/$dataIdResourceName"

    val pkcs12 = resoucreAsFile("test-cert-bundle.p12") apply (PKCS12File(_))

    val RSAKeyPair(publicKey, privateKey) = pkcs12.rsaKeyPairs.head

    val signature = resourceAsStream(dataIdResourceName) apply { dataIdStream =>
      signing.signInputStream(privateKey, dataIdStream)
    }

    val uploadParams = Map(
      UploadParams.dataIdIdentifier -> List("mammals"),
      UploadParams.dataIdVersion -> List("1.0"),
      UploadParams.dataIdLocation -> List(dataIdTargetLocation)
    )

    abstract class X509Mock extends X509Certificate {

      override def toString: String = "X509 Mock"
    }

    val clientCertMock = mock[X509Mock]

    (clientCertMock.getPublicKey _).expects().returning(publicKey)

    val handler = new DataIdUploadHandler(clientCertMock, resourceAsStream(dataIdResourceName), signature, uploadParams)

    val response = handler.response

    logger.info(s"${response.status}:\n${response.body}")

    response.status should equal (200)
  }
}
