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
import vinyldns.api.domain.record.RecordType._
import vinyldns.api.domain.zone.{InvalidRequest, PendingUpdateError, RecordSetAlreadyExists}
import vinyldns.api.{GroupTestData, ResultHelpers, VinylDNSTestData}

class RecordSetValidationsSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with VinylDNSTestData
    with GroupTestData
    with EitherMatchers {

  import RecordSetValidations._

  "RecordSetValidations" should {
    "validRecordTypes" should {
      "return invalid request when adding a PTR record to a forward zone" in {
        val error = leftValue(validRecordTypes(ptrIp4, okZone))
        error shouldBe a[InvalidRequest]
      }

      "return invalid request when adding a SRV record to an IP4 reverse zone" in {
        val error = leftValue(validRecordTypes(srv, zoneIp4))
        error shouldBe a[InvalidRequest]
      }

      "return invalid request when adding a SRV record to an IP6 reverse zone" in {
        val error = leftValue(validRecordTypes(srv, zoneIp6))
        error shouldBe a[InvalidRequest]
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
        error shouldBe a[InvalidRequest]
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
        leftValue(isNotDotted(test, okZone)) shouldBe a[InvalidRequest]
      }

      "return a failure for any record that is a dotted host ending in the zone name" in {
        val test = aaaa.copy(name = "this.is.a.failure." + okZone.name)
        leftValue(isNotDotted(test, okZone)) shouldBe a[InvalidRequest]
      }

      "return a failure for a dotted record name that matches a zone name except for a trailing period" in {
        val test = aaaa.copy(name = "boo.hoo.www.comcast.net")
        val zone = okZone.copy(name = "www.comcast.net.")

        leftValue(isNotDotted(test, zone)) shouldBe a[InvalidRequest]
      }

      "return a failure for a dotted record name that matches a zone name when the record has a trailing period" in {
        val test = aaaa.copy(name = "boo.hoo.www.comcast.net.")
        val zone = okZone.copy(name = "www.comcast.net")
        leftValue(isNotDotted(test, zone)) shouldBe a[InvalidRequest]
      }

      "return success for any record in a forward zone that is not a dotted host" in {
        val test = aaaa.copy(name = "this-passes")
        isNotDotted(test, okZone) should be(right)
      }

      "return success for a record that has the same name as the zone" in {
        val test = aaaa.copy(name = okZone.name)

        isNotDotted(test, okZone) should be(right)
      }
    }

    "typeSpecificAddValidations" should {
      "Run dotted hosts checks" should {
        val dottedARecord = rsOk.copy(name = "this.is.a.failure.")
        "return a failure for any record with dotted hosts in forward zones" in {
          leftValue(
            typeSpecificAddValidations(dottedARecord, List(), okZone)
          ) shouldBe a[InvalidRequest]
        }
        "return a failure for any record with dotted hosts in forward zones (CNAME)" in {
          leftValue(
            typeSpecificAddValidations(dottedARecord.copy(typ = CNAME), List(), okZone)
          ) shouldBe a[InvalidRequest]
        }
        "return a failure for any record with dotted hosts in forward zones (NS)" in {
          leftValue(
            typeSpecificAddValidations(dottedARecord.copy(typ = NS), List(), okZone)
          ) shouldBe a[InvalidRequest]
        }
      }
      "Skip dotted checks on SRV" should {
        "return success for an SRV record following convention with FQDN" in {
          val test = srv.copy(name = "_sip._tcp.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificAddValidations(test, List(), zone) should be(right)
        }

        "return success for an SRV record following convention without FQDN" in {
          val test = srv.copy(name = "_sip._tcp")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificAddValidations(test, List(), zone) should be(right)
        }

        "return success for an SRV record following convention with a record name" in {
          val test = srv.copy(name = "_sip._tcp.foo.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificAddValidations(test, List(), zone) should be(right)
        }

        "return success on a wildcard SRV that follows convention" in {
          val test = srv.copy(name = "*._tcp.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificAddValidations(test, List(), zone) should be(right)
        }

        "return success on a wildcard in second position SRV that follows convention" in {
          val test = srv.copy(name = "_sip._*.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificAddValidations(test, List(), zone) should be(right)
        }
      }
      "Skip dotted checks on PTR" should {
        "return success for a PTR record with dots in a reverse zone" in {
          val test = ptrIp4.copy(name = "10.1.2.")
          val zone = zoneIp4.copy(name = "198.in-addr.arpa.")

          typeSpecificAddValidations(test, List(), zone) should be(right)
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

          typeSpecificAddValidations(test, List(), zoneIp4) should be(right)
        }
      }
    }

    "NSValidations" should {
      "return ok if the record is an NS record but not origin" in {
        val valid = invalidNsRecordToOrigin.copy(
          name = "this-is-not-origin-mate",
          records = List(NSData("some.test.ns.")))

        nsValidations(valid, okZone) should be(right)
      }

      "return an InvalidRequest if an NS record is '@'" in {
        val error = leftValue(nsValidations(invalidNsRecordToOrigin, okZone))
        error shouldBe a[InvalidRequest]
      }

      "return an InvalidRequest if an NS record is the same as the zone" in {
        val invalid = invalidNsRecordToOrigin.copy(name = okZone.name)
        val error = leftValue(nsValidations(invalid, okZone))
        error shouldBe a[InvalidRequest]
      }

      "return an InvalidRequest if the NS record being updated is '@'" in {
        val valid = invalidNsRecordToOrigin.copy(name = "this-is-not-origin-mate")
        val error = leftValue(nsValidations(valid, okZone, Some(invalidNsRecordToOrigin)))
        error shouldBe a[InvalidRequest]
      }

      "return an InvalidRequest if an NS record data is not in the approved server list" in {
        val ns = invalidNsRecordToOrigin.copy(records = List(NSData("not.approved.")))
        val error = leftValue(nsValidations(ns, okZone))
        error shouldBe a[InvalidRequest]
      }
    }

    "CnameValidations" should {
      "return a RecordSetAlreadyExistsError if a record with the same name exists and creating a cname" in {
        val error = leftValue(cnameValidations(cname, List(aaaa), okZone))
        error shouldBe a[RecordSetAlreadyExists]
      }
      "return ok if name is not '@'" in {
        cnameValidations(cname, List(), okZone) should be(right)
      }
      "return an InvalidRequest if a cname record set name is '@'" in {
        val error = leftValue(cnameValidations(invalidCnameToOrigin, List(), okZone))
        error shouldBe a[InvalidRequest]
      }
      "return an InvalidRequest if a cname record set name is same as zone" in {
        val invalid = invalidCnameToOrigin.copy(name = okZone.name)
        val error = leftValue(cnameValidations(invalid, List(), okZone))
        error shouldBe a[InvalidRequest]
      }
    }
  }
}
