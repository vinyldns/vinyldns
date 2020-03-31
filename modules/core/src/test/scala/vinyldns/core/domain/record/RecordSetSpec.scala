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
import vinyldns.core.TestRecordSetData._
import vinyldns.core.domain.Fqdn

class RecordSetSpec extends WordSpec with Matchers {

  "RecordSetHelpers" should {
    "toString" should {
      "output a record set properly" in {
        val result = aaaa.toString

        result should include("zoneId=\"" + aaaa.zoneId + "\"")
        result should include("name=\"" + aaaa.name + "\"")
        result should include("type=\"" + aaaa.typ + "\"")
        result should include("ttl=\"" + aaaa.ttl + "\"")
        result should include("status=\"" + aaaa.status + "\"")
        result should include("created=\"" + aaaa.created + "\"")
        result should include("updated=\"" + aaaa.updated + "\"")
        result should include("records=\"" + aaaa.records + "\"")
        result should include("id=\"" + aaaa.id + "\"")
        result should include("account=\"" + aaaa.account + "\"")
        result should include("ownerGroupId=\"" + aaaa.ownerGroupId + "\"")
      }
    }

    "ensure trailing dot on CNAME record cname" in {
      val result = cname

      result.records shouldBe List(CNAMEData(Fqdn("cname.")))
    }

    "ensure trailing dot on MX record exchange" in {
      val result = mx

      result.records shouldBe List(MXData(3, Fqdn("mx.")))
    }

    "ensure trailing dot on PTR record ptrdname" in {
      val result = ptrIp4

      result.records shouldBe List(PTRData(Fqdn("ptr.")))
    }

    "ensure trailing dot on SRV record target" in {
      val result = srv

      result.records shouldBe List(SRVData(1, 2, 3, Fqdn("target.")))
    }

    "ensure trailing dot on NAPTR record target" in {
      val result = naptr

      result.records shouldBe List(NAPTRData(1, 2, "S", "E2U+sip", "", Fqdn("target.")))
    }
  }
}
