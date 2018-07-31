package org.dbpedia.databus.dataidrepo

import org.dbpedia.databus.dataidrepo.authentication._

import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.x509.{Extension, GeneralName, GeneralNames}
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.openssl.PEMParser

import scalaz.std.AllInstances._
import scalaz.syntax.equal._

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.util.{Failure, Success, Try}

import java.io.{ByteArrayInputStream, StringReader}
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.cert.{CertificateFactory, X509Certificate}
import java.security.interfaces.RSAPublicKey
import javax.servlet.http.HttpServletRequest


package object authentication {

  case class RSAModulusAndExponent(modulus: BigInteger, exponent: BigInteger)

  object bouncyCastle {

    def parsePemString(certString: String): Try[Seq[AnyRef]] = {

      val parser = new PEMParser(new StringReader(certString))

      val results = Stream.continually(Try(parser.readObject())).takeWhile {
        case Success(null) => false
        case _ => true
      }

      results.find(_.isFailure) match {

        case Some(Failure(parseError)) => {

          def msg = s"Error while parsing an object in PEM:\n$certString"

          Failure(new RuntimeException(msg, parseError))
        }

        case None => Success(results collect { case Success(obj) => obj } toList)
      }
    }

    def filterSingleX509Cert(pemObjects: Seq[AnyRef]) = {

      val x509Certs = pemObjects collect { case cert: X509CertificateHolder => cert }

      x509Certs match {

        case singleCert :: Nil => Success(singleCert)

        case Nil => Failure(new RuntimeException("No X509 certificate found in PEM."))

        case multipleCerts => {

          val msg = s"Multiple X509 certificates found in PEM:\n${multipleCerts} certificates"

          Failure(new RuntimeException(msg))
        }
      }
    }

    def parseSingleX059Cert(certString: String) = {

      parsePemString(certString).flatMap(filterSingleX509Cert)
    }

    def getAlternativeNameURIs(cert: X509CertificateHolder) = {

      def getNameString(gn: GeneralName) = DERIA5String.getInstance(gn.getName).getString()

      val altNames = GeneralNames.fromExtensions(cert.getExtensions, Extension.subjectAlternativeName).getNames

      altNames.filter(_.getTagNo === GeneralName.uniformResourceIdentifier).map { uriName =>

        DERIA5String.getInstance(uriName.getName).getString()
      } toList
    }
  }

  object jca {

    def parseSingleX059Cert(certString: String) = {

      def bytes = certString.getBytes(StandardCharsets.US_ASCII)

      def cf = CertificateFactory.getInstance("X.509");

      Try(cf.generateCertificate(new ByteArrayInputStream(bytes))) flatMap {

        case x509: X509Certificate => Success(x509)

        case otherCert => Failure(new RuntimeException("Unexpected certificate type: " + otherCert.getType))
      }
    }

    def getAlternativeNameURIs(cert: X509Certificate) = {

      val altNames = Option(cert.getSubjectAlternativeNames).map(_.asScala)

      altNames.fold(List.empty[String]) {
        _.filter(_.get(0).asInstanceOf[Integer].toInt === 6).map(_.get(1).asInstanceOf[String]).toList
      }
    }

    def getRSAModulusAndExponent(cert: X509Certificate) = {

      cert.getPublicKey match {

        case rsa: RSAPublicKey => Success(RSAModulusAndExponent(rsa.getModulus, rsa.getPublicExponent))

        case other => Failure(new RuntimeException(s"unexpected, non-RSA public key: ${other.getClass.getSimpleName}"))
      }
    }

    def describeX059Cert(cert: X509Certificate) = {

      def modExpDesc = getRSAModulusAndExponent(cert).fold(_ => "[modulus and exponent could not be extracted]", {

        modExp => List(s"modulus: ${modExp.modulus.toString.take(64)}...",
          s"exponent: ${modExp.exponent}").mkString("\n")
      })

      def altNameDesc = getAlternativeNameURIs(cert) match {

        case Nil => "[no subject alternative names of type URI]"

        case uris => s"subject alternative name URIs (${uris.size}): ${uris mkString(", ")}"
      }

      s"""
         |class: ${cert.getClass.getName}
         |issuer: ${cert.getIssuerDN.getName}
         |subject: ${cert.getSubjectDN.getName}
         |version: ${cert.getVersion}
         |${altNameDesc}
         |${modExpDesc}
         """.stripMargin
    }

    def getSingleCertFromContainer(request: HttpServletRequest) = {
      val certOption = Option(request.getAttribute("javax.servlet.request.X509Certificate")).collect {

        case cert: X509Certificate => Success(cert)

        case array: Array[X509Certificate] => array.toList match {
          case singleCert :: Nil => Success(singleCert)
          case _ => Failure(new RuntimeException(s"Several client certs received:\n" + array.mkString("\n----\n")))
        }

        case other => Failure(new RuntimeException(s"unexpected  class: ${other.getClass.getName}"))
      }


      Try(certOption.get).recoverWith {

        case nse: NoSuchElementException => Failure(new RuntimeException("container provided no client cert"))
      } flatten
    }

    def getSingleCertFromHeader(request: HttpServletRequest) = {

      request.getHeaders(settings.clientCertHeaderName).asScala.toList match {
        case singleCertString :: Nil => Try{

          val fixedCertString = fixPemStringFromApacheHttpd(singleCertString)

          authentication.jca.parseSingleX059Cert(singleCertString)
        }

        case Nil => Failure(new RuntimeException(s"no client cert received"))

        case certs => Failure(new RuntimeException(s"Several client certs received:\n" + certs.mkString("\n----\n")))
      }
    } flatten


  }

  /**
    * Apache HTTPD offers a non-standard format for the PEM serialisation of client certificates when using
    * `+ExportCertData`: line breaks are replaced by single spaces. Since the PEMParser from Bouncy Castle
    * is unable to recognise PEM mangled in such manner, this method re-formats the certificate serialisations
    * from HTTPD back to a more standard-conform format.
    *
    * @param certString a PEM string representation of a client certificate for CGI variables from HTTPD 2.x
    * @return a PEM string representation with line breaks restored
    */
  protected def fixPemStringFromApacheHttpd(certString: String) = {

    def base64Lines = certString.replace(" ", "\n").lines.toSeq.drop(2).dropRight(2)

    s"""
       |-----BEGIN CERTIFICATE-----
       |${base64Lines mkString "\n"}
       |-----END CERTIFICATE-----
       """.stripMargin
  }
}
