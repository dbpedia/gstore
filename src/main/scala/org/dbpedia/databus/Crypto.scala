package org.dbpedia.databus

import java.nio.file.{Files, Paths}
import java.security.{KeyFactory, KeyStore, PrivateKey, PublicKey, Signature}
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object Crypto {

  def bytesToPrivateKey(data: Array[Byte], algo: String): PrivateKey = {
    val spec = new PKCS8EncodedKeySpec(data)
    // RSA
    val kf = KeyFactory.getInstance(algo)
    kf.generatePrivate(spec)
  }

  def bytesToPublicKey(data: Array[Byte], algo: String): PublicKey = {
    val spec = new X509EncodedKeySpec(data)
    // RSA
    val kf = KeyFactory.getInstance(algo)
    kf.generatePublic(spec)
  }

  def sign(plainText: String, privateKey: PrivateKey, algo: String): String = {
    val privateSignature = Signature.getInstance(algo)
    privateSignature.initSign(privateKey)
    privateSignature.update(plainText.getBytes("UTF-8"))
    val signature = privateSignature.sign
    Base64.getEncoder.encodeToString(signature)
  }

  def getKeyPairFromKeystore(fn: String, alias: String, keystorePass: String, keyPass: String): (PublicKey, PrivateKey) = {
    val ins = Files.newInputStream(Paths.get(fn))
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(ins, keystorePass.toCharArray)
    val keyPassword = new KeyStore.PasswordProtection(keyPass.toCharArray)
    val privateKeyEntry =
      keyStore.getEntry(alias, keyPassword)
        .asInstanceOf[KeyStore.PrivateKeyEntry]
    val cert = keyStore.getCertificate(alias)
    val publicKey = cert.getPublicKey
    val privateKey = privateKeyEntry.getPrivateKey
    (publicKey, privateKey)
  }

}
