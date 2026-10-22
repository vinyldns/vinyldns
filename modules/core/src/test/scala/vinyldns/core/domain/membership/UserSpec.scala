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

package vinyldns.core.domain.membership

import java.security.SecureRandom
import java.util.Random

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.domain.Encrypted

class UserSpec extends AnyWordSpec with Matchers {

  "User.generateKey" should {
    "generate 20-character alphanumeric keys" in {
      val keys = List.fill(1000)(User.generateKey)
      keys.foreach { key =>
        key.length shouldBe User.CredentialKeyLength
        key should fullyMatch regex "[A-Za-z0-9]+"
      }
    }

    "generate unique keys" in {
      val keys = List.fill(10000)(User.generateKey)
      keys.distinct.size shouldBe keys.size
    }

    // Regression guard for SRC-021: credentials must come from a cryptographically
    // strong source, never a JVM-default pseudo-random generator.
    "draw from a SecureRandom by default" in {
      User.secureRandom shouldBe a[SecureRandom]
    }

    "derive its output solely from the supplied random source" in {
      // Same seed must reproduce the same key, proving the key is fully determined by the
      // supplied source; this fails if the implementation falls back to a hidden/shared PRNG.
      User.generateKey(new Random(42L)) shouldBe User.generateKey(new Random(42L))
      User.generateKey(new Random(1L)) should not be User.generateKey(new Random(2L))
    }
  }

  "User.regenerateCredentials" should {
    "replace both the access key and the secret key" in {
      val original = User(userName = "test", accessKey = "old-access", secretKey = Encrypted("old-secret"))
      val regenerated = original.regenerateCredentials()

      regenerated.accessKey should not be original.accessKey
      regenerated.secretKey.value should not be original.secretKey.value
      regenerated.accessKey.length shouldBe User.CredentialKeyLength
      regenerated.secretKey.value.length shouldBe User.CredentialKeyLength
    }
  }
}
