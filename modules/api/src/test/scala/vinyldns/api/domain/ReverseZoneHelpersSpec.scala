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

package vinyldns.api.domain

import cats.scalatest.EitherMatchers
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.domain.record._
import vinyldns.api.domain.zone.InvalidRequest
import vinyldns.api.ResultHelpers
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.zone.Zone

class ReverseZoneHelpersSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with Eventually
    with ResultHelpers
    with EitherMatchers {

  "ReverseZoneHelpersSpec" should {
    "convertPTRtoIPv4" should {

      "convert PTR with two octets in zone and two octets in record set to ip4" in {
        val zn1 = Zone("1.2.in-addr.arpa.", "email")
        val rs1 = RecordSet("id", "4.5", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

        ReverseZoneHelpers.convertPTRtoIPv4(zn1, rs1.name) shouldBe "2.1.5.4"
      }

      "convert PTR with three octets in zone and one octet in record set to ip4" in {
        val zn1 = Zone("100.80.9.in-addr.arpa.", "email")
        val rs1 = RecordSet("id", "3", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

        ReverseZoneHelpers.convertPTRtoIPv4(zn1, rs1.name) shouldBe "9.80.100.3"
      }

      "convert PTR with one octet in zone and three octets in record set to ip4" in {
        val zn1 = Zone("111.in-addr.arpa.", "email")
        val rs1 =
          RecordSet("id", "123.32.4", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

        ReverseZoneHelpers.convertPTRtoIPv4(zn1, rs1.name) shouldBe "111.4.32.123"
      }

      "drop slash part for reverse classless zone delegations" in {
        val zn1 = Zone("192/30.2.0.192.in-addr.arpa.", "email")
        val rs1 = RecordSet("id", "193", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

        ReverseZoneHelpers.convertPTRtoIPv4(zn1, rs1.name) shouldBe "192.0.2.193"
      }

      "convert PTR with two octets in zone and two octets in record set to ip4 with upper case" in {
        val zn1 = Zone("1.2.IN-ADDR.ARPA.", "email")
        val rs1 = RecordSet("id", "4.5", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

        ReverseZoneHelpers.convertPTRtoIPv4(zn1, rs1.name) shouldBe "2.1.5.4"
      }
    }

    "convertPTRtoIPv6" should {

      "convert PTR with 16 nibbles in zone and 16 nibbles in record set to ip6" in {
        val zn1 = Zone("0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.", "email")
        val rs1 = RecordSet(
          "id",
          "b.a.9.8.7.6.5.0.0.0.0.0.0.0.0.0",
          RecordType.PTR,
          200,
          RecordSetStatus.Active,
          Instant.now.truncatedTo(ChronoUnit.MILLIS)
        )

        ReverseZoneHelpers.convertPTRtoIPv6(zn1, rs1.name) shouldBe "2001:0db8:0000:0000:0000:0000:0567:89ab"
      }

      "convert PTR with 20 nibbles in zone and 12 nibbles in record set to ip6" in {
        val zn1 = Zone("0.0.0.0.0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.", "email")
        val rs1 = RecordSet(
          "id",
          "b.a.9.8.7.6.5.0.0.0.0.0",
          RecordType.PTR,
          200,
          RecordSetStatus.Active,
          Instant.now.truncatedTo(ChronoUnit.MILLIS)
        )

        ReverseZoneHelpers.convertPTRtoIPv6(zn1, rs1.name) shouldBe "2001:0db8:0000:0000:0000:0000:0567:89ab"
      }

      "convert PTR with 12 nibbles in zone and 20 nibbles in record set to ip6" in {
        val zn1 = Zone("0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.", "email")
        val rs1 = RecordSet(
          "id",
          "b.a.9.8.7.6.5.0.0.0.0.0.0.0.0.0.0.0",
          RecordType.PTR,
          200,
          RecordSetStatus.Active,
          Instant.now.truncatedTo(ChronoUnit.MILLIS)
        )

        ReverseZoneHelpers.convertPTRtoIPv6(zn1, rs1.name) shouldBe "2001:0db8:0000:0000:0000:0000:0567:89ab"
      }
      "convert PTR with 16 nibbles in zone and 16 nibbles in record set to ip6 with upper case" in {
        val zn1 = Zone("0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.IP6.ARPA.", "email")
        val rs1 = RecordSet(
          "id",
          "B.A.9.8.7.6.5.0.0.0.0.0.0.0.0.0",
          RecordType.PTR,
          200,
          RecordSetStatus.Active,
          Instant.now.truncatedTo(ChronoUnit.MILLIS)
        )

        ReverseZoneHelpers.convertPTRtoIPv6(zn1, rs1.name) shouldBe "2001:0db8:0000:0000:0000:0000:0567:89AB"
      }
    }
    "recordsetIsWithinCidrMask" should {
      "when testing IPv4" should {
        "filter in/out record set based on CIDR rule of 1 (lower bound for ip4 CIDR rules)" in {
          val mask = "120.1.2.0/1"
          val znTrue = Zone("40.120.in-addr.arpa.", "email")
          val rsTrue =
            RecordSet("id", "20.3", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))
          val znFalse = Zone("255.129.in-addr.arpa.", "email")
          val rsFalse =
            RecordSet("id", "255.255", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, znTrue, rsTrue.name) shouldBe true
          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, znFalse, rsFalse.name) shouldBe false
        }

        "filter in/out record set based on CIDR rule of 8" in {
          val mask = "10.10.32.0/19"
          val zone = Zone("10.10.in-addr.arpa.", "email")
          val recordSet =
            RecordSet("id", "90.44", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, zone, recordSet.name) shouldBe true
        }

        "expand shorthand PTR" in {
          val mask = "202.204.62.208/8"
          val znTrue = Zone("202.in-addr.arpa.", "email")
          val rsTrue =
            RecordSet("id", "32.23.100", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))
          val znFalse = Zone("1.23.100.in-addr.arpa.", "email")
          val rsFalse =
            RecordSet("id", "3", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, znTrue, rsTrue.name) shouldBe true
          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, znFalse, rsFalse.name) shouldBe false
        }

        "filter in/out record set based on CIDR rule of 32 (upper bound for ip4 CIDR rules)" in {
          val mask = "120.1.2.0/32"
          val znTrue = Zone("1.120.in-addr.arpa.", "email")
          val rsTrue =
            RecordSet("id", "0.2", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))
          val znFalse = Zone("23.10.in-addr.arpa.", "email")
          val rsFalse =
            RecordSet("id", "3", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, znTrue, rsTrue.name) shouldBe true
          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, znFalse, rsFalse.name) shouldBe false
        }

        "not apply when regex could match a ptr" in {
          val mask = ".*0.*"
          val zone = Zone("1.120.in-addr.arpa.", "email")
          val recordSet =
            RecordSet("id", "0.2", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, zone, recordSet.name) shouldBe false
        }
      }
      "when testing IPv6" should {
        "filter in/out record set based on CIDR rule of 8 (lower bound for ip6 CIDR rules)" in {
          val mask = "5bbe:ffa5:631b:4777:5887:c84a:844e:a3c2/8"
          val znTrue = Zone("7.7.4.b.1.3.6.5.a.f.f.e.b.b.5.ip6.arpa.", "email")
          val rsTrue = RecordSet(
            "id",
            "2.c.3.a.e.4.4.8.a.4.8.c.7.8.8.5.7",
            RecordType.PTR,
            200,
            RecordSetStatus.Active,
            Instant.now.truncatedTo(ChronoUnit.MILLIS)
          )
          val znFalse = Zone("5.b.e.f.9.d.2.f.9.5.c.c.7.4.a.a.8.ip6.arpa.", "email")
          val rsFalse = RecordSet(
            "id",
            "2.3.d.f.3.7.3.4.8.a.5.b.d.1.7",
            RecordType.PTR,
            200,
            RecordSetStatus.Active,
            Instant.now.truncatedTo(ChronoUnit.MILLIS)
          )

          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, znTrue, rsTrue.name) shouldBe true
          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, znFalse, rsFalse.name) shouldBe false
        }

        "filter in/out record set based on CIDR rule of 76" in {
          val mask = "5bbe:ffa5:631b:4777:5887:c84a:844e:a3c2/76"
          val znTrue = Zone("7.7.4.b.1.3.6.5.a.f.f.e.b.b.5.ip6.arpa.", "email")
          val rsTrue = RecordSet(
            "id",
            "f.f.f.f.f.4.4.8.a.4.8.c.7.8.8.5.7",
            RecordType.PTR,
            200,
            RecordSetStatus.Active,
            Instant.now.truncatedTo(ChronoUnit.MILLIS)
          )
          val znFalse = Zone("5.b.e.f.9.d.2.f.9.5.c.c.7.4.a.a.8.ip6.arpa.", "email")
          val rsFalse = RecordSet(
            "id",
            "f.3.d.f.3.7.3.4.8.a.5.b.d.1.7",
            RecordType.PTR,
            200,
            RecordSetStatus.Active,
            Instant.now.truncatedTo(ChronoUnit.MILLIS)
          )

          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, znTrue, rsTrue.name) shouldBe true
          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, znFalse, rsFalse.name) shouldBe false
        }

        "filter in/out record set based on CIDR rule of 128 (upper bound for ip6 CIDR rules)" in {
          val mask = "5bbe:ffa5:631b:4777:5887:c84a:844e:a3c2/128"
          val znTrue = Zone("7.7.4.b.1.3.6.5.a.f.f.e.b.b.5.ip6.arpa.", "email")
          val rsTrue = RecordSet(
            "id",
            "2.c.3.a.e.4.4.8.a.4.8.c.7.8.8.5.7",
            RecordType.PTR,
            200,
            RecordSetStatus.Active,
            Instant.now.truncatedTo(ChronoUnit.MILLIS)
          )
          val znFalse = Zone("5.b.e.f.9.d.2.f.9.5.c.c.7.4.a.a.8.ip6.arpa.", "email")
          val rsFalse = RecordSet(
            "id",
            "f.3.d.f.3.7.3.4.8.a.5.b.d.1.7",
            RecordType.PTR,
            200,
            RecordSetStatus.Active,
            Instant.now.truncatedTo(ChronoUnit.MILLIS)
          )

          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, znTrue, rsTrue.name) shouldBe true
          ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, znFalse, rsFalse.name) shouldBe false
        }
      }
    }
    "ptrIsInClasslessDelegatedZone" should {
      "return InvalidRequest if a PTR is being added to a non-ipv4/ipv6 zone" in {
        eventually {
          val error =
            leftValue(ReverseZoneHelpers.ptrIsInClasslessDelegatedZone(zoneActive, ptrIp6.name))
          error shouldBe a[InvalidRequest]
        }
      }
      "when testing IPv4" should {
        "return ok if the ptr is for an IP4 reverse zone (octet boundry)" in {
          ReverseZoneHelpers.ptrIsInClasslessDelegatedZone(zoneIp4, ptrIp4.name) shouldBe right
        }

        "return InvalidRequest if ptr is not valid for an IP4 reverse zone (octet boundry)" in {
          val badPtr = ptrIp4.copy(name = "1.2.3")

          val error =
            leftValue(ReverseZoneHelpers.ptrIsInClasslessDelegatedZone(zoneIp4, badPtr.name))
          error shouldBe a[InvalidRequest]
        }
        "return ok if the ptr is within CIDR for the zone (classless)" in {
          val zone = Zone("32/27.1.10.10.in-addr.arpa.", "email")
          val record =
            RecordSet("id", "44", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

          ReverseZoneHelpers.ptrIsInClasslessDelegatedZone(zone, record.name) shouldBe right
        }
        "return InvalidRequest if the ptr is outside of the CIDR range for the zone (classless)" in {
          val zone = Zone("32/27.1.10.10.in-addr.arpa.", "email")
          val record =
            RecordSet("id", "90", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

          val error = leftValue(ReverseZoneHelpers.ptrIsInClasslessDelegatedZone(zone, record.name))
          error shouldBe a[InvalidRequest]
        }
        "return InvalidRequest if the ptr created is illegal (classless)" in {
          val zone = Zone("32/27.1.10.10.in-addr.arpa.", "email")
          val record =
            RecordSet("id", "44.44", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

          val error = leftValue(ReverseZoneHelpers.ptrIsInClasslessDelegatedZone(zone, record.name))
          error shouldBe a[InvalidRequest]
        }
        "return InvalidRequest if the ptr is outside of the CIDR range for the zone (classless - 2 octet)" in {
          val zone = Zone("32/19.10.10.in-addr.arpa.", "email")
          val record =
            RecordSet("id", "90.90", RecordType.PTR, 200, RecordSetStatus.Active, Instant.now.truncatedTo(ChronoUnit.MILLIS))

          val error = leftValue(ReverseZoneHelpers.ptrIsInClasslessDelegatedZone(zone, record.name))
          error shouldBe a[InvalidRequest]
        }
      }

      "when testing IPv6" should {
        "return ok if the ptr is for an IP6 reverse zone" in {
          ReverseZoneHelpers.ptrIsInClasslessDelegatedZone(zoneIp6, ptrIp6.name) shouldBe right
        }
        "return invalid request if ptr is not valid for an IP6 reverse zone" in {
          val badPtr = ptrIp6.copy(name = "1.2.3")

          val error =
            leftValue(ReverseZoneHelpers.ptrIsInClasslessDelegatedZone(zoneIp6, badPtr.name))
          error shouldBe a[InvalidRequest]
        }
      }
    }
    "ipIsInIpv4ReverseZone" should {
      "return true for the base zone" in {
        val zone = Zone("1.2.3.in-addr.arpa.", "email")
        ReverseZoneHelpers.ipIsInIpv4ReverseZone(zone, "3.2.1.10") shouldBe true
      }
      "return false for a zone ending in the base zone" in {
        val zone = Zone("21.2.3.in-addr.arpa.", "email")
        ReverseZoneHelpers.ipIsInIpv4ReverseZone(zone, "3.2.1.10") shouldBe false
      }
      "return true for a valid delegated zone" in {
        val zone = Zone("0/30.1.2.3.in-addr.arpa.", "email")
        ReverseZoneHelpers.ipIsInIpv4ReverseZone(zone, "3.2.1.1") shouldBe true
      }
      "return false for a delegated zone that does not contain the fqdn" in {
        val zone = Zone("0/30.1.2.3.in-addr.arpa.", "email")
        ReverseZoneHelpers.ipIsInIpv4ReverseZone(zone, "3.2.1.10") shouldBe false
      }
      "return false for a zone similar to the fqdn" in {
        val zone = Zone("1.2.3.in-addr.arpa.", "email")
        ReverseZoneHelpers.ipIsInIpv4ReverseZone(zone, "3.2.21.10") shouldBe false
      }
    }
  }
}
