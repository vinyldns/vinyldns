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

import org.joda.time.DateTime
import org.json4s.JsonDSL._
import org.json4s._
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.VinylDNSTestData
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{CreateZoneInput, Zone, ZoneConnection}

class VinylDNSJsonProtocolSpec
    extends WordSpec
    with Matchers
    with VinylDNSJsonProtocol
    with VinylDNSTestData {

  private val completeCreateZoneInput = CreateZoneInput(
    "testZone.",
    "test@test.com",
    connection = Some(
      ZoneConnection(
        "primaryConnection",
        "primaryConnectionKeyName",
        "primaryConnectionKey",
        "10.1.1.1")),
    transferConnection = Some(
      ZoneConnection(
        "transferConnection",
        "transferConnectionKeyName",
        "transferConnectionKey",
        "10.1.1.2")),
    adminGroupId = "admin-group-id"
  )

  private val completeZone = Zone(completeCreateZoneInput)

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

  "ZoneSerializer" should {
    "parse a zone with no connections" in {
      val zone: JValue =
        ("name" -> "testZone.") ~~
          ("email" -> "test@test.com") ~~
          ("adminGroupId" -> "admin-group-id")

      val expected = completeZone.copy(connection = None, transferConnection = None)
      val actual = zone.extract[Zone]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "parse a zone with a connection and no transferConnection" in {
      val zone: JValue =
        ("name" -> "testZone.") ~~
          ("email" -> "test@test.com") ~~
          ("connection" -> primaryConnection) ~~
          ("adminGroupId" -> "admin-group-id")

      val expected = completeZone.copy(transferConnection = None)
      val actual = zone.extract[Zone]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "parse a zone with a transferConnection" in {
      val zone: JValue =
        ("name" -> "testZone.") ~~
          ("email" -> "test@test.com") ~~
          ("connection" -> primaryConnection) ~~
          ("transferConnection" -> transferConnection) ~~
          ("adminGroupId" -> "admin-group-id")

      val actual = zone.extract[Zone]
      anonymize(actual) shouldBe anonymize(completeZone)
    }
  }

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
        ("name" -> "testZone.") ~~
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

  "RecordSetSerializer" should {
    "parse a record set with an absolute CNAME record passes" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "Comcast") ~~
          ("type" -> "CNAME") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> List("cname" -> "cname."))

      val expected = RecordSet(
        "1",
        "Comcast",
        RecordType.CNAME,
        1000,
        RecordSetStatus.Pending,
        new DateTime(2010, 1, 1, 0, 0),
        records = List(CNAMEData("cname.")))

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "convert non-dotted CNAME record to an absolute CNAME record" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "Comcast") ~~
          ("type" -> "CNAME") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> List("cname" -> "cname.data"))

      val expected = RecordSet(
        "1",
        "Comcast",
        RecordType.CNAME,
        1000,
        RecordSetStatus.Pending,
        new DateTime(2010, 1, 1, 0, 0),
        records = List(CNAMEData("cname.data.")))

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
      anonymize(actual).records shouldBe List(CNAMEData("cname.data."))
    }
    "reject a relative CNAME record" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "Comcast") ~~
          ("type" -> "CNAME") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> List("cname" -> "cname"))

      val thrown = the[MappingException] thrownBy recordSetJValue.extract[RecordSet]
      thrown.msg.contains("CNAME data must be absolute") shouldBe true
    }

    "parse a record set with an absolute MX exchange passes" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "Comcast") ~~
          ("type" -> "MX") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(Set(MXData(1, "mx."))))

      val expected = RecordSet(
        "1",
        "Comcast",
        RecordType.MX,
        1000,
        RecordSetStatus.Pending,
        new DateTime(2010, 1, 1, 0, 0),
        records = List(MXData(1, "mx.")))

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "convert relative MX exchange to an absolute MX exchange" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "Comcast") ~~
          ("type" -> "MX") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(Set(MXData(1, "mx"))))

      val expected = RecordSet(
        "1",
        "Comcast",
        RecordType.MX,
        1000,
        RecordSetStatus.Pending,
        new DateTime(2010, 1, 1, 0, 0),
        records = List(MXData(1, "mx.")))

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
      anonymize(actual).records shouldBe List(MXData(1, "mx."))
    }

    "parse a record set with an absolute SRV target passes" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "Comcast") ~~
          ("type" -> "SRV") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(Set(SRVData(1, 20, 5000, "srv."))))

      val expected = RecordSet(
        "1",
        "Comcast",
        RecordType.SRV,
        1000,
        RecordSetStatus.Pending,
        new DateTime(2010, 1, 1, 0, 0),
        records = List(SRVData(1, 20, 5000, "srv.")))

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "convert relative SRV target to an absolute SRV target" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "Comcast") ~~
          ("type" -> "SRV") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(Set(SRVData(1, 20, 5000, "srv"))))

      val expected = RecordSet(
        "1",
        "Comcast",
        RecordType.SRV,
        1000,
        RecordSetStatus.Pending,
        new DateTime(2010, 1, 1, 0, 0),
        records = List(SRVData(1, 20, 5000, "srv.")))

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
      anonymize(actual).records shouldBe List(SRVData(1, 20, 5000, "srv."))
    }

    "parse a record set with an absolute PTR domain name" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "Comcast") ~~
          ("type" -> "PTR") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(Set(PTRData("ptr."))))

      val expected = RecordSet(
        "1",
        "Comcast",
        RecordType.PTR,
        1000,
        RecordSetStatus.Pending,
        new DateTime(2010, 1, 1, 0, 0),
        records = List(PTRData("ptr.")))

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "convert relative PTR domain name to an absolute PTR domain name" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "Comcast") ~~
          ("type" -> "PTR") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> Extraction.decompose(Set(PTRData("ptr"))))

      val expected = RecordSet(
        "1",
        "Comcast",
        RecordType.PTR,
        1000,
        RecordSetStatus.Pending,
        new DateTime(2010, 1, 1, 0, 0),
        records = List(PTRData("ptr.")))

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
      anonymize(actual).records shouldBe List(PTRData("ptr."))
    }

    "convert non-dotted NS record to an absolute NS record" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "Comcast") ~~
          ("type" -> "NS") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> List("nsdname" -> "abs.data"))

      val expected = RecordSet(
        "1",
        "Comcast",
        RecordType.NS,
        1000,
        RecordSetStatus.Pending,
        new DateTime(2010, 1, 1, 0, 0),
        records = List(NSData("abs.data")))

      val actual = recordSetJValue.extract[RecordSet]
      anonymize(actual) shouldBe anonymize(expected)
      anonymize(actual).records shouldBe List(NSData("abs.data."))
    }

    "reject a relative NS record" in {
      val recordSetJValue: JValue =
        ("zoneId" -> "1") ~~
          ("name" -> "Comcast") ~~
          ("type" -> "NS") ~~
          ("ttl" -> 1000) ~~
          ("status" -> "Pending") ~~
          ("records" -> List("nsdname" -> "abs"))

      val thrown = the[MappingException] thrownBy recordSetJValue.extract[RecordSet]
      thrown.msg.contains("NS data must be absolute") shouldBe true
    }
  }
}

object Serializer extends VinylDNSJsonProtocol
