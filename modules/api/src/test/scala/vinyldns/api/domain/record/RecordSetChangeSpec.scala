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
import vinyldns.api.domain.dns.DnsConversions

class RecordSetChangeSpec extends WordSpec with Matchers with VinylDNSTestData with DnsConversions {

  "RecordSetChange" should {
    "toString" should {
      "output a record set change properly" in {
        val result = pendingCreateAAAA.toString

        result should include("id=\"" + pendingCreateAAAA.id + "\"")
        result should include("userId=\"" + pendingCreateAAAA.userId + "\"")
        result should include("changeType=\"" + pendingCreateAAAA.changeType + "\"")
        result should include("status=\"" + pendingCreateAAAA.status + "\"")
        result should include("systemMessage=\"" + pendingCreateAAAA.systemMessage + "\"")
        result should include("zoneId=\"" + pendingCreateAAAA.zone.id + "\"")
        result should include("zoneName=\"" + pendingCreateAAAA.zone.name + "\"")
        result should include(pendingCreateAAAA.recordSet.toString)
      }
    }
    "forAdd" should {
      "change the record set name if it is @" in {
        val result = RecordSetChangeGenerator.forAdd(atRs, okZone)
        result.recordSet.name shouldBe okZone.name
      }
      "change the record set name if it is @ with AddRecordSet" in {
        val result = RecordSetChangeGenerator.forAdd(atRs, okZone, okAuth)
        result.recordSet.name shouldBe okZone.name
      }
    }
    "forUpdate" should {
      "change the record set name if it is @" in {
        val newRS = atRs.copy(records = List(AData("2.2.2.2")))
        val result = RecordSetChangeGenerator.forUpdate(atRs, newRS, okZone)
        result.recordSet.name shouldBe okZone.name
      }
      "change the record set name if it is @ with AddRecordSet" in {
        val newRS = atRs.copy(records = List(AData("2.2.2.2")))
        val result = RecordSetChangeGenerator.forUpdate(atRs, newRS, okZone, okAuth)
        result.recordSet.name shouldBe okZone.name
      }
      "not copy the account from the record set being replaced when using an UpdateRecordSet" in {
        val replacing = aaaa
        val update = replacing.copy(account = "shouldn't be this account")

        val result = RecordSetChangeGenerator.forUpdate(replacing, update, okZone, okAuth)

        result.recordSet.account shouldBe update.account
      }
      "not copy the account from the record set being replaced when using zone, replacing, and new" in {
        val replacing = aaaa
        val update = replacing.copy(account = "shouldn't be this account")

        val result = RecordSetChangeGenerator.forUpdate(replacing, update, okZone)

        result.recordSet.account shouldBe update.account
      }
    }
    "forDelete" should {
      "change the record set name if it is @" in {
        val result = RecordSetChangeGenerator.forDelete(atRs, okZone)
        result.recordSet.name shouldBe okZone.name
      }
      "change the record set name if it is @ with AddRecordSet" in {
        val result = RecordSetChangeGenerator.forDelete(atRs, okZone, okAuth)
        result.recordSet.name shouldBe okZone.name
      }
    }
  }

  "fail" should {
    "set the system message when provided" in {
      val result = pendingCreateAAAA.failed("my message")
      result.systemMessage shouldBe Some("my message")
    }

    "set the system message to none when not provided" in {
      val result = pendingCreateAAAA.failed()
      result.systemMessage shouldBe None
    }
  }
}
