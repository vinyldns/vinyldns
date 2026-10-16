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

import java.security.GeneralSecurityException

import com.typesafe.config._
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
      | type = "vinyldns.core.crypto.JavaCrypto"
      | secret = "8B06A7F3BC8A2497736F1916A123AA40E88217BE9264D8872597EF7A6E5DCE61"
    """.stripMargin

  private val cryptoConf = ConfigFactory.parseString(conf)
  private val javaCrypto = new JavaCrypto(cryptoConf)

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

    "encrypt only the exact UTF-8 bytes of the plaintext" in {
      val plaintext = "exactBytes"
      val ciphertext = javaCrypto.encrypt(plaintext)
      // Ciphertext embeds a 16-byte IV prefix; AES-CBC pads to block boundary.
      // For 10 chars (10 bytes) the padded block is 16 bytes -> 32 bytes total (IV + ciphertext)
      val rawBytes = java.util.Base64.getDecoder.decode(ciphertext.stripPrefix("ENC:"))
      rawBytes.length shouldBe (16 + 16) // 16-byte IV + one 16-byte AES block
    }

    "reject a secret that is not 256 bits (32 bytes)" in {
      val shortKeyConf = ConfigFactory.parseString(
        """
          | type = "vinyldns.core.crypto.JavaCrypto"
          | secret = "DEADBEEF"
        """.stripMargin
      )
      an[IllegalArgumentException] should be thrownBy new JavaCrypto(shortKeyConf)
    }

    "round trip an empty string" in {
      javaCrypto.decrypt(javaCrypto.encrypt("")) shouldBe ""
    }

    "round trip a multibyte UTF-8 string" in {
      val unicode = "日本語テスト — αβγδ — \uD83D\uDD11"
      javaCrypto.decrypt(javaCrypto.encrypt(unicode)) shouldBe unicode
    }

    "round trip a plaintext that is exactly one AES block (16 bytes)" in {
      val exactly16 = "A" * 16
      javaCrypto.decrypt(javaCrypto.encrypt(exactly16)) shouldBe exactly16
    }

    "round trip a plaintext that is exactly two AES blocks (32 bytes)" in {
      val exactly32 = "B" * 32
      javaCrypto.decrypt(javaCrypto.encrypt(exactly32)) shouldBe exactly32
    }

    "always prefix ciphertext with ENC:" in {
      javaCrypto.encrypt("anything") should startWith("ENC:")
    }

    "produce a unique ciphertext on every call due to random IV" in {
      val a = javaCrypto.encrypt(unencryptedString)
      val b = javaCrypto.encrypt(unencryptedString)
      a should not be b
    }

    "throw GeneralSecurityException when decrypting invalid base64 after ENC:" in {
      a[GeneralSecurityException] should be thrownBy javaCrypto.decrypt("ENC:!!!not-base64!!!")
    }

    "throw GeneralSecurityException when the ENC: payload is empty" in {
      // Empty payload means a zero-length IV; IvParameterSpec rejects it.
      a[GeneralSecurityException] should be thrownBy javaCrypto.decrypt("ENC:")
    }

    "throw when decrypting a ciphertext that has invalid PKCS5 padding" in {
      // 32 bytes of 0xFF: 16-byte IV + 16-byte block that will not decode to valid PKCS5 padding.
      val fakeCiphertext =
        "ENC:" + java.util.Base64.getEncoder.encodeToString(Array.fill[Byte](32)(0xff.toByte))
      a[GeneralSecurityException] should be thrownBy javaCrypto.decrypt(fakeCiphertext)
    }

    "throw when decrypting a valid ciphertext with the wrong key" in {
      val enc = javaCrypto.encrypt(unencryptedString)
      val altConf = ConfigFactory.parseString(
        """
          | secret = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        """.stripMargin
      )
      val altCrypto = new JavaCrypto(altConf)
      a[GeneralSecurityException] should be thrownBy altCrypto.decrypt(enc)
    }
  }
}
