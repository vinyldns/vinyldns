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

package vinyldns.api.domain.zone

import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.VinylDNSTestData
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.Zone

class ZoneViewSpec extends WordSpec with Matchers with VinylDNSTestData {

  val testZone = Zone("vinyldns.", "test@test.com")

  val records = List(
    RecordSet(
      zoneId = testZone.id,
      name = "abc",
      typ = RecordType.A,
      ttl = 100,
      status = RecordSetStatus.Active,
      created = DateTime.now,
      records = List(AData("1.1.1.1"))),
    RecordSet(
      zoneId = testZone.id,
      name = "abc",
      typ = RecordType.A,
      ttl = 100,
      status = RecordSetStatus.Active,
      created = DateTime.now,
      records = List(AData("2.2.2.2"))),
    RecordSet(
      zoneId = testZone.id,
      name = "abc",
      typ = RecordType.AAAA,
      ttl = 100,
      status = RecordSetStatus.Active,
      created = DateTime.now,
      records = List(AAAAData("2001:db8:a0b:12f0::1"))
    ),
    RecordSet(
      zoneId = testZone.id,
      name = "def",
      typ = RecordType.A,
      ttl = 100,
      status = RecordSetStatus.Active,
      created = DateTime.now,
      records = List(AData("3.3.3.3"))),
    RecordSet(
      zoneId = testZone.id,
      name = "vinyldns",
      typ = RecordType.A,
      ttl = 100,
      status = RecordSetStatus.Active,
      created = DateTime.now,
      records = List(AData("1.1.1.1"))),
    RecordSet(
      zoneId = testZone.id,
      name = "vinyldns.",
      typ = RecordType.AAAA,
      ttl = 100,
      status = RecordSetStatus.Active,
      created = DateTime.now,
      records = List(AAAAData("2001:db8:a0b:12f0::1"))
    )
  )

