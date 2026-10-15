/*
 * Copyright 2018 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vinyldns.core.crypto

import java.nio.charset.StandardCharsets
import java.security.{GeneralSecurityException, SecureRandom}
import java.util.Base64

import com.typesafe.config._
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JavaCryptoSpec extends AnyWordSpec with Matchers {

  val unencryptedString =
    s"""Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore
    magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo
    consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.
    Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."""

  private val conf =
    """
      | type = "com.comcast.denis.crypto.JavaCrypto"
      | secret = "8B06A7F3BC8A2497736F1916A123AA40E88217BE9264D8872597EF7A6E5DCE61"
    """.stripMargin

  private val cryptoConf = ConfigFactory.parseString(conf)
  private val javaCrypto = new JavaCrypto(cryptoConf)

  private val otherKeyCrypto = new JavaCrypto(
    ConfigFactory.parseString(
      """secret = "0000000000000000000000000000000000000000000000000000000000000001""""
    )
  )

  private val secretBytes =
    javax.xml.bind.DatatypeConverter.parseHexBinary(cryptoConf.getString("secret"))

  // Produces a value exactly the way earlier releases (AES-CBC, "ENC:" prefix) did
  private def legacyEncrypt(value: String): String = {
    val iv = new Array[Byte](16)
    new SecureRandom().nextBytes(iv)
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretBytes, "AES"), new IvParameterSpec(iv))
    val ct = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8))
    "ENC:" + Base64.getEncoder.encodeToString(iv ++ ct)
  }

  private def payload(value: String, prefix: String): Array[Byte] =
    Base64.getDecoder.decode(value.stripPrefix(prefix))

  private def rebuild(prefix: String, bytes: Array[Byte]): String =
    prefix + Base64.getEncoder.encodeToString(bytes)

  private def failsUniformly(f: => Any): Unit = {
    val ex = the[GeneralSecurityException] thrownBy f
    ex.getClass shouldBe classOf[GeneralSecurityException]
    ex.getMessage shouldBe JavaCrypto.InvalidCiphertextMessage
    ex.getCause shouldBe null
  }

  "JavaCrypto" should {
    "round trip successfully" in {
      val hidden = javaCrypto.encrypt(unencryptedString)
      hidden should not be unencryptedString

      val roundTripped = javaCrypto.decrypt(hidden)
      roundTripped shouldBe unencryptedString

      javaCrypto.decrypt(javaCrypto.encrypt(roundTripped)) shouldBe unencryptedString
    }

    "be thread safe" in {
      (1 to 100).par.foreach { _ =>
        val e = unencryptedString
        val h = javaCrypto.encrypt(e)
        val r = javaCrypto.decrypt(h)

        e shouldBe r
      }
    }

    "not double encrypt" in {
      val base = unencryptedString
      val encryptedOnce = javaCrypto.encrypt(base)
      val encryptedTwice = javaCrypto.encrypt(encryptedOnce)

      encryptedOnce shouldBe encryptedTwice
    }

    "not decrypt text that doesn't have encrypted prefix" in {
      val base = unencryptedString
      val decryptAttempt = javaCrypto.decrypt(base)

      decryptAttempt shouldBe base
    }

    "work successfully for different instances encrypting and decrypting a message" in {
      val hidden = javaCrypto.encrypt(unencryptedString)

      val secondCrypto = new JavaCrypto(cryptoConf)
      secondCrypto.decrypt(hidden) shouldBe unencryptedString
    }

    "encrypt new values with the authenticated v2 format" in {
      javaCrypto.encrypt("secret") should startWith(JavaCrypto.GcmPrefix)
    }

    "use a fresh IV for every encryption" in {
      val values = (1 to 50).map(_ => javaCrypto.encrypt("secret")).toSet
      values.size shouldBe 50
    }

    "round trip empty, unicode and whitespace-padded values exactly" in {
      Seq("", "   ", "  padded key  \n", "ключ-秘密-🔑", "ENC", "ENC;notprefixed").foreach { v =>
        javaCrypto.decrypt(javaCrypto.encrypt(v)) shouldBe v
      }
    }

    "reject any single-bit modification of an authenticated value" in {
      val hidden = javaCrypto.encrypt("tsig-key-value")
      val bytes = payload(hidden, JavaCrypto.GcmPrefix)
      bytes.indices.foreach { i =>
        val tampered = bytes.clone()
        tampered(i) = (tampered(i) ^ 0x01).toByte
        failsUniformly(javaCrypto.decrypt(rebuild(JavaCrypto.GcmPrefix, tampered)))
      }
    }

    "reject truncated or extended authenticated values" in {
      val bytes = payload(javaCrypto.encrypt("tsig-key-value"), JavaCrypto.GcmPrefix)
      failsUniformly(javaCrypto.decrypt(rebuild(JavaCrypto.GcmPrefix, bytes.dropRight(1))))
      failsUniformly(javaCrypto.decrypt(rebuild(JavaCrypto.GcmPrefix, bytes :+ 0.toByte)))
      failsUniformly(javaCrypto.decrypt(rebuild(JavaCrypto.GcmPrefix, bytes.take(12))))
      failsUniformly(javaCrypto.decrypt(JavaCrypto.GcmPrefix))
    }

    "reject authenticated values produced with a different secret" in {
      failsUniformly(javaCrypto.decrypt(otherKeyCrypto.encrypt("secret")))
      failsUniformly(javaCrypto.encrypt(otherKeyCrypto.encrypt("secret")))
    }

    "reject attacker supplied v2 ciphertext on encrypt instead of storing it verbatim" in {
      val forged = rebuild(JavaCrypto.GcmPrefix, Array.fill[Byte](48)(7))
      failsUniformly(javaCrypto.encrypt(forged))
      failsUniformly(javaCrypto.encrypt(JavaCrypto.GcmPrefix + "!!!!"))
    }

    "still decrypt values written by earlier releases" in {
      val legacy = legacyEncrypt(unencryptedString)
      javaCrypto.decrypt(legacy) shouldBe unencryptedString.trim
    }

    "pass well formed legacy values through encrypt unchanged" in {
      val legacy = legacyEncrypt("legacy")
      javaCrypto.encrypt(legacy) shouldBe legacy
    }

    "reject malformed legacy values on encrypt without decrypting them" in {
      failsUniformly(javaCrypto.encrypt("ENC:!!!!"))
      failsUniformly(javaCrypto.encrypt("ENC:////////////////////////"))
      failsUniformly(javaCrypto.encrypt("ENC:"))
      failsUniformly(javaCrypto.encrypt(rebuild("ENC:", new Array[Byte](16))))
    }

    "not distinguish legacy padding failures from other decryption failures" in {
      val bytes = payload(legacyEncrypt("legacy"), "ENC:")
      // Tampering with the last byte of the IV changes the padding byte of a single-block message
      val badPadding = bytes.clone()
      badPadding(15) = (badPadding(15) ^ 0x7f).toByte
      failsUniformly(javaCrypto.decrypt(rebuild("ENC:", badPadding)))
      failsUniformly(javaCrypto.decrypt("ENC:!!!!"))
      failsUniformly(javaCrypto.decrypt(rebuild("ENC:", bytes.take(10))))
      failsUniformly(javaCrypto.decrypt(rebuild("ENC:", bytes.dropRight(1))))
    }
  }
}
