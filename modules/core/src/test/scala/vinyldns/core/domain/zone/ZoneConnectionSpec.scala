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

package vinyldns.core.domain.zone

import cats.scalatest.EitherMatchers
import vinyldns.core.crypto.CryptoAlgebra
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.domain.Encrypted

class ZoneConnectionSpec extends AnyWordSpec with Matchers with EitherMatchers {

  val testCrypto = new CryptoAlgebra {
    def encrypt(value: String): String = "encrypted!"

    def decrypt(value: String): String = "decrypted!"
  }

  "ZoneConnection" should {
    "encrypt clear connections" in {
      val test = ZoneConnection("vinyldns.", "vinyldns.", Encrypted("nzisn+4G2ldMn0q1CV3vsg=="), "10.1.1.1")

      test.encrypted(testCrypto).key.value shouldBe "encrypted!"
    }

    "decrypt connections" in {
      val test = ZoneConnection("vinyldns.", "vinyldns.", Encrypted("nzisn+4G2ldMn0q1CV3vsg=="), "10.1.1.1")
      val decrypted = test.decrypted(testCrypto)

      decrypted.key.value shouldBe "decrypted!"
    }
  }
}
