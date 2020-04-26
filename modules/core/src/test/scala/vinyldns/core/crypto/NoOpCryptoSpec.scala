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

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NoOpCryptoSpec extends AnyWordSpec with Matchers {

  private val conf =
    """
      | type = "vinyldns.core.crypto.NoOpCrypto"
    """.stripMargin

  private val cryptoConf = ConfigFactory.parseString(conf)
  private val underTest = new NoOpCrypto(cryptoConf)

  "NoOpCrypto" should {
    "not encrypt" in {
      underTest.encrypt("foo") shouldBe "foo"
    }
    "not decrypt" in {
      val e = underTest.encrypt("foo")
      underTest.decrypt(e) shouldBe e
    }
  }
}
