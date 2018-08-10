package org.dbpedia.databus.dataidrepo

import org.dbpedia.databus.dataidrepo.helpers.resourceAsStream

import better.files.File
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import org.apache.http.impl.client.HttpClientBuilder
import resource._

import java.io.{FileInputStream, InputStream}
import java.security.KeyStore

/**
  * Created by Markus Ackermann.
  * No rights reserved.
  */
package object testhelpers {

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
}
