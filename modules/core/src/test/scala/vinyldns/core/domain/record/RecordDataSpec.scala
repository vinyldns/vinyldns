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

package vinyldns.core.domain.record

import vinyldns.core.domain.record.DnsSecAlgorithm._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RecordDataSpec extends AnyWordSpec with Matchers {

  "DigestType" should {
    "Properly convert from int" in {
      DigestType(1) shouldBe DigestType.SHA1
      DigestType(2) shouldBe DigestType.SHA256
      DigestType(3) shouldBe DigestType.GOSTR341194
      DigestType(4) shouldBe DigestType.SHA384
      DigestType(10) shouldBe DigestType.UnknownDigestType(10)
    }
    "Properly convert to int" in {
      DigestType.SHA1.value shouldBe 1
      DigestType.SHA256.value shouldBe 2
      DigestType.GOSTR341194.value shouldBe 3
      DigestType.SHA384.value shouldBe 4

      DigestType.UnknownDigestType(10).value shouldBe 10
    }
  }
  "DnsSecAlgorithm" should {
    "Properly convert to/from int" in {
      List(
        DSA,
        RSASHA1,
        DSA_NSEC3_SHA1,
        RSASHA1_NSEC3_SHA1,
        RSASHA256,
        RSASHA512,
        ECC_GOST,
        ECDSAP256SHA256,
        ECDSAP384SHA384,
        ED25519,
        ED25519,
        ED448,
        PRIVATEDNS,
        PRIVATEOID,
        UnknownAlgorithm(100)
      ).foreach { alg =>
        DnsSecAlgorithm(alg.value) shouldBe alg
      }
    }
  }
}
