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

package vinyldns.api.route

import cats.scalatest.ValidatedValues
import java.time.{LocalDateTime, Month, ZoneOffset}
import org.json4s.JsonDSL._
import org.json4s._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.VinylDNSTestHelpers
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{CreateZoneInput, UpdateZoneInput, ZoneConnection}
import vinyldns.core.TestRecordSetData._
import vinyldns.core.domain.Fqdn
import vinyldns.core.Messages._

class VinylDNSJsonProtocolSpec
    extends AnyWordSpec
    with Matchers
    with VinylDNSJsonProtocol
    with ValidatedValues
    with VinylDNSTestHelpers {

  private val completeCreateZoneInput = CreateZoneInput(
    "testZone.",
    "test@test.com",
    connection = Some(
      ZoneConnection(
        "primaryConnection",
        "primaryConnectionKeyName",
        "primaryConnectionKey",
        "10.1.1.1"
      )
    ),
    transferConnection = Some(
      ZoneConnection(
        "transferConnection",
        "transferConnectionKeyName",
        "transferConnectionKey",
        "10.1.1.2"
      )
    ),
    adminGroupId = "admin-group-id"
  )

  private val completeUpdateZoneInput = UpdateZoneInput(
    "updated-zone-id",
    "updated-zone-name.",
    "updated@email.com",
    connection = Some(
      ZoneConnection(
        "primaryConnection",
        "primaryConnectionKeyName",
        "primaryConnectionKey",
        "10.1.1.1"
      )
    ),
    transferConnection = Some(
      ZoneConnection(
        "transferConnection",
        "transferConnectionKeyName",
        "transferConnectionKey",
        "10.1.1.2"
      )
    ),
    adminGroupId = "updated-admin-group-id"
  )

  private val primaryConnection: JValue =
    ("name" -> "primaryConnection") ~~
      ("keyName" -> "primaryConnectionKeyName") ~~
      ("key" -> "primaryConnectionKey") ~~
      ("primaryServer" -> "10.1.1.1")

  private val transferConnection: JValue =
    ("name" -> "transferConnection") ~~
      ("keyName" -> "transferConnectionKeyName") ~~
      ("key" -> "transferConnectionKey") ~~
      ("primaryServer" -> "10.1.1.2")

  "CreateZoneInputSerializer" should {
    "parse a create zone input with no connections" in {
      val createZoneInput: JValue =
        ("name" -> "testZone.") ~~
          ("email" -> "test@test.com") ~~
          ("adminGroupId" -> "admin-group-id")

      val expected = completeCreateZoneInput.copy(connection = None, transferConnection = None)
      val actual = createZoneInput.extract[CreateZoneInput]
      actual shouldBe expected
    }

    "parse a create zone input with a connection and no transfer connection" in {
      val createZoneInput: JValue =
        ("name" -> "testZone.") ~~
          ("email" -> "test@test.com") ~~
          ("connection" -> primaryConnection) ~~
          ("adminGroupId" -> "admin-group-id")

      val expected = completeCreateZoneInput.copy(transferConnection = None)
      val actual = createZoneInput.extract[CreateZoneInput]
      actual shouldBe expected
    }

    "parse a create zone input with a transfer connection" in {
      val createZoneInput: JValue =
        ("name" -> "testZone ") ~~
          ("email" -> "test@test.com") ~~
          ("connection" -> primaryConnection) ~~
          ("transferConnection" -> transferConnection) ~~
          ("adminGroupId" -> "admin-group-id")

      val actual = createZoneInput.extract[CreateZoneInput]
      actual shouldBe completeCreateZoneInput
    }

    "parse a shared create zone input" in {
      val createZoneInput: JValue =
        ("name" -> "testZone.") ~~
          ("email" -> "test@test.com") ~~
          ("connection" -> primaryConnection) ~~
          ("transferConnection" -> transferConnection) ~~
          ("shared" -> true) ~~
          ("adminGroupId" -> "admin-group-id")

      val expected = completeCreateZoneInput.copy(shared = true)
      val actual = createZoneInput.extract[CreateZoneInput]
      actual shouldBe expected
      actual.shared shouldBe true
    }

    "parse a create zone input with a backendId" in {
      val createZoneInput: JValue =
        ("name" -> "testZone.") ~~
          ("email" -> "test@test.com") ~~
          ("adminGroupId" -> "admin-group-id") ~~
          ("backendId" -> "test-backend-id")

      val expected = completeCreateZoneInput.copy(
        connection = None,
        transferConnection = None,
        backendId = Some("test-backend-id")
      )
      val actual = createZoneInput.extract[CreateZoneInput]
      actual shouldBe expected
    }

    "throw an error if zone name is missing" in {
      val createZoneInput: JValue =
        ("email" -> "test@test.com") ~~
          ("adminGroupId" -> "admin-group-id")

      assertThrows[MappingException](createZoneInput.extract[CreateZoneInput])
    }

    "throw an error if zone email is missing" in {
      val createZoneInput: JValue =
        ("name" -> "testZone.") ~~
          ("adminGroupId" -> "admin-group-id")

      assertThrows[MappingException](createZoneInput.extract[CreateZoneInput])
    }

    "throw an error if adminGroupId is missing" in {
      val createZoneInput: JValue =
        ("name" -> "testZone.") ~~
          ("email" -> "test@test.com")

      assertThrows[MappingException](createZoneInput.extract[CreateZoneInput])
    }

    "throw an error if there is a type mismatch during deserialization" in {
      val createZoneInput: JValue =
        ("name" -> "testZone.") ~~
          ("email" -> "test@test.com") ~~
          ("adminGroupId" -> true)

      assertThrows[MappingException](createZoneInput.extract[CreateZoneInput])
    }
  }

  "UpdateZoneInputSerializer" should {
    "parse a zone with no connections" in {
      val updateZoneInput: JValue =
        ("id" -> "updated-zone-id") ~~
          ("name" -> "updated-zone-name.") ~~
          ("email" -> "updated@email.com") ~~
          ("adminGroupId" -> "updated-admin-group-id")

      val expected = completeUpdateZoneInput.copy(connection = None, transferConnection = None)
      val actual = updateZoneInput.extract[UpdateZoneInput]
      actual shouldBe expected
    }

    "parse a zone with a connection and no transferConnection" in {
      val updateZoneInput: JValue =
        ("id" -> "updated-zone-id") ~~
          ("name" -> "updated-zone-name.") ~~
          ("email" -> "updated@email.com") ~~
          ("connection" -> primaryConnection) ~~
          ("adminGroupId" -> "updated-admin-group-id")

      val expected = completeUpdateZoneInput.copy(transferConnection = None)
      val actual = updateZoneInput.extract[UpdateZoneInput]
      actual shouldBe expected
    }

    "parse a zone with a transferConnection" in {
      val updateZoneInput: JValue =
        ("id" -> "updated-zone-id") ~~
          ("name" -> "updated-zone-name.") ~~
          ("email" -> "updated@email.com") ~~
          ("connection" -> primaryConnection) ~~
          ("transferConnection" -> transferConnection) ~~
          ("adminGroupId" -> "updated-admin-group-id")

      val actual = updateZoneInput.extract[UpdateZoneInput]
      actual shouldBe completeUpdateZoneInput
    }

    "parse a shared update zone input" in {
      val updateZoneInput: JValue =
        ("id" -> "updated-zone-id") ~~
          ("name" -> "updated-zone-name.") ~~
          ("email" -> "updated@email.com") ~~
          ("connection" -> primaryConnection) ~~
          ("transferConnection" -> transferConnection) ~~
          ("shared" -> true) ~~
          ("adminGroupId" -> "updated-admin-group-id")

      val expected = completeUpdateZoneInput.copy(shared = true)
      val actual = updateZoneInput.extract[UpdateZoneInput]
      actual shouldBe expected
    }

    "parse an update zone input with a backendId" in {
      val updateZoneInput: JValue =
        ("id" -> "updated-zone-id") ~~
          ("name" -> "updated-zone-name.") ~~
          ("email" -> "updated@email.com") ~~
          ("adminGroupId" -> "updated-admin-group-id") ~~
          ("backendId" -> "test-backend-id")

      val expected = completeUpdateZoneInput.copy(
        connection = None,
        transferConnection = None,
        backendId = Some("test-backend-id")
      )
      val actual = updateZoneInput.extract[UpdateZoneInput]
      actual shouldBe expected
    }

    "throw an error if zone id is missing" in {
      val updateZoneInput: JValue =
        ("name" -> "updated-zone-name.") ~~
          ("email" -> "test@test.com") ~~
          ("adminGroupId" -> "admin-group-id")

      assertThrows[MappingException](updateZoneInput.extract[UpdateZoneInput])
    }

    "throw an error if zone name is missing" in {
      val updateZoneInput: JValue =
        ("id" -> "updated-zone-id") ~~
          ("email" -> "test@test.com") ~~
          ("adminGroupId" -> "admin-group-id")

      assertThrows[MappingException](updateZoneInput.extract[UpdateZoneInput])
    }

    "throw an error if zone email is missing" in {
      val updateZoneInput: JValue =
        ("id" -> "updated-zone-id") ~~
          ("name" -> "updated-zone-name.") ~~
          ("adminGroupId" -> "admin-group-id")

      assertThrows[MappingException](updateZoneInput.extract[UpdateZoneInput])
    }

    "throw an error if adminGroupId is missing" in {
      val updateZoneInput: JValue =
        ("id" -> "updated-zone-id") ~~
          ("name" -> "updated-zone-name.") ~~
          ("email" -> "test@test.com")

      assertThrows[MappingException](updateZoneInput.extract[UpdateZoneInput])
    }

    "throw an error if there is a type mismatch during deserialization" in {
      val updateZoneInput: JValue =
        ("id" -> "updated-zone-id") ~~
          ("name" -> "testZone.") ~~
          ("email" -> "test@test.com") ~~
          ("adminGroupId" -> true)

      assertThrows[MappingException](updateZoneInput.extract[UpdateZoneInput])
    }
  }

  "RecordSetSerializer" should {
    "parse a record set with an absolute CNAME record passes" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "CNAME") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> List("cname" -> "cname."))

      val expected = RecordSet(
        "1",
        "TestRecordName",
        RecordType.CNAME,
        1000,
        RecordSetStatus.Pending,
        LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC),
        records = List(CNAMEData(Fqdn("cname. ")))
      )

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "convert non-dotted CNAME record to an absolute CNAME record" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "CNAME") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> List("cname" -> "cname.data "))

      val expected = RecordSet(
        "1",
        "TestRecordName",
        RecordType.CNAME,
        1000,
        RecordSetStatus.Pending,
        LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC),
        records = List(CNAMEData(Fqdn("cname.data.")))
      )

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
      anonymize(actual).records shouldBe List(CNAMEData(Fqdn("cname.data.")))
    }
    "reject a relative CNAME record" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "CNAME") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> List("cname" -> "cname "))

      val thrown = the[MappingException] thrownBy recordSetJValue.extract[RecordSet]
      thrown.msg should include("CNAME data must be absolute")
    }

    "parse a record set with an absolute MX exchange passes" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "MX") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(Set(MXData(1, Fqdn("mx.")))))

      val expected = RecordSet(
        "1",
        "TestRecordName",
        RecordType.MX,
        1000,
        RecordSetStatus.Pending,
        LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC),
        records = List(MXData(1, Fqdn("mx.")))
      )

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "convert relative MX exchange to an absolute MX exchange" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "MX") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(Set(MXData(1, Fqdn("mx")))))

      val expected = RecordSet(
        "1",
        "TestRecordName",
        RecordType.MX,
        1000,
        RecordSetStatus.Pending,
        LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC),
        records = List(MXData(1, Fqdn("mx.")))
      )

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
      anonymize(actual).records shouldBe List(MXData(1, Fqdn("mx.")))
    }

    "parse a record set with an absolute SRV target passes" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "SRV") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(Set(SRVData(1, 20, 5000, Fqdn("srv.")))))

      val expected = RecordSet(
        "1",
        "TestRecordName",
        RecordType.SRV,
        1000,
        RecordSetStatus.Pending,
        LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC),
        records = List(SRVData(1, 20, 5000, Fqdn("srv.")))
      )

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "convert relative SRV target to an absolute SRV target" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "SRV") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(Set(SRVData(1, 20, 5000, Fqdn("srv")))))

      val expected = RecordSet(
        "1",
        "TestRecordName",
        RecordType.SRV,
        1000,
        RecordSetStatus.Pending,
        LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC),
        records = List(SRVData(1, 20, 5000, Fqdn("srv.")))
      )

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
      anonymize(actual).records shouldBe List(SRVData(1, 20, 5000, Fqdn("srv.")))
    }

    "parse a record set with an absolute NAPTR target passes" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "NAPTR") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(
            Set(NAPTRData(1, 20, "U", "E2U+sip", "!.*!test.!", Fqdn("naptr.")))
          ))

      val expected = RecordSet(
        "1",
        "TestRecordName",
        RecordType.NAPTR,
        1000,
        RecordSetStatus.Pending,
        LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC),
        records = List(NAPTRData(1, 20, "U", "E2U+sip", "!.*!test.!", Fqdn("naptr.")))
      )

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "convert relative NAPTR target to an absolute NAPTR target" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "NAPTR") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(
            Set(NAPTRData(1, 20, "U", "E2U+sip", "!.*!test.!", Fqdn("naptr")))
          ))

      val expected = RecordSet(
        "1",
        "TestRecordName",
        RecordType.NAPTR,
        1000,
        RecordSetStatus.Pending,
        LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC),
        records = List(NAPTRData(1, 20, "U", "E2U+sip", "!.*!test.!", Fqdn("naptr.")))
      )

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
      anonymize(actual).records shouldBe List(
        NAPTRData(1, 20, "U", "E2U+sip", "!.*!test.!", Fqdn("naptr."))
      )
    }

    "parse a record set with an absolute PTR domain name" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "PTR") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(Set(PTRData(Fqdn("ptr.")))))

      val expected = RecordSet(
        "1",
        "TestRecordName",
        RecordType.PTR,
        1000,
        RecordSetStatus.Pending,
        LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC),
        records = List(PTRData(Fqdn("ptr.")))
      )

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "convert relative PTR domain name to an absolute PTR domain name" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "PTR") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(Set(PTRData(Fqdn("ptr")))))

      val expected = RecordSet(
        "1",
        "TestRecordName",
        RecordType.PTR,
        1000,
        RecordSetStatus.Pending,
        LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC),
        records = List(PTRData(Fqdn("ptr.")))
      )

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
      anonymize(actual).records shouldBe List(PTRData(Fqdn("ptr.")))
    }
    "convert non-dotted NS record to an absolute NS record" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "NS") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> List("nsdname" -> "abs.data"))

      val expected = RecordSet(
        "1",
        "TestRecordName",
        RecordType.NS,
        1000,
        RecordSetStatus.Pending,
        LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC),
        records = List(NSData(Fqdn("abs.data")))
      )

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
      anonymize(actual).records shouldBe List(NSData(Fqdn("abs.data.")))
    }
    "reject a relative NS record" in {
      val data = List("nsdname" -> "abs")

      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "NS") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> data)

      val thrown = the[MappingException] thrownBy recordSetJValue.extract[RecordSet]
      thrown.msg should include(NSDataError)
    }
    "round trip a DS record set" in {
      val rs = RecordSet(
        "1",
        "TestRecordName",
        RecordType.DS,
        1000,
        RecordSetStatus.Pending,
        LocalDateTime.of(2010, Month.JANUARY, 1, 0, 0).toInstant(ZoneOffset.UTC),
        records = List(dSDataSha1)
      )

      RecordSetSerializer.fromJson(RecordSetSerializer.toJson(rs)).value shouldBe rs
    }
    "reject a DS record with non-hex digest" in {
      val dsData = ("keytag" -> 60485) ~
        ("algorithm" -> 5) ~
        ("digesttype" -> 1) ~
        ("digest" -> "G123")

      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "DS") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> List(dsData))

      val thrown = the[MappingException] thrownBy recordSetJValue.extract[RecordSet]
      thrown.msg should include("Could not convert digest to valid hex")
    }
    "reject a DS record with unknown algorithm" in {
      val dsData = ("keytag" -> 60485) ~
        ("algorithm" -> 0) ~
        ("digesttype" -> 1) ~
        ("digest" -> "2BB183AF5F22588179A53B0A98631FAD1A292118")

      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "DS") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> List(dsData))

      val thrown = the[MappingException] thrownBy recordSetJValue.extract[RecordSet]
      thrown.msg should include("Algorithm 0 is not a supported DNSSEC algorithm")
    }
    "reject a DS record with digest type" in {
      val dsData = ("keytag" -> 60485) ~
        ("algorithm" -> 5) ~
        ("digesttype" -> 0) ~
        ("digest" -> "2BB183AF5F22588179A53B0A98631FAD1A292118")

      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "TestRecordName") ~~
          ("type" -> "DS") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> List(dsData))

      val thrown = the[MappingException] thrownBy recordSetJValue.extract[RecordSet]
      thrown.msg should include("Digest Type 0 is not a supported DS record digest type")
    }
  }
}

object Serializer extends VinylDNSJsonProtocol