  "ZoneView" should {
    "apply" should {
      "group record sets by name and typ" in {
        val view = ZoneView(testZone, records)

        val record1 = view.recordSetsMap.get("abc", RecordType.A)
        record1 match {
          case Some(records) =>
            records.records should contain theSameElementsAs List(
              AData("1.1.1.1"),
              AData("2.2.2.2"))
          case None => fail()
        }

        val record2 = view.recordSetsMap.get("abc", RecordType.AAAA)
        record2 match {
          case Some(records) =>
            records.records should contain theSameElementsAs List(AAAAData("2001:db8:a0b:12f0::1"))
          case None => fail()
        }

        val record3 = view.recordSetsMap.get("def", RecordType.A)
        record3 match {
          case Some(records) =>
            records.records should contain theSameElementsAs List(AData("3.3.3.3"))
          case None => fail()
        }

        val apexNotDotted = view.recordSetsMap.get("vinyldns.", RecordType.A)
        apexNotDotted match {
          case Some(records) => records.name shouldBe "vinyldns"
          case None => fail()
        }

        val apexDotted = view.recordSetsMap.get("vinyldns.", RecordType.AAAA)
        apexDotted match {
          case Some(records) => records.name shouldBe "vinyldns."
          case None => fail()
        }
      }
    }
    "diff" should {
      "compute the correct additions" in {
        val testZone = Zone("vinyldns.", "test@test.com")

        val vinyldnsRecords = List(
          records(0)
        )
        val vinyldnsView = ZoneView(testZone, vinyldnsRecords)

        val dnsRecords = List(records(0), records(2), records(3))
        val dnsView = ZoneView(testZone, dnsRecords)

        val diff = vinyldnsView.diff(dnsView)

        val expectedRecordSetChanges = Seq(
          RecordSetChangeGenerator.forSyncAdd(dnsRecords(1), testZone),
          RecordSetChangeGenerator.forSyncAdd(dnsRecords(2), testZone)
        )
        val anonymizedDiffRecordSetChanges = diff.map(anonymize)
        val anonymizedExpectedRecordSetChanges = expectedRecordSetChanges.map(anonymize)

        anonymizedDiffRecordSetChanges should contain theSameElementsAs anonymizedExpectedRecordSetChanges
      }
      "compute the correct deletions" in {
        val testZone = Zone("vinyldns.", "test@test.com")

        val vinyldnsRecords = List(records(0), records(2), records(3))

        val vinyldnsView = ZoneView(testZone, vinyldnsRecords)

        val dnsRecords = List(records(0))

        val dnsView = ZoneView(testZone, dnsRecords)

        val diff = vinyldnsView.diff(dnsView)

        val expectedRecordSetChanges = Seq(
          RecordSetChangeGenerator.forSyncDelete(vinyldnsRecords(1), testZone),
          RecordSetChangeGenerator.forSyncDelete(vinyldnsRecords(2), testZone)
        )

        val anonymizedDiffRecordSetChanges = diff.map(anonymize)
        val anonymizedExpectedRecordSetChanges = expectedRecordSetChanges.map(anonymize)

        anonymizedDiffRecordSetChanges should contain theSameElementsAs anonymizedExpectedRecordSetChanges
      }
      "compute the correct updates" in {
        val testZone = Zone("vinyldns.", "test@test.com")

        val vinyldnsRecords = List(records(2), records(3))
        val vinyldnsView = ZoneView(testZone, vinyldnsRecords)

        val dnsRecords = List(
          records(2),
          RecordSet(
            zoneId = testZone.id,
            name = "def",
            typ = RecordType.A,
            ttl = 100,
            status = RecordSetStatus.Active,
            created = DateTime.now,
            records = List(AData("4.4.4.4"))) //updated
        )
        val dnsView = ZoneView(testZone, dnsRecords)

        val diff = vinyldnsView.diff(dnsView)

        val expectedRecordSetChanges = Seq(
          RecordSetChangeGenerator.forSyncUpdate(vinyldnsRecords(1), dnsRecords(1), testZone)
        )

        val anonymizedDiffRecordSetChanges = diff.map(anonymize)
        val anonymizedExpectedRecordSetChanges = expectedRecordSetChanges.map(anonymize)

        anonymizedDiffRecordSetChanges should contain theSameElementsAs anonymizedExpectedRecordSetChanges
      }
      "computes no difference" in {
        val testZone = Zone("vinyldns.", "test@test.com")

        val vinyldnsRecords = List(records(0), records(2), records(3))

        val vinyldnsView = ZoneView(testZone, vinyldnsRecords)

        val dnsRecords = List(records(0), records(2), records(3))
        val dnsView = ZoneView(testZone, dnsRecords)

        val diff = vinyldnsView.diff(dnsView)

        val expectedRecordSetChanges = Seq()
        val anonymizedDiffRecordSetChanges = diff.map(anonymize)

        anonymizedDiffRecordSetChanges should contain theSameElementsAs expectedRecordSetChanges
      }
      "use the vinyldns record set's id and account for updates" in {
        val testZone = Zone("vinyldns.", "test@test.com")

        val vinyldnsRecords = List(
          RecordSet(
            zoneId = testZone.id,
            name = "abc",
            typ = RecordType.AAAA,
            ttl = 100,
            status = RecordSetStatus.Active,
            created = DateTime.now,
            records = List(AAAAData("2001:db8:a0b:12f0::1"))
          ),
          RecordSet(
            zoneId = testZone.id,
            name = "def",
            typ = RecordType.A,
            ttl = 100,
            status = RecordSetStatus.Active,
            created = DateTime.now,
            records = List(AData("3.3.3.3")))
        )
        val vinyldnsView = ZoneView(testZone, vinyldnsRecords)

        val dnsRecords = List(
          RecordSet(
            zoneId = testZone.id,
            name = "abc",
            typ = RecordType.AAAA,
            ttl = 100,
            status = RecordSetStatus.Active,
            created = DateTime.now,
            records = List(AAAAData("2001:db8:a0b:12f0::1"))
          ), //same
          RecordSet(
            zoneId = testZone.id,
            name = "def",
            typ = RecordType.A,
            ttl = 100,
            status = RecordSetStatus.Active,
            created = DateTime.now,
            records = List(AData("4.4.4.4"))) //updated
        )
        val dnsView = ZoneView(testZone, dnsRecords)

        val diff = vinyldnsView.diff(dnsView)

        val expected = vinyldnsRecords(1)

        diff.foreach { rsc =>
          val record = rsc.recordSet
          record.id shouldBe expected.id
          record.account shouldBe expected.account
        }
      }
    }
  }
}
