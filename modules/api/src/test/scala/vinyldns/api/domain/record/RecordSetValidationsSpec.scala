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
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.domain.record.RecordType._
import vinyldns.api.domain.zone.{InvalidGroupError, InvalidRequest, PendingUpdateError, RecordSetAlreadyExists, RecordSetValidation}
import vinyldns.api.ResultHelpers
import vinyldns.api.VinylDNSTestHelpers
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.membership.Group
import vinyldns.core.domain.record._
import vinyldns.core.Messages._

import scala.util.matching.Regex

class RecordSetValidationsSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ResultHelpers
    with EitherMatchers {

  import RecordSetValidations._

  val dottedHostsConfigZonesAllowed: List[String] = VinylDNSTestHelpers.dottedHostsConfig.zoneAuthConfigs.map(x => x.zone)

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
        val error = leftValue(recordSetDoesNotExist(aaaa, existing, okZone))
        error shouldBe a[RecordSetAlreadyExists]
      }

      "return the record set if the record set exists but has the same id" in {
        recordSetDoesNotExist(aaaa, List(aaaa), okZone) should be(right)
      }

      "return the record set if the record set does not exist" in {
        recordSetDoesNotExist(aaaa, List(), okZone) should be(right)
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

    "isDotted" should {
      "return a failure for any record with dotted hosts if it is already present" in {
        val test = aaaa.copy(name = "this.is.a.failure.")
        leftValue(isDotted(test, okZone, None, false, true)) shouldBe an[InvalidRequest]
      }

      "return a failure for any record that is a dotted host if user or record type is not allowed" in {
        val test = aaaa.copy(name = "this.is.a.failure." + okZone.name)
        leftValue(isDotted(test, okZone, None, true, false)) shouldBe an[InvalidRequest]
      }

      "return success for a dotted record if it does not already have a record or zone with same name and user is allowed" in {
        val test = aaaa.copy(name = "this.passes")
        isDotted(test, okZone, None, true, true) should be(right)
      }

      "return success for a new record that has the same name as the existing record" in {
        val newRecord = aaaa.copy(name = "dot.ted")
        val existingRecord = newRecord.copy(ttl = 330)

        isDotted(newRecord, okZone, Some(existingRecord), true, true) should be(right)
      }
    }

    "typeSpecificValidations" should {
      "Run dotted hosts checks" should {
        val dottedARecord = rsOk.copy(name = "this.is.a.failure.")
        "return a failure for any new record with dotted hosts in forward zones" in {
          leftValue(
            typeSpecificValidations(dottedARecord, List(), okZone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false)
          ) shouldBe an[InvalidRequest]
        }

        "return a failure for any new record with dotted hosts in forward zones (CNAME)" in {
          leftValue(
            typeSpecificValidations(dottedARecord.copy(typ = CNAME), List(), okZone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false)
          ) shouldBe an[InvalidRequest]
        }

        "return a failure for any new record with dotted hosts in forward zones (NS)" in {
          leftValue(
            typeSpecificValidations(dottedARecord.copy(typ = NS), List(), okZone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false)
          ) shouldBe an[InvalidRequest]
        }

        "return a success for any new record with dotted hosts in forward zones if it satisfies dotted hosts configs" in {
          // Zone, User, Record Type and Number of dots are all satisfied
          val record = typeSpecificValidations(dottedARecord.copy(typ = CNAME, zoneId = dottedZone.id), List(), dottedZone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, true, 5)
          record should be(right)
        }

        "return a failure for any new record with dotted hosts if no.of.dots allowed is 0" in {
          // Zone, User, Record Type and Number of dots are all satisfied
          leftValue(
            typeSpecificValidations(dottedARecord.copy(typ = CNAME, zoneId = dottedZone.id), List(), dottedZone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, true, 0)
          ) shouldBe an[InvalidRequest]
        }

        "return a failure for any new record with dotted hosts in forward zones (A record) if it doesn't satisfy dotted hosts configs" in {
          // 'A' record is not allowed in the config
          leftValue(
            typeSpecificValidations(dottedARecord.copy(zoneId = dottedZone.id), List(), dottedZone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false, 5)
          ) shouldBe an[InvalidRequest]
        }

        "return a failure for any new record with dotted hosts in forward zones (NS record) if it doesn't satisfy dotted hosts configs" in {
          // 'NS' record is not allowed in the config
          leftValue(
            typeSpecificValidations(dottedARecord.copy(typ = NS, zoneId = dottedZone.id), List(), dottedZone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false, 5)
          ) shouldBe an[InvalidRequest]
        }

        "return a success for any existing record with dotted hosts in forward zones" in {
          typeSpecificValidations(
            dottedARecord,
            List(),
            okZone,
            Some(dottedARecord.copy(ttl = 300)),
            Nil,
            true,
            dottedHostsConfigZonesAllowed.toSet,
            false
          ) should be(right)
        }

        "return a success for any existing record with dotted hosts in forward zones (CNAME)" in {
          val dottedCNAMERecord = dottedARecord.copy(typ = CNAME)
          typeSpecificValidations(
            dottedCNAMERecord,
            List(),
            okZone,
            Some(dottedCNAMERecord.copy(ttl = 300)),
            Nil,
            true,
            dottedHostsConfigZonesAllowed.toSet,
            false
          ) should be(right)
        }

        "return a failure for any existing record with dotted hosts in forward zones (NS)" in {
          val dottedNSRecord = dottedARecord.copy(typ = NS)
          leftValue(
            typeSpecificValidations(
              dottedNSRecord,
              List(),
              okZone,
              Some(dottedNSRecord.copy(ttl = 300)),
              Nil,
              true,
              dottedHostsConfigZonesAllowed.toSet,
              false
            )
          ) shouldBe an[InvalidRequest]
        }
      }

      "Skip dotted checks on SRV" should {
        "return success for an SRV record following convention with FQDN" in {
          val test = srv.copy(name = "_sip._tcp.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
        }

        "return success for an SRV record following convention without FQDN" in {
          val test = srv.copy(name = "_sip._tcp")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
        }

        "return success for an SRV record following convention with a record name" in {
          val test = srv.copy(name = "_sip._tcp.foo.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
        }

        "return success on a wildcard SRV that follows convention" in {
          val test = srv.copy(name = "*._tcp.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
        }

        "return success on a wildcard in second position SRV that follows convention" in {
          val test = srv.copy(name = "_sip._*.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
        }
      }
      "Skip dotted checks on NAPTR" should {
        "return success for an NAPTR record with FQDN" in {
          val test = naptr.copy(name = "sub.naptr.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
        }

        "return success for an NAPTR record without FQDN" in {
          val test = naptr.copy(name = "sub.naptr")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
        }

        "return success on a wildcard NAPTR" in {
          val test = naptr.copy(name = "*.sub.naptr.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
        }

      }
      "Skip dotted checks on PTR" should {
        "return success for a PTR record with dots in a reverse zone" in {
          val test = ptrIp4.copy(name = "10.1.2.")
          val zone = zoneIp4.copy(name = "198.in-addr.arpa.")

          typeSpecificValidations(test, List(), zone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
        }
      }
      "Skip dotted checks on TXT" should {
        "return success for a TXT record with dots in a reverse zone" in {
          val test = txt.copy(name = "sub.txt.example.com.")
          val zone = okZone.copy(name = "example.com.")

          typeSpecificValidations(test, List(), zone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
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
            Instant.now.truncatedTo(ChronoUnit.MILLIS),
            None,
            List(SOAData(Fqdn("something"), "other", 1, 2, 3, 5, 6))
          )

          typeSpecificValidations(test, List(), zoneIp4, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
        }
      }
    }

    "NSValidations" should {
      val invalidNsApexRs: RecordSet = ns.copy(name = "@")
      "return ok if the record is an NS record but not origin" in {
        val valid = invalidNsApexRs.copy(
          name = "this-is-not-origin-mate",
          records = List(NSData(Fqdn("some.test.ns.")))
        )

        nsValidations(valid, okZone, None, List(new Regex(".*")), true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
      }

      "return an InvalidRequest if an NS record is '@'" in {
        val error = leftValue(nsValidations(invalidNsApexRs, okZone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }

      "return an InvalidRequest if an NS record is the same as the zone" in {
        val invalid = invalidNsApexRs.copy(name = okZone.name)
        val error = leftValue(nsValidations(invalid, okZone, None, Nil, true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }

      "return an InvalidRequest if the NS record being updated is '@'" in {
        val valid = invalidNsApexRs.copy(name = "this-is-not-origin-mate")
        val error = leftValue(nsValidations(valid, okZone, Some(invalidNsApexRs), Nil, true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }

      "return an InvalidRequest if an NS record data is not in the approved server list" in {
        val ns = invalidNsApexRs.copy(records = List(NSData(Fqdn("not.approved."))))
        val error = leftValue(nsValidations(ns, okZone, None, List(new Regex("not.*")), true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }
    }

    "DSValidations" should {
      val matchingNs = ns.copy(zoneId = ds.zoneId, name = ds.name, ttl = ds.ttl)
      "return ok if the record is non-origin DS with matching NS" in {
        dsValidations(ds, List(matchingNs), okZone, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
      }
      "return an InvalidRequest if a DS record is '@'" in {
        val apex = ds.copy(name = "@")
        val error = leftValue(dsValidations(apex, List(matchingNs), okZone, true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if a DS record is the same as the zone" in {
        val apex = ds.copy(name = okZone.name)
        val error = leftValue(dsValidations(apex, List(matchingNs), okZone, true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if there is no NS matching the record" in {
        val error = leftValue(dsValidations(ds, List(), okZone, true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if the DS is dotted" in {
        val error =
          leftValue(dsValidations(ds.copy(name = "test.dotted"), List(matchingNs), okZone, true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }
      "return ok if the DS is dotted and zone, user, record type is allowed in dotted hosts config" in {
        val record =
          dsValidations(ds.copy(name = "dotted.trial", zoneId = dottedZone.id), List(matchingNs), dottedZone, true, dottedHostsConfigZonesAllowed.toSet, true, 5)
        record should be(right)
      }
      "return an InvalidRequest if the DS is dotted and zone, user, record type is allowed in dotted hosts config but has a conflict with existing record or zone" in {
        val error =
          leftValue(dsValidations(ds.copy(name = "dotted.trial", zoneId = dottedZone.id), List(matchingNs), dottedZone, false, dottedHostsConfigZonesAllowed.toSet, true))
        error shouldBe an[InvalidRequest]
      }
    }

    "CnameValidations" should {
      val invalidCnameApexRs: RecordSet = cname.copy(name = "@")
      "return a RecordSetAlreadyExistsError if a record with the same name exists and creating a cname" in {
        val error = leftValue(cnameValidations(cname, List(aaaa), okZone, None, true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe a[RecordSetAlreadyExists]
      }
      "return ok if name is not '@'" in {
        cnameValidations(cname, List(), okZone, None, true, dottedHostsConfigZonesAllowed.toSet, false) should be(right)
      }
      "return an InvalidRequest if a cname record set name is '@'" in {
        val error = leftValue(cnameValidations(invalidCnameApexRs, List(), okZone, None,  true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if a cname record set name is same as zone" in {
        val invalid = invalidCnameApexRs.copy(name = okZone.name)
        val error = leftValue(cnameValidations(invalid, List(), okZone, None, true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if a cname record set fqdn is IPv4 address" in {
        val error = leftValue(cnameValidations(cname.copy(records = List(CNAMEData(Fqdn("1.2.3.4")))), List(), okZone, None, true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[RecordSetValidation]
      }
      "return an InvalidRequest if a cname record set name is dotted" in {
        val error = leftValue(cnameValidations(cname.copy(name = "dot.ted"), List(), okZone, None, true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }
      "return ok if new recordset name does not contain dot" in {
        cnameValidations(cname, List(), okZone, Some(cname.copy(name = "not-dotted")), true, dottedHostsConfigZonesAllowed.toSet, false) should be(
          right
        )
      }
      "return ok if dotted host name doesn't change" in {
        val newRecord = cname.copy(name = "dot.ted", ttl = 500)
        cnameValidations(newRecord, List(), okZone, Some(newRecord.copy(ttl = 300)), true, dottedHostsConfigZonesAllowed.toSet, false) should be(
          right
        )
      }
      "return an InvalidRequest if a cname record set name is updated to '@'" in {
        val error = leftValue(cnameValidations(cname.copy(name = "@"), List(), okZone, Some(cname), true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }
      "return an InvalidRequest if updated cname record set name is same as zone" in {
        val error =
          leftValue(cnameValidations(cname.copy(name = okZone.name), List(), okZone, Some(cname), true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[InvalidRequest]
      }
      "return an RecordSetValidation error if recordset data contain more than one sequential '.'" in {
        val error = leftValue(cnameValidations(cname.copy(records = List(CNAMEData(Fqdn("record..zone")))), List(), okZone, None, true, dottedHostsConfigZonesAllowed.toSet, false))
        error shouldBe an[RecordSetValidation]
      }
      "return ok if recordset data does not contain sequential '.'" in {
        cnameValidations(cname.copy(records = List(CNAMEData(Fqdn("record.zone")))), List(), okZone, None, true, dottedHostsConfigZonesAllowed.toSet, false) should be(
          right
        )
      }
      "return ok if the CNAME is dotted and zone, user, record type is allowed in dotted hosts config" in {
        val record =
          cnameValidations(cname.copy(name = "dot.ted", zoneId = dottedZone.id), List(), dottedZone, None, true, dottedHostsConfigZonesAllowed.toSet, true, 5)
        record should be(right)
      }
      "return an InvalidRequest if the CNAME is dotted and zone, user, record type is allowed in dotted hosts config but has a conflict with existing record or zone" in {
        val error =
          leftValue(cnameValidations(cname.copy(name = "dot.ted", zoneId = dottedZone.id), List(), dottedZone, None, false, dottedHostsConfigZonesAllowed.toSet, true))
        error shouldBe an[InvalidRequest]
      }
    }

    "isNotHighValueDomain" should {
      "return InvalidRequest if a ptr ip4 record matches a High Value Domain" in {
        val zone = okZone.copy(name = "2.0.192.in-addr.arpa.")
        val record = ptrIp4.copy(name = "252")

        val error =
          leftValue(isNotHighValueDomain(record, zone, VinylDNSTestHelpers.highValueDomainConfig))
        error shouldBe an[InvalidRequest]
      }

      "return InvalidRequest if a ptr ip6 record matches a High Value Domain" in {
        val zone = okZone.copy(name = "1.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.")
        val record = ptrIp6.copy(name = "f.f.f.f.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0")

        val error =
          leftValue(isNotHighValueDomain(record, zone, VinylDNSTestHelpers.highValueDomainConfig))
        error shouldBe an[InvalidRequest]
      }

      "return InvalidRequest if a non ptr record matches a High Value Domain" in {
        val zone = okZone
        val record = aaaa.copy(name = "high-value-domain")

        val error =
          leftValue(isNotHighValueDomain(record, zone, VinylDNSTestHelpers.highValueDomainConfig))
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

        isNotHighValueDomain(recordIp4, zoneIp4, VinylDNSTestHelpers.highValueDomainConfig) should be(
          right
        )
        isNotHighValueDomain(recordIp6, zoneIp6, VinylDNSTestHelpers.highValueDomainConfig) should be(
          right
        )
        isNotHighValueDomain(
          recordClassless,
          zoneClassless,
          VinylDNSTestHelpers.highValueDomainConfig
        ) should be(
          right
        )
        isNotHighValueDomain(recordAAAA, zoneAAAA, VinylDNSTestHelpers.highValueDomainConfig) should be(
          right
        )
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
          signedInUser = okAuth.signedInUser.copy(isSuper = true)
        )
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

    "unchangedRecordName" should {
      "return ok when given name is @ and existing record is apex" in {
        val zone = okZone
        val existing = rsOk.copy(name = zone.name)
        val rs = rsOk.copy(name = "@")
        unchangedRecordName(existing, rs, zone) should be(right)
      }
      "return invalid when given name is apex without trailing dot and existing record is apex" in {
        val zone = okZone
        val existing = rsOk.copy(name = zone.name)
        val rs = rsOk.copy(name = "ok.zone.recordsets")
        val error = leftValue(unchangedRecordName(existing, rs, zone))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet's name."
      }
      "return invalid request when given record name does not match existing record name" in {
        val existing = rsOk
        val rs = rsOk.copy(name = "non-matching-name")
        val zone = okZone
        val error = leftValue(unchangedRecordName(existing, rs, zone))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet's name."
      }
    }

    "unchangedRecordType" should {
      "return invalid request when given record type does not match existing recordset record type" in {
        val existing = rsOk
        val rs = rsOk.copy(typ = AAAA)
        val error = leftValue(unchangedRecordType(existing, rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet's record type."
      }
    }

    "unchangedZoneId" should {
      "return invalid request when given zone ID does not match existing recordset zone ID" in {
        val existing = rsOk
        val rs = rsOk.copy(zoneId = "not-real")
        val error = leftValue(unchangedZoneId(existing, rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet's zone ID."
      }
    }

    "validRecordNameFilterLength" should {
      "return valid when given a string containing at least two letters or numbers" in {
        val validString = ".ok"
        validRecordNameFilterLength(validString) should be(right)
      }
      "return invalid when given a string that does not contan at least two letters or numbers" in {
        val invalidString = "*o*"
        val error = leftValue(validRecordNameFilterLength(invalidString))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe RecordNameFilterError
      }
    }
    
    "canSuperUserUpdateOwnerGroup" should {
      "return true when record owner group is the only field changed in the updated record, the zone is shared, " +
        "and user is a superuser" in {
        val zone = sharedZone
        val existing = sharedZoneRecord.copy(ownerGroupId = Some(okGroup.id))
        val rs = sharedZoneRecord.copy(ownerGroupId = Some(dummyGroup.id))
        canSuperUserUpdateOwnerGroup(existing, rs, zone, superUserAuth) should be(true)
      }
      "return false when record owner group is the only field changed in the updated record, the zone is shared, " +
        "and user is NOT a superuser" in {
        val zone = sharedZone
        val existing = sharedZoneRecord.copy(ownerGroupId = Some(okGroup.id))
        val rs = sharedZoneRecord.copy(ownerGroupId = Some(dummyGroup.id))
        canSuperUserUpdateOwnerGroup(existing, rs, zone, okAuth) should be(false)
      }
      "return false when record owner group is the only field changed in the updated record, the zone is NOT shared, " +
        "and user is a superuser" in {
        val zone = okZone
        val existing = sharedZoneRecord.copy(ownerGroupId = Some(okGroup.id))
        val rs = sharedZoneRecord.copy(ownerGroupId = Some(dummyGroup.id))
        canSuperUserUpdateOwnerGroup(existing, rs, zone, superUserAuth) should be(false)
      }
      /*"return false when record owner group is NOT the only field changed in the updated record" in {
        val zone = sharedZone
        val existing = sharedZoneRecord.copy(ownerGroupId = Some(okGroup.id), records = List(AData("10.1.1.1")))
        val rs = sharedZoneRecord.copy(ownerGroupId = Some(dummyGroup.id), records = List(AData("10.1.1.2")))
        canSuperUserUpdateOwnerGroup(existing, rs, zone, superUserAuth) should be(false)
      }*/

      "return true when record owner group is NOT the only field changed in the updated record" in {
        val zone = sharedZone
        val existing = sharedZoneRecord.copy(ownerGroupId = Some(okGroup.id), records = List(AData("10.1.1.1")))
        val rs = sharedZoneRecord.copy(ownerGroupId = Some(dummyGroup.id), records = List(AData("10.1.1.2")))
        canSuperUserUpdateOwnerGroup(existing, rs, zone, superUserAuth) should be(true)
      }
    }
    "unchangedRecordSet" should {
      "return invalid request when given zone ID does not match existing recordset zone ID" in {
        val existing = rsOk
        val rs = rsOk.copy(zoneId = "not-real")
        val error = leftValue(unchangedRecordSet(existing, rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet's if user not a member of ownership group. User can only request for ownership transfer"
      }
      "return invalid request when given record type does not match existing recordset record type" in {
        val existing = rsOk
        val rs = rsOk.copy(typ = RecordType.AAAA)
        val error = leftValue(unchangedRecordSet(existing, rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet's if user not a member of ownership group. User can only request for ownership transfer"
      }
      "return invalid request when given records does not match existing recordset records" in {
        val existing = rsOk
        val rs = rsOk.copy(records = List(AData("10.1.1.0")))
        val error = leftValue(unchangedRecordSet(existing, rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet's if user not a member of ownership group. User can only request for ownership transfer"
      }
      "return invalid request when given recordset id does not match existing recordset ID" in {
        val existing = rsOk
        val rs = rsOk.copy(id = abcRecord.id)
        val error = leftValue(unchangedRecordSet(existing, rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet's if user not a member of ownership group. User can only request for ownership transfer"
      }
      "return invalid request when given recordset name does not match existing recordset name" in {
        val existing = rsOk
        val rs = rsOk.copy(name = "abc")
        val error = leftValue(unchangedRecordSet(existing, rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet's if user not a member of ownership group. User can only request for ownership transfer"
      }
      "return invalid request when given owner group ID does not match existing recordset owner group ID" in {
        val existing = rsOk
        val rs = rsOk.copy(ownerGroupId = Some(abcGroup.id))
        val error = leftValue(unchangedRecordSet(existing, rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet's if user not a member of ownership group. User can only request for ownership transfer"
      }
      "return invalid request when given ttl does not match existing recordset ttl" in {
        val existing = rsOk
        val rs = rsOk.copy(ttl = 3000)
        val error = leftValue(unchangedRecordSet(existing, rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet's if user not a member of ownership group. User can only request for ownership transfer"
      }
    }
    "recordSetOwnerShipApproveStatus" should {
      "return invalid request when given ownership transfer does not match OwnerShipTransferStatus as ManuallyRejected" in {
        val rs = rsOk.copy(recordSetGroupChange = Some(ownerShipTransfer.copy(OwnerShipTransferStatus.ManuallyRejected)))
        val error = leftValue(recordSetOwnerShipApproveStatus(rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet OwnerShip Status when request is cancelled."
      }
      "return invalid request when given ownership transfer does not match OwnerShipTransferStatus as ManuallyApproved" in {
        val rs = rsOk.copy(recordSetGroupChange = Some(ownerShipTransfer.copy(OwnerShipTransferStatus.ManuallyApproved)))
        val error = leftValue(recordSetOwnerShipApproveStatus(rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet OwnerShip Status when request is cancelled."
      }
      "return invalid request when given ownership transfer does not match OwnerShipTransferStatus as AutoApproved" in {
        val rs = rsOk.copy(recordSetGroupChange = Some(ownerShipTransfer.copy(OwnerShipTransferStatus.AutoApproved)))
        val error = leftValue(recordSetOwnerShipApproveStatus(rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet OwnerShip Status when request is cancelled."
      }
    }
    "unchangedRecordSetOwnershipStatus" should {
      "return invalid request when given ownership transfer status does not match existing recordset ownership transfer status for non shared zones" in {
        val existing = rsOk
        val rs = rsOk.copy(recordSetGroupChange = Some(ownerShipTransfer.copy(OwnerShipTransferStatus.AutoApproved)))
        val error = leftValue(unchangedRecordSetOwnershipStatus(existing, rs))
        error shouldBe an[InvalidRequest]
        error.getMessage() shouldBe "Cannot update RecordSet OwnerShip Status when zone is not shared."
      }
    }
  }
}
