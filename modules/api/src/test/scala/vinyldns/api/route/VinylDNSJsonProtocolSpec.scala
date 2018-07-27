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
import vinyldns.api.domain.record._
import vinyldns.api.domain.zone._

class VinylDNSJsonProtocolSpec
    extends WordSpec
    with Matchers
    with VinylDNSJsonProtocol
    with VinylDNSTestData {
  "ZoneSerializer" should {
    "parse a zone with no connections" in {
      val zone: JValue =
        ("name" -> "testZone.") ~~
          ("email" -> "test@test.com")

      val expected = Zone("testZone.", "test@test.com")
      val actual = zone.extract[Zone]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "parse a zone with a connection and no transferConnection" in {
      val connection: JValue =
        ("name" -> "connection") ~~
          ("keyName" -> "keyName") ~~
          ("key" -> "key") ~~
          ("primaryServer" -> "10.1.1.1")

      val zone: JValue =
        ("name" -> "testZone.") ~~
          ("email" -> "test@test.com") ~~
          ("connection" -> connection)

      val expected = Zone(
        "testZone.",
        "test@test.com",
        connection = Some(ZoneConnection("connection", "keyName", "key", "10.1.1.1")),
        transferConnection = None)
      val actual = zone.extract[Zone]
      anonymize(actual) shouldBe anonymize(expected)
    }
    "parse a zone with a transferConnection" in {
      val connection: JValue =
        ("name" -> "connection1") ~~
          ("keyName" -> "keyName1") ~~
          ("key" -> "key1") ~~
          ("primaryServer" -> "10.1.1.1")

      val transferConnection: JValue =
        ("name" -> "connection2") ~~
          ("keyName" -> "keyName2") ~~
          ("key" -> "key2") ~~
          ("primaryServer" -> "10.1.1.2")

      val zone: JValue =
        ("name" -> "testZone.") ~~
          ("email" -> "test@test.com") ~~
          ("connection" -> connection) ~~
          ("transferConnection" -> transferConnection)

      val expected = Zone(
        "testZone.",
        "test@test.com",
        connection = Some(ZoneConnection("connection1", "keyName1", "key1", "10.1.1.1")),
        transferConnection = Some(ZoneConnection("connection2", "keyName2", "key2", "10.1.1.2"))
      )
      val actual = zone.extract[Zone]
      anonymize(actual) shouldBe anonymize(expected)
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
