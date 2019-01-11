package org.dbpedia.databus.dataidrepo

import org.dbpedia.databus.shared.helpers.resourceAsStream

import com.typesafe.scalalogging.LazyLogging
import javax.net.ssl._
import org.apache.http.impl.client.HttpClientBuilder
import scalaj.http.{BaseHttp, HttpConstants, HttpOptions}

import java.io.InputStream
import java.security.KeyStore

/**
  * Created by Markus Ackermann.
  * No rights reserved.
  */
package object testhelpers extends LazyLogging {

  def defaultX509TrustManager = {

    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

    tmf.init(null.asInstanceOf[KeyStore])
    tmf.getTrustManagers.collect({ case x509: X509TrustManager => x509 }).head
  }

  def pkcsClientCertSslContext(pkcs12BundleInput: InputStream) = {

    val password = ""

    val ks = KeyStore.getInstance("PKCS12")
    ks.load(pkcs12BundleInput, password.toCharArray)

    val kmf = KeyManagerFactory.getInstance("SunX509")
    kmf.init(ks, password.toCharArray)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(kmf.getKeyManagers, null, null)
    sslContext
  }

  def pkcsClientCertSslContext(pkcs12BundleResourceName: String): SSLContext = {

    resourceAsStream(pkcs12BundleResourceName) apply { bundleStream =>

      pkcsClientCertSslContext(bundleStream)
    }
  }

  def httpClientWithClientCert(pkcs12BundleResourceName: String) = {

    val sslContext = pkcsClientCertSslContext(pkcs12BundleResourceName)

    val builder = HttpClientBuilder.create()
    builder.disableRedirectHandling()
    builder.setSSLContext(sslContext)

    builder.build()
  }

  def scalajHttpWithClientCert(pkcs12BundleResourceName: String) = {

    val sslContext = pkcsClientCertSslContext(pkcs12BundleResourceName)

    val httpOptions = Seq(
      HttpOptions.connTimeout(1000),
      HttpOptions.readTimeout(30000),
      HttpOptions.followRedirects(false),
      HttpOptions.sslSocketFactory(sslContext.getSocketFactory),
    )

    new BaseHttp(options = httpOptions)
  }
}
