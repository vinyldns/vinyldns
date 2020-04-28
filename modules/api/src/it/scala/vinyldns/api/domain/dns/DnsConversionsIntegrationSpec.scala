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

package vinyldns.api.domain.dns

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.xbill.DNS
import vinyldns.api.domain.dns.DnsProtocol.{DnsResponse, NoError}
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.core.domain.zone.{Zone, ZoneConnection, ZoneStatus}
import vinyldns.api.ResultHelpers
import vinyldns.core.TestRecordSetData.{aaaa, ds}
import vinyldns.core.domain.record.{RecordSet, RecordType}

class DnsConversionsIntegrationSpec extends AnyWordSpec with Matchers with ResultHelpers {

  private val zoneName = "example.com."
  private val testZone = Zone(
    zoneName,
    "test@test.com",
    ZoneStatus.Active,
    connection =
      Some(ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "127.0.0.1:19001")),
    transferConnection =
      Some(ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "127.0.0.1:19001"))
  )

  "Interacting with the DNS backend" should {
    "remove the tsig key value during an update" in {
      val testRecord = aaaa.copy(zoneId = testZone.id)
      val conn = DnsConnection(testZone.connection.get)
      val result: DnsResponse =
        rightResultOf(conn.addRecord(RecordSetChangeGenerator.forAdd(testRecord, testZone)).value)

      result shouldBe a[NoError]
      val resultingMessage = result.asInstanceOf[NoError].message
      resultingMessage.getSectionArray(DNS.Section.ADDITIONAL) shouldBe empty

      val resultingMessageString = resultingMessage.toString

      resultingMessageString should not contain "TSIG"

      val queryResult: List[RecordSet] =
        rightResultOf(conn.resolve(testRecord.name, testZone.name, RecordType.AAAA).value)

      val recordOut = queryResult.head
      recordOut.records should contain theSameElementsAs testRecord.records
      recordOut.name shouldBe testRecord.name
      recordOut.ttl shouldBe testRecord.ttl
      recordOut.typ shouldBe testRecord.typ
    }
    "Successfully add and remove DS record type" in {
      val testRecord = ds.copy(zoneId = testZone.id)

      val conn = DnsConnection(testZone.connection.get)
      val result: DnsResponse =
        rightResultOf(conn.addRecord(RecordSetChangeGenerator.forAdd(testRecord, testZone)).value)

      result shouldBe a[NoError]

      val queryResult: List[RecordSet] =
        rightResultOf(conn.resolve(testRecord.name, testZone.name, RecordType.DS).value)

      val recordOut = queryResult.head
      recordOut.records should contain theSameElementsAs testRecord.records
      recordOut.name shouldBe testRecord.name
      recordOut.ttl shouldBe testRecord.ttl
      recordOut.typ shouldBe testRecord.typ

      // deleting the record just added
      val deleteResult: DnsResponse =
        rightResultOf(
          conn.deleteRecord(RecordSetChangeGenerator.forAdd(testRecord, testZone)).value
        )

      deleteResult shouldBe a[NoError]

      val deleteQuery: List[RecordSet] =
        rightResultOf(conn.resolve(testRecord.name, testZone.name, RecordType.DS).value)

      deleteQuery shouldBe List.empty
    }
  }
}
