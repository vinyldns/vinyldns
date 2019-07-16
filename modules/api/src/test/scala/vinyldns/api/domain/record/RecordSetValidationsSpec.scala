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

import cats.scalatest.EitherMatchers
import org.joda.time.DateTime
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.domain.record.RecordType._
import vinyldns.api.domain.zone.{
  InvalidGroupError,
  InvalidRequest,
  PendingUpdateError,
  RecordSetAlreadyExists
}
import vinyldns.api.ResultHelpers
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain.membership.Group
import vinyldns.core.domain.record._

class RecordSetValidationsSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with EitherMatchers {

  import RecordSetValidations._

  "RecordSetValidations" should {
    "validRecordTypes" should {
      "return invalid request when adding a PTR record to a forward zone" in {
        val error = leftValue(validRecordTypes(ptrIp4, okZone))
        error shouldBe an[InvalidRequest]
      }

      "return invalid request when adding a SRV record to an IP4 reverse zone" in {
        val error = leftValue(validRecordTypes(srv, zoneIp4))
        error shouldBe an[InvalidRequest]
      }

      "return invalid request when adding a NAPTR record to an IP4 reverse zone" in {
        val error = leftValue(validRecordTypes(naptr, zoneIp4))
        error shouldBe an[InvalidRequest]
      }

      "return invalid request when adding a SRV record to an IP6 reverse zone" in {
        val error = leftValue(validRecordTypes(srv, zoneIp6))
        error shouldBe an[InvalidRequest]
      }

      "return invalid request when adding a NAPTR record to an IP6 reverse zone" in {
        val error = leftValue(validRecordTypes(naptr, zoneIp6))
        error shouldBe an[InvalidRequest]
      }

      "return ok when adding an acceptable record to a forward zone" in {
        validRecordTypes(aaaa, okZone) should be(right)
      }

      "return ok when adding an acceptable record to a IP4 reverse zone" in {
        validRecordTypes(ptrIp4, zoneIp4) should be(right)
      }

      "return ok when adding an acceptable record to a IP6 reverse zone" in {
        validRecordTypes(ptrIp4, zoneIp6) should be(right)
      }
    }

    "validRecordNameLength" should {
      "return ok when zone name + record name < 256 characters" in {
        val rs = rsOk
        validRecordNameLength(rs, okZone) should be(right)
      }
      "return invalid request when zone name + record name > 256 characters" in {
        val rs = rsOk.copy(name = "a" * 256)
        val error = leftValue(validRecordNameLength(rs, okZone))
        error shouldBe an[InvalidRequest]
      }
    }

    "notPending" should {
      "return PendingUpdateError if there's already an update pending on the record" in {
        val error = leftValue(notPending(aaaa))
        error shouldBe a[PendingUpdateError]
      }

      "return the record if there are no pending updates" in {
        notPending(rsOk) should be(right)
      }
    }

    "noCnameWithNewName" should {
      "return the record set if no other record sets with name and zone id found" in {
        noCnameWithNewName(aaaa, List(), okZone) should be(right)
      }

      "return a RecordSetAlreadyExistsError if a cname record with the same name exists and creating any record" in {
        val error = leftValue(noCnameWithNewName(aaaa, List(cname), okZone))
        error shouldBe a[RecordSetAlreadyExists]
      }
    }

    "isUniqueUpdate" should {
      "return a RecordSetAlreadyExistsError if a record set already exists with the same name but different id" in {
        val existing = List(aaaa.copy(id = "DifferentID"))
        val error = leftValue(isUniqueUpdate(aaaa, existing, okZone))
        error shouldBe a[RecordSetAlreadyExists]
      }

      "return the record set if the record set exists but has the same id" in {
        isUniqueUpdate(aaaa, List(aaaa), okZone) should be(right)
      }

      "return the record set if the record set does not exist" in {
        isUniqueUpdate(aaaa, List(), okZone) should be(right)
      }
    }

    "isNotDotted" should {
      "return a failure for any record with dotted hosts in forward zones" in {
        val test = aaaa.copy(name = "this.is.a.failure.")
        leftValue(isNotDotted(test, okZone)) shouldBe an[InvalidRequest]
      }

      "return a failure for any record that is a dotted host ending in the zone name" in {
        val test = aaaa.copy(name = "this.is.a.failure." + okZone.name)
        leftValue(isNotDotted(test, okZone)) shouldBe an[InvalidRequest]
      }

      "return a failure for a dotted record name that matches a zone name except for a trailing period" in {
        val test = aaaa.copy(name = "boo.hoo.www.comcast.net")
        val zone = okZone.copy(name = "www.comcast.net.")

        leftValue(isNotDotted(test, zone)) shouldBe an[InvalidRequest]
      }

      "return a failure for a dotted record name that matches a zone name when the record has a trailing period" in {
        val test = aaaa.copy(name = "boo.hoo.www.comcast.net.")
        val zone = okZone.copy(name = "www.comcast.net")
        leftValue(isNotDotted(test, zone)) shouldBe an[InvalidRequest]
      }

      "return success for any record in a forward zone that is not a dotted host" in {
        val test = aaaa.copy(name = "this-passes")
        isNotDotted(test, okZone) should be(right)
      }

      "return success for a record that has the same name as the zone" in {
        val test = aaaa.copy(name = okZone.name)

        isNotDotted(test, okZone) should be(right)
      }

      "return success for a new record that has the same name as the existing record" in {
        val newRecord = aaaa.copy(name = "dot.ted")
        val existingRecord = newRecord.copy(ttl = 330)

        isNotDotted(newRecord, okZone, Some(existingRecord)) should be(right)
      }

      "return failure for a new record that is a dotted record name" in {
        val existingRecord = aaaa.copy(ttl = 330)
        val newRecord = existingRecord.copy(name = "dot.ted")

        leftValue(isNotDotted(newRecord, okZone, Some(existingRecord))) shouldBe an[InvalidRequest]
      }
    }

    "typeSpecificValidations" should {
      "Run dotted hosts checks" should {
        val dottedARecord = rsOk.copy(name = "this.is.a.failure.")
        "return a failure for any new record with dotted hosts in forward zones" in {
          leftValue(
            typeSpecificValidations(dottedARecord, List(), okZone)
          ) shouldBe an[InvalidRequest]
        }

        "return a failure for any new record with dotted hosts in forward zones (CNAME)" in {
          leftValue(
            typeSpecificValidations(dottedARecord.copy(typ = CNAME), List(), okZone)
          ) shouldBe an[InvalidRequest]
        }

        "return a failure for any new record with dotted hosts in forward zones (NS)" in {
          leftValue(
            typeSpecificValidations(dottedARecord.copy(typ = NS), List(), okZone)
          ) shouldBe an[InvalidRequest]
        }

        "return a success for any existing record with dotted hosts in forward zones" in {
          typeSpecificValidations(
            dottedARecord,
            List(),
            okZone,
            Some(dottedARecord.copy(ttl = 300))) should be(right)
        }

        "return a success for any existing record with dotted hosts in forward zones (CNAME)" in {
          val dottedCNAMERecord = dottedARecord.copy(typ = CNAME)
          typeSpecificValidations(
            dottedCNAMERecord,
            List(),
            okZone,
            Some(dottedCNAMERecord.copy(ttl = 300))) should be(right)
        }

        "return a failure for any existing record with dotted hosts in forward zones (NS)" in {
          val dottedNSRecord = dottedARecord.copy(typ = NS)
          leftValue(
            typeSpecificValidations(
              dottedNSRecord,
              List(),
              okZone,
              Some(dottedNSRecord.copy(ttl = 300)))
          ) shouldBe an[InvalidRequest]
        }
      }

      "Skip dotted checks on SRV" should {
        "return success for an SRV record following convention with FQDN" in {
          val test = srv.copy(name = "_sip._tcp.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone) should be(right)
        }

        "return success for an SRV record following convention without FQDN" in {
          val test = srv.copy(name = "_sip._tcp")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone) should be(right)
        }

        "return success for an SRV record following convention with a record name" in {
          val test = srv.copy(name = "_sip._tcp.foo.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone) should be(right)
        }

        "return success on a wildcard SRV that follows convention" in {
          val test = srv.copy(name = "*._tcp.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone) should be(right)
        }

        "return success on a wildcard in second position SRV that follows convention" in {
          val test = srv.copy(name = "_sip._*.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone) should be(right)
        }
      }
      "Skip dotted checks on NAPTR" should {
        "return success for an NAPTR record with FQDN" in {
          val test = naptr.copy(name = "sub.naptr.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone) should be(right)
        }

        "return success for an NAPTR record without FQDN" in {
          val test = naptr.copy(name = "sub.naptr")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone) should be(right)
        }

        "return success on a wildcard NAPTR" in {
          val test = naptr.copy(name = "*.sub.naptr.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone) should be(right)
        }

      }
      "Skip dotted checks on PTR" should {
        "return success for a PTR record with dots in a reverse zone" in {
          val test = ptrIp4.copy(name = "10.1.2.")
          val zone = zoneIp4.copy(name = "198.in-addr.arpa.")

          typeSpecificValidations(test, List(), zone) should be(right)
        }

      }
      "Skip dotted checks on SOA in reverse zones" should {
        "return success for an SOA record with dots in a reverse zone" in {
          val test = RecordSet(
            ptrIp4.name,
            "soa.foo.bar.com",
            SOA,
            200,
            RecordSetStatus.Active,
            DateTime.now,
            None,
            List(SOAData("something", "other", 1, 2, 3, 5, 6)))

          typeSpecificValidations(test, List(), zoneIp4) should be(right)
        }
      }
    }

    "NSValidations" should {
      val invalidNsApexRs: RecordSet = ns.copy(name = "@")
      "return ok if the record is an NS record but not origin" in {
        val valid = invalidNsApexRs.copy(
          name = "this-is-not-origin-mate",
          records = List(NSData("some.test.ns.")))

        nsValidations(valid, okZone) should be(right)
      }

      "return an InvalidRequest if an NS record is '@'" in {
        val error = leftValue(nsValidations(invalidNsApexRs, okZone))
        error shouldBe an[InvalidRequest]
      }

      "return an InvalidRequest if an NS record is the same as the zone" in {
        val invalid = invalidNsApexRs.copy(name = okZone.name)
        val error = leftValue(nsValidations(invalid, okZone))
        error shouldBe an[InvalidRequest]
      }

      "return an InvalidRequest if the NS record being updated is '@'" in {
        val valid = invalidNsApexRs.copy(name = "this-is-not-origin-mate")
        val error = leftValue(nsValidations(valid, okZone, Some(invalidNsApexRs)))
        error shouldBe an[InvalidRequest]
      }

      "return an InvalidRequest if an NS record data is not in the approved server list" in {
        val ns = invalidNsApexRs.copy(records = List(NSData("not.approved.")))
        val error = leftValue(nsValidations(ns, okZone))
        error shouldBe an[InvalidRequest]
      }
    }

    "DSValidations" should {
      val matchingNs = ns.copy(zoneId = ds.zoneId, name = ds.name, ttl = ds.ttl)
      "return ok if the record is non-origin DS with matching NS" in {
        dsValidations(ds, List(matchingNs), okZone) should be(right)
      }
      "return an InvalidRequest if a DS record is '@'" in {
        val apex = ds.copy(name = "@")
        val error = leftValue(dsValidations(apex, List(matchingNs), okZone))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if a DS record is the same as the zone" in {
        val apex = ds.copy(name = okZone.name)
        val error = leftValue(dsValidations(apex, List(matchingNs), okZone))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if there is no NS matching the record" in {
        val error = leftValue(dsValidations(ds, List(), okZone))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if the matching NS has a different TTL" in {
        val error = leftValue(dsValidations(ds, List(matchingNs.copy(ttl = 20)), okZone))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if the DS is dotted" in {
        val error =
          leftValue(dsValidations(ds.copy(name = "test.dotted"), List(matchingNs), okZone))
        error shouldBe an[InvalidRequest]
      }
    }

    "CnameValidations" should {
      val invalidCnameApexRs: RecordSet = cname.copy(name = "@")
      "return a RecordSetAlreadyExistsError if a record with the same name exists and creating a cname" in {
        val error = leftValue(cnameValidations(cname, List(aaaa), okZone))
        error shouldBe a[RecordSetAlreadyExists]
      }
      "return ok if name is not '@'" in {
        cnameValidations(cname, List(), okZone) should be(right)
      }
      "return an InvalidRequest if a cname record set name is '@'" in {
        val error = leftValue(cnameValidations(invalidCnameApexRs, List(), okZone))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if a cname record set name is same as zone" in {
        val invalid = invalidCnameApexRs.copy(name = okZone.name)
        val error = leftValue(cnameValidations(invalid, List(), okZone))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if a cname record set name is dotted" in {
        val error = leftValue(cnameValidations(cname.copy(name = "dot.ted"), List(), okZone))
        error shouldBe an[InvalidRequest]
      }
      "return ok if new recordset name does not contain dot" in {
        cnameValidations(cname, List(), okZone, Some(cname.copy(name = "not-dotted"))) should be(
          right)
      }
      "return ok if dotted host name doesn't change" in {
        val newRecord = cname.copy(name = "dot.ted", ttl = 500)
        cnameValidations(newRecord, List(), okZone, Some(newRecord.copy(ttl = 300))) should be(
          right)
      }
      "return an InvalidRequest if a cname record set name is updated to '@'" in {
        val error = leftValue(cnameValidations(cname.copy(name = "@"), List(), okZone, Some(cname)))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if updated cname record set name is same as zone" in {
        val error =
          leftValue(cnameValidations(cname.copy(name = okZone.name), List(), okZone, Some(cname)))
        error shouldBe an[InvalidRequest]
      }
    }

    "isNotHighValueDomain" should {
      "return InvalidRequest if a ptr ip4 record matches a High Value Domain" in {
        val zone = okZone.copy(name = "2.0.192.in-addr.arpa.")
        val record = ptrIp4.copy(name = "252")

        val error = leftValue(isNotHighValueDomain(record, zone))
        error shouldBe an[InvalidRequest]
      }

      "return InvalidRequest if a ptr ip6 record matches a High Value Domain" in {
        val zone = okZone.copy(name = "1.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.")
        val record = ptrIp6.copy(name = "f.f.f.f.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0")

        val error = leftValue(isNotHighValueDomain(record, zone))
        error shouldBe an[InvalidRequest]
      }

      "return InvalidRequest if a non ptr record matches a High Value Domain" in {
        val zone = okZone
        val record = aaaa.copy(name = "high-value-domain")

        val error = leftValue(isNotHighValueDomain(record, zone))
        error shouldBe an[InvalidRequest]
      }

      "return right if record is not a High Value Domain" in {
        val zoneIp4 = okZone.copy(name = "10.10.10.in-addr.arpa.")
        val recordIp4 = ptrIp4.copy(name = "10")

        val zoneClassless = okZone.copy(name = "10/30.10.10.10.in-addr.arpa.")
        val recordClassless = ptrIp4.copy(name = "10")

        val zoneIp6 = okZone.copy(name = "0.0.0.0.0.0.0.0.0.0.0.0.ip6.arpa.")
        val recordIp6 = ptrIp6.copy(name = "1.2.3.4.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0")

        val zoneAAAA = okZone
        val recordAAAA = aaaa.copy(name = "not-important")

        isNotHighValueDomain(recordIp4, zoneIp4) should be(right)
        isNotHighValueDomain(recordIp6, zoneIp6) should be(right)
        isNotHighValueDomain(recordClassless, zoneClassless) should be(right)
        isNotHighValueDomain(recordAAAA, zoneAAAA) should be(right)
      }
    }

    "canUseOwnerGroup" should {
      "pass if owner group id is None" in {
        canUseOwnerGroup(None, None, okAuth) should be(right)
        canUseOwnerGroup(None, Some(okGroup), okAuth) should be(right)
      }
      "fail if owner group id is provided with no group" in {
        val ownerGroupId = "bar"
        val auth = okAuth.copy(memberGroupIds = Seq("foo"))

        leftValue(canUseOwnerGroup(Some(ownerGroupId), None, auth)) shouldBe a[InvalidGroupError]
      }
      "pass if owner group id is provided and user is super" in {
        val ownerGroupIdGood = "foo"
        val ownerGroupIdBad = "bar"
        val auth = okAuth.copy(
          memberGroupIds = Seq("foo"),
          signedInUser = okAuth.signedInUser.copy(isSuper = true))
        val ownerGroup = Group(id = ownerGroupIdGood, name = "test", email = "test@test.com")

        canUseOwnerGroup(Some(ownerGroupIdGood), Some(ownerGroup), auth) should be(right)
        canUseOwnerGroup(Some(ownerGroupIdBad), Some(ownerGroup), auth) should be(right)
      }
      "pass if owner group if is provided and user is in owner group" in {
        val ownerGroupId = "foo"
        val auth = okAuth.copy(memberGroupIds = Seq("foo"))
        val ownerGroup = Group(id = ownerGroupId, name = "test", email = "test@test.com")

        canUseOwnerGroup(Some(ownerGroupId), Some(ownerGroup), auth) should be(right)
      }
      "fail if owner group id is provided and user is not in owner group" in {
        val ownerGroupId = "bar"
        val auth = okAuth.copy(memberGroupIds = Seq("foo"))
        val ownerGroup = Group(id = ownerGroupId, name = "test", email = "test@test.com")

        val error = leftValue(canUseOwnerGroup(Some(ownerGroupId), Some(ownerGroup), auth))
        error shouldBe an[InvalidRequest]
      }
    }

    "recordSetIsInZone" should {
      "pass if the recordSets's zoneId matches the zone's id" in {
        recordSetIsInZone(aaaa.copy(zoneId = okZone.id), okZone) should be(right)
      }

      "fail if the recordSet's zoneId does not match the zone's id" in {
        val error = leftValue(recordSetIsInZone(aaaa.copy(zoneId = "not-ok-zone"), okZone))
        error shouldBe an[InvalidRequest]
      }
    }
  }
}
