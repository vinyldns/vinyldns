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

import org.scalatest.{Matchers, WordSpec}

class RecordDataSpec extends WordSpec with Matchers {

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
    "Properly convert from int" in {
      DnsSecAlgorithm(3) shouldBe DnsSecAlgorithm.DSA
      DnsSecAlgorithm(5) shouldBe DnsSecAlgorithm.RSASHA1
      DnsSecAlgorithm(6) shouldBe DnsSecAlgorithm.DSA_NSEC3_SHA1
      DnsSecAlgorithm(7) shouldBe DnsSecAlgorithm.RSA_NSEC3_SHA1
      DnsSecAlgorithm(1) shouldBe DnsSecAlgorithm.UnknownAlgorithm(1)
    }
    "Properly convert to int" in {
      DnsSecAlgorithm.DSA.value shouldBe 3
      DnsSecAlgorithm.RSASHA1.value shouldBe 5
      DnsSecAlgorithm.DSA_NSEC3_SHA1.value shouldBe 6
      DnsSecAlgorithm.RSA_NSEC3_SHA1.value shouldBe 7
      DnsSecAlgorithm.UnknownAlgorithm(1).value shouldBe 1
    }
  }
}
