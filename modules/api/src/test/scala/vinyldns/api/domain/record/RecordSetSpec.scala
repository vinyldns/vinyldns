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

package vinyldns.api.domain.record

import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.VinylDNSTestData
import vinyldns.api.domain.record.RecordSetHelpers._
import vinyldns.core.domain.record._

class RecordSetSpec extends WordSpec with Matchers with VinylDNSTestData {

  "RecordSet" should {
    "toString" should {
      "output a record set properly" in {
        val result = aaaa.toString

        result should include("id=\"" + aaaa.id + "\"")
        result should include("name=\"" + aaaa.name + "\"")
        result should include("type=\"" + aaaa.typ + "\"")
        result should include("ttl=\"" + aaaa.ttl + "\"")
        result should include("account=\"" + aaaa.account + "\"")
        result should include("status=\"" + aaaa.status + "\"")
        result should include("records=\"" + aaaa.records + "\"")
      }
    }

    "matches should match record data when not AAAA" in {
      val records = List(CNAMEData("foo."))
      val left = cname.copy(records = records)
      val right = cname.copy(records = records)
      matches(left, right, okZone.name) shouldBe true
    }

    "matches should match eight hextet ipv6 addresses when equal" in {
      val records = List(AAAAData("caec:cec6:c4ef:bb7b:1a78:d055:216d:3a78"))
      val left = aaaa.copy(records = records)
      val right = aaaa.copy(records = records)

      matches(left, right, okZone.name) shouldBe true
    }

    "matches should not match eight hextet length ipv6 addresses when not equal" in {
      val leftRecords = List(AAAAData("caec:cec6:c4ef:bb7b:1a78:d055:216d:3a78"))
      val rightRecords = List(AAAAData("eeee:1111:aaaa:bb7b:1a78:d055:216d:3a78"))
      val left = aaaa.copy(records = leftRecords)
      val right = aaaa.copy(records = rightRecords)

      matches(left, right, okZone.name) shouldBe false
    }

    "matches should match multiple eight hextet ipv6 addresses when equal" in {
      val records =
        List(AAAAData("caec:cec6:c4ef:bb7b:1a78:d055:216d:3a78"), AAAAData("1:2:3:4:5:6:7:8"))
      val left = aaaa.copy(records = records)
      val right = aaaa.copy(records = records)

      matches(left, right, okZone.name) shouldBe true
    }

    "matches should match abbreviated eight hextet ipv6 addresses when equal" in {
      val leftRecords = List(AAAAData("2001:0db8:3c4d:0015:1:1:1a2f:1a2b"))
      val rightRecords = List(AAAAData("2001:db8:3c4d:15:1:1:1a2f:1a2b"))
      val left = aaaa.copy(records = leftRecords)
      val right = aaaa.copy(records = rightRecords)

      matches(left, right, okZone.name) shouldBe true
    }

    "matches should not match abbreviated eight hextet length ipv6 addresses when not equal" in {
      val leftRecords = List(AAAAData("caec:1:c4ef:bb7b:1a78:d055:216d:3a78"))
      val rightRecords = List(AAAAData("caec:1:aaaa:bb7b:1a78:d055:216d:3a78"))
      val left = aaaa.copy(records = leftRecords)
      val right = aaaa.copy(records = rightRecords)

      matches(left, right, okZone.name) shouldBe false
    }

    "matches should match ipv6 addresses when one uses shorthand" in {
      val recordsLeft = List(AAAAData("2001:0db8:3c4d:0015:0000:0000:1a2f:1a2b"))
      val recordsRight = List(AAAAData("2001:db8:3c4d:15::1a2f:1a2b"))
      val left = aaaa.copy(records = recordsLeft)
      val right = aaaa.copy(records = recordsRight)

      matches(left, right, okZone.name) shouldBe true
    }

    "matches should not match ipv6 addresses when one uses shorthand and not equal" in {
      val recordsLeft = List(AAAAData("aaaa:0db8:3c4d:0015:1000:0000:1a2f:1a2b"))
      val recordsRight = List(AAAAData("2001:db8:3c4d:15::1a2f:1a2b"))
      val left = aaaa.copy(records = recordsLeft)
      val right = aaaa.copy(records = recordsRight)
      matches(left, right, okZone.name) shouldBe false
    }

    "matches should match ipv6 addresses when two use shorthand" in {
      val records = List(AAAAData("2001::1a2b"))
      val left = aaaa.copy(records = records)
      val right = aaaa.copy(records = records)

      matches(left, right, okZone.name) shouldBe true
    }

    "matches should not match ipv6 addresses when two use shorthand and not equal" in {
      val recordsLeft = List(AAAAData("2001::1a2b"))
      val recordsRight = List(AAAAData("eaf1::1a2b"))
      val left = aaaa.copy(records = recordsLeft)
      val right = aaaa.copy(records = recordsRight)

      matches(left, right, okZone.name) shouldBe false
    }

    "matches should be insensitive to string escaping for TXTdata" in {
      val recordsLeft = List(TXTData("\"ri\\ght\""))
      val recordsRight = List(TXTData("\\\"right\\\""))
      val left = txt.copy(records = recordsLeft)
      val right = txt.copy(records = recordsRight)

      matches(left, right, okZone.name) shouldBe true
    }

    "matches should not match if some records are removed and a subset remain" in {
      val recordsLeft = List(AData("10.1.1.1"), AData("20.2.2.2"))
      val recordsRight = List(AData("10.1.1.1"))
      val left = rsOk.copy(records = recordsLeft)
      val right = rsOk.copy(records = recordsRight)

      matches(left, right, okZone.name) shouldBe false
    }

    "matches should not match if the unique sets of records do not match" in {
      val recordsLeft = List(AData("1.1.1.1"), AData("2.2.2.2"))
      val recordsRight = List(AData("1.1.1.1"), AData("1.1.1.1"))
      val left = rsOk.copy(records = recordsLeft)
      val right = rsOk.copy(records = recordsRight)

      matches(left, right, okZone.name) shouldBe false
    }

    "matches should match regardless of order" in {
      val recordsLeft = List(AData("1.1.1.1"), AData("2.2.2.2"))
      val recordsRight = List(AData("2.2.2.2"), AData("1.1.1.1"))
      val left = rsOk.copy(records = recordsLeft)
      val right = rsOk.copy(records = recordsRight)

      matches(left, right, okZone.name) shouldBe true
    }

    "matches should match if the unique sets of records match" in {
      val recordsLeft = List(AData("1.1.1.1"), AData("2.2.2.2"), AData("1.1.1.1"))
      val recordsRight = List(AData("2.2.2.2"), AData("1.1.1.1"))
      val left = rsOk.copy(records = recordsLeft)
      val right = rsOk.copy(records = recordsRight)

      matches(left, right, okZone.name) shouldBe true
    }

    "ensure trailing dot on CNAME record cname" in {
      val result = cname

      result.records shouldBe List(CNAMEData("cname."))
    }

    "ensure trailing dot on MX record exchange" in {
      val result = mx

      result.records shouldBe List(MXData(3, "mx."))
    }

    "ensure trailing dot on PTR record ptrdname" in {
      val result = ptrIp4

      result.records shouldBe List(PTRData("ptr."))
    }

    "ensure trailing dot on SRV record target" in {
      val result = srv

      result.records shouldBe List(SRVData(1, 2, 3, "target."))
    }
  }
}
