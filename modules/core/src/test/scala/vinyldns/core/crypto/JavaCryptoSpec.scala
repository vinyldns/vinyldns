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
      | type = "com.comcast.denis.crypto.JavaCrypto"
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
  }
}
