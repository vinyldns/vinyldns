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

package vinyldns.api.domain.batch

import cats.data.Validated.Valid
import cats.effect._
import cats.implicits._
import cats.scalatest.{EitherMatchers, ValidatedMatchers}
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterEach, EitherValues, Matchers, WordSpec}
import vinyldns.api.ValidatedBatchMatcherImprovements._
import vinyldns.api._
import vinyldns.api.domain.batch.BatchChangeInterfaces.{BatchResult, _}
import vinyldns.api.domain.batch.BatchTransformations._
import vinyldns.api.domain.{AccessValidations, _}
import vinyldns.api.repository.{
  EmptyGroupRepo,
  EmptyRecordSetRepo,
  EmptyZoneRepo,
  InMemoryBatchChangeRepository
}
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.batch.{
  BatchChange,
  BatchChangeInfo,
  SingleAddChange,
  SingleChangeStatus
}
import vinyldns.core.domain.membership.Group
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record.{RecordType, _}
import vinyldns.core.domain.zone.Zone

class BatchChangeServiceSpec
    extends WordSpec
    with Matchers
    with CatsHelpers
    with BeforeAndAfterEach
    with EitherMatchers
    with EitherValues
    with ValidatedMatchers {

  private val validations = new BatchChangeValidations(10, AccessValidations)

  private val apexAddA = AddChangeInput("apex.test.com.", RecordType.A, 100, AData("1.1.1.1"))
  private val nonApexAddA =
    AddChangeInput("non-apex.test.com.", RecordType.A, 100, AData("1.1.1.1"))
  private val onlyApexAddA =
    AddChangeInput("only.apex.exists.", RecordType.A, 100, AData("1.1.1.1"))
  private val onlyBaseAddAAAA =
    AddChangeInput("have.only.base.", RecordType.AAAA, 3600, AAAAData("1:2:3:4:5:6:7:8"))
  private val noZoneAddA = AddChangeInput("no.zone.match.", RecordType.A, 100, AData("1.1.1.1"))
  private val cnameAdd =
    AddChangeInput("cname.test.com.", RecordType.CNAME, 100, CNAMEData("testing.test.com."))
  private val cnameApexAdd =
    AddChangeInput("apex.test.com.", RecordType.CNAME, 100, CNAMEData("testing.test.com."))
  private val cnameReverseAdd = AddChangeInput(
    "cname.55.144.10.in-addr.arpa.",
    RecordType.CNAME,
    100,
    CNAMEData("testing.cname.com."))
  private val ptrAdd = AddChangeInput("10.144.55.11", RecordType.PTR, 100, PTRData("ptr"))
  private val ptrAdd2 = AddChangeInput("10.144.55.255", RecordType.PTR, 100, PTRData("ptr"))
  private val ptrDelegatedAdd = AddChangeInput("192.0.2.193", RecordType.PTR, 100, PTRData("ptr"))
  private val ptrV6Add =
    AddChangeInput("2001:0000:0000:0000:0000:ff00:0042:8329", RecordType.PTR, 100, PTRData("ptr"))

  private val authGrp = okGroup
  private val auth = okAuth
  private val notAuth = dummyAuth

  private val apexZone = Zone("apex.test.com.", "email", id = "apex", adminGroupId = authGrp.id)
  private val baseZone = Zone("test.com.", "email", id = "base", adminGroupId = authGrp.id)
  private val onlyApexZone = Zone("only.apex.exists.", "email", id = "onlyApex")
  private val onlyBaseZone = Zone("only.base.", "email", id = "onlyBase")
  private val ptrZone = Zone("55.144.10.in-addr.arpa.", "email", id = "nonDelegatedPTR")
  private val delegatedPTRZone = Zone("64/25.55.144.10.in-addr.arpa.", "email", id = "delegatedPTR")
  private val otherPTRZone = Zone("56.144.10.in-addr.arpa.", "email", id = "otherPTR")
  private val ipv6PTRZone = Zone("0.1.0.0.2.ip6.arpa.", "email", id = "ipv6PTR")

  private val apexAddForVal = AddChangeForValidation(apexZone, "apex.test.com.", apexAddA)
  private val nonApexAddForVal = AddChangeForValidation(baseZone, "non-apex", nonApexAddA)
  private val ptrAddForVal = AddChangeForValidation(ptrZone, "11", ptrAdd)
  private val ptrDelegatedAddForVal =
    AddChangeForValidation(delegatedPTRZone, "193", ptrDelegatedAdd)
  private val ptrV6AddForVal = AddChangeForValidation(
    ipv6PTRZone,
    "9.2.3.8.2.4.0.0.0.0.f.f.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0",
    ptrV6Add)

  private val pendingChange = SingleAddChange(
    "zoneid",
    "zonename",
    "rname",
    "inputname",
    RecordType.A,
    123,
    AData("2.2.2.2"),
    SingleChangeStatus.Pending,
    None,
    None,
    None)

  private val batchChangeRepo = new InMemoryBatchChangeRepository

  object EmptyBatchConverter extends BatchChangeConverterAlgebra {
    def sendBatchForProcessing(
        batchChange: BatchChange,
        existingZones: ExistingZones,
        existingRecordSets: ExistingRecordSets,
        ownerGroupId: Option[String]): BatchResult[BatchConversionOutput] =
      batchChange.comments match {
        case Some("conversionError") => BatchConversionError(pendingChange).toLeftBatchResult
        case _ => BatchConversionOutput(batchChange, List()).toRightBatchResult
      }
  }

  override protected def beforeEach(): Unit = batchChangeRepo.clear()

  private def makeRS(zoneId: String, name: String, typ: RecordType): RecordSet =
    RecordSet(zoneId, name, typ, 100, RecordSetStatus.Active, DateTime.now())

  private val existingApex: RecordSet =
    makeRS(apexAddForVal.zone.name, apexAddForVal.recordName, SOA)
  private val existingNonApex: RecordSet =
    makeRS(nonApexAddForVal.zone.name, nonApexAddForVal.recordName, TXT)
  private val existingPtr: RecordSet =
    makeRS(ptrAddForVal.zone.name, ptrAddForVal.recordName, PTR)
  private val existingPtrDelegated: RecordSet =
    makeRS(ptrDelegatedAddForVal.zone.name, ptrDelegatedAddForVal.recordName, PTR)
  private val existingPtrV6: RecordSet =
    makeRS(ptrV6AddForVal.zone.name, ptrV6AddForVal.recordName, PTR)

  object TestRecordSetRepo extends EmptyRecordSetRepo {
    val dbRecordSets: Set[(RecordSet, String)] =
      Set(
        (existingApex, "apex.test.com."),
        (existingNonApex, "non-apex.test.com."),
        (existingPtr, "11.55.144.10.in-addr.arpa."),
        (existingPtrDelegated, "193.64/25.55.144.10.in-addr.arpa."),
        (existingPtrV6, "9.2.3.8.2.4.0.0.0.0.f.f.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.1.0.0.2.ip6.arpa.")
      )

    override def getRecordSetsByName(zoneId: String, name: String): IO[List[RecordSet]] =
      IO.pure {
        (zoneId, name) match {
          case ("apex", "apex.test.com.") => List(existingApex)
          case ("base", "non-apex") => List(existingNonApex)
          case ("nonDelegatedPTR", "11") => List(existingPtr)
          case ("delegatedPTR", "193") => List(existingPtrDelegated)
          case ("ipv6PTR", "9.2.3.8.2.4.0.0.0.0.f.f.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0") =>
            List(existingPtrV6)
          case (_, _) => List()
        }
      }

    override def getRecordSetsByFQDNs(names: Set[String]): IO[List[RecordSet]] =
      IO.pure {
        dbRecordSets
          .filter {
            case (_, fqdn) =>
              names.contains(fqdn)
          }
          .map {
            case (rs, _) => rs
          }
          .toList
      }
  }

  object AlwaysExistsZoneRepo extends EmptyZoneRepo {
    override def getZonesByNames(zoneNames: Set[String]): IO[Set[Zone]] = {
      val zones = zoneNames.map(Zone(_, "test@test.com"))
      IO.pure(zones)
    }
  }

  object TestGroupRepo extends EmptyGroupRepo {
    override def getGroup(groupId: String): IO[Option[Group]] =
      IO.pure {
        groupId match {
          case okGroup.id => Some(okGroup)
          case authGrp.id => Some(authGrp)
          case "user-is-not-member" => Some(abcGroup)
          case _ => None
        }
      }

    override def getGroups(groupIds: Set[String]): IO[Set[Group]] =
      IO.pure {
        groupIds.flatMap {
          case okGroup.id => Some(okGroup)
          case authGrp.id => Some(authGrp)
          case _ => None
        }
      }
  }

  object TestZoneRepo extends EmptyZoneRepo {
    val dbZones: Set[Zone] =
      Set(apexZone, baseZone, onlyApexZone, onlyBaseZone, ptrZone, delegatedPTRZone, otherPTRZone)

    override def getZonesByNames(zoneNames: Set[String]): IO[Set[Zone]] =
      IO.pure(dbZones.filter(zn => zoneNames.contains(zn.name)))

    override def getZonesByFilters(zoneNames: Set[String]): IO[Set[Zone]] =
      IO.pure(dbZones.filter(z => zoneNames.exists(z.name.endsWith)))
  }

  private val underTest = new BatchChangeService(
    TestZoneRepo,
    TestRecordSetRepo,
    TestGroupRepo,
    validations,
    batchChangeRepo,
    EmptyBatchConverter)

  "applyBatchChange" should {
    "succeed if all inputs are good" in {
      val input = BatchChangeInput(None, List(apexAddA, nonApexAddA))

      val result = rightResultOf(underTest.applyBatchChange(input, auth).value)

      result.changes.length shouldBe 2
    }

    "fail if conversion cannot process" in {
      val input = BatchChangeInput(Some("conversionError"), List(apexAddA, nonApexAddA))
      val result = leftResultOf(underTest.applyBatchChange(input, auth).value)

      result shouldBe an[BatchConversionError]
    }

    "fail with GroupDoesNotExist if owner group ID is provided for a non-existent group" in {
      val ownerGroupId = "non-existent-group-id"
      val input = BatchChangeInput(None, List(apexAddA), Some(ownerGroupId))
      val result = leftResultOf(underTest.applyBatchChange(input, auth).value)

      result shouldBe InvalidBatchChangeInput(List(GroupDoesNotExist(ownerGroupId)))
    }

    "fail with UserDoesNotBelongToOwnerGroup if normal user does not belong to group specified by owner group ID" in {
      val ownerGroupId = "user-is-not-member"
      val input = BatchChangeInput(None, List(apexAddA), Some(ownerGroupId))
      val result = leftResultOf(underTest.applyBatchChange(input, notAuth).value)

      result shouldBe
        InvalidBatchChangeInput(
          List(NotAMemberOfOwnerGroup(ownerGroupId, notAuth.signedInUser.userName)))
    }

    "succeed if owner group ID is provided and user is a member of the group" in {
      val input = BatchChangeInput(None, List(apexAddA), Some(okGroup.id))
      val result = rightResultOf(underTest.applyBatchChange(input, okAuth).value)

      result.changes.length shouldBe 1
    }

    "succeed if owner group ID is provided and user is a super user" in {
      val ownerGroupId = Some("user-is-not-member")
      val input = BatchChangeInput(None, List(apexAddA), ownerGroupId)
      val result =
        rightResultOf(underTest.applyBatchChange(input, AuthPrincipal(superUser, Seq())).value)

      result.changes.length shouldBe 1
    }
  }

  "getBatchChange" should {
    "Succeed if batchChange id exists" in {
      val batchChange =
        BatchChange(auth.userId, auth.signedInUser.userName, None, DateTime.now, List())
      batchChangeRepo.save(batchChange)

      val result = rightResultOf(underTest.getBatchChange(batchChange.id, auth).value)

      result shouldBe BatchChangeInfo(batchChange)
    }

    "Fail if batchChange id does not exist" in {
      val result = leftResultOf(underTest.getBatchChange("badId", auth).value)

      result shouldBe BatchChangeNotFound("badId")
    }

    "Fail if user did not create the batch change" in {
      val batchChange = BatchChange("badID", "badUN", None, DateTime.now, List())
      batchChangeRepo.save(batchChange)

      val result = leftResultOf(underTest.getBatchChange(batchChange.id, notAuth).value)

      result shouldBe UserNotAuthorizedError(batchChange.id)
    }

    "Succeed if user is a super user" in {
      val batchChange = BatchChange("badID", "badUN", None, DateTime.now, List())
      batchChangeRepo.save(batchChange)

      val authSuper = notAuth.copy(signedInUser = notAuth.signedInUser.copy(isSuper = true))

      val result = rightResultOf(underTest.getBatchChange(batchChange.id, authSuper).value)

      result shouldBe BatchChangeInfo(batchChange)
    }

    "Succeed if user is a support user" in {
      val batchChange = BatchChange("badID", "badUN", None, DateTime.now, List())
      batchChangeRepo.save(batchChange)

      val authSuper = notAuth.copy(signedInUser = notAuth.signedInUser.copy(isSupport = true))

      val result = rightResultOf(underTest.getBatchChange(batchChange.id, authSuper).value)

      result shouldBe BatchChangeInfo(batchChange)
    }

    "Succeed with record owner group name in result" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          ownerGroupId = Some(okGroup.id))
      batchChangeRepo.save(batchChange)

      val result = rightResultOf(underTest.getBatchChange(batchChange.id, auth).value)
      result shouldBe BatchChangeInfo(batchChange, Some(okGroup.name))
    }

    "Succeed if record owner group name is not found" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          ownerGroupId = Some("no-existo"))
      batchChangeRepo.save(batchChange)

      val result = rightResultOf(underTest.getBatchChange(batchChange.id, auth).value)
      result shouldBe BatchChangeInfo(batchChange)
    }
  }

  "getExistingRecordSets" should {
    val error = InvalidTTL(0).invalidNel

    "combine gets for each valid record" in {
      val in = List(
        apexAddForVal.validNel,
        nonApexAddForVal.validNel,
        ptrAddForVal.validNel,
        ptrDelegatedAddForVal.validNel,
        ptrV6AddForVal.validNel,
        error)
      val result = await(underTest.getExistingRecordSets(in))

      val expected =
        List(existingApex, existingNonApex, existingPtr, existingPtrDelegated, existingPtrV6)
      result.recordSets should contain theSameElementsAs expected
    }
    "not fail if gets all lefts" in {
      val errors = List(error)
      val result = await(underTest.getExistingRecordSets(errors))

      result.recordSets.length shouldBe 0
    }
  }

  "getZonesForRequest" should {
    "return names for the apex and base zones if they both exist" in {
      val underTestBaseApexZoneList: ExistingZones =
        await(underTest.getZonesForRequest(List(apexAddA.validNel)))

      (underTestBaseApexZoneList.zones should contain).allOf(apexZone, baseZone)
    }

    "return only the apex zone if only the apex zone exists or A or AAAA records" in {
      val underTestOnlyApexZoneList: ExistingZones =
        await(underTest.getZonesForRequest(List(onlyApexAddA.validNel)))

      (underTestOnlyApexZoneList.zones should contain).only(onlyApexZone)
    }

    "return only the base zone if only the base zone exists" in {
      val underTestOnlyBaseZoneList: ExistingZones =
        await(underTest.getZonesForRequest(List(onlyBaseAddAAAA.validNel)))

      (underTestOnlyBaseZoneList.zones should contain).only(onlyBaseZone)
    }

    "return no zones if neither the apex nor base zone exist" in {
      val underTestOnlyNoZonesList: ExistingZones =
        await(underTest.getZonesForRequest(List(noZoneAddA.validNel)))

      underTestOnlyNoZonesList.zones shouldBe Set()
    }

    "return all possible zones given an IPv4 PTR" in {
      val underTestPTRZonesList: ExistingZones =
        await(underTest.getZonesForRequest(List(ptrAdd.validNel)))

      (underTestPTRZonesList.zones should contain).allOf(ptrZone, delegatedPTRZone)
    }

    "return all possible zones given an IPv6 PTR (full form)" in {
      // returning all zones to validate we are searching for the right items
      val underTest = new BatchChangeService(
        AlwaysExistsZoneRepo,
        TestRecordSetRepo,
        TestGroupRepo,
        validations,
        batchChangeRepo,
        EmptyBatchConverter)

      val ip = "2001:0db8:0000:0000:0000:ff00:0042:8329"
      val possibleZones = List(
        "0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "8.b.d.0.1.0.0.2.ip6.arpa.",
        "b.d.0.1.0.0.2.ip6.arpa.",
        "d.0.1.0.0.2.ip6.arpa.",
        "0.1.0.0.2.ip6.arpa."
      )

      val ptr = AddChangeInput(ip, RecordType.PTR, 100, PTRData("ptr.")).validNel
      val underTestPTRZonesList: ExistingZones = await(underTest.getZonesForRequest(List(ptr)))

      val zoneNames = underTestPTRZonesList.zones.map(_.name)
      zoneNames should contain theSameElementsAs possibleZones
    }

    "return all possible zones given short form IPv6 PTRs" in {
      // returning all zones to validate we are searching for the right items
      val underTest = new BatchChangeService(
        AlwaysExistsZoneRepo,
        TestRecordSetRepo,
        TestGroupRepo,
        validations,
        batchChangeRepo,
        EmptyBatchConverter)

      val ip1 = "::1"
      val possibleZones1 = (5 to 16).map(num0s => ("0." * num0s) + "ip6.arpa.")

      // these are both the same as 2001:0db8:0000:0000:0000:ff00:0042:8329
      val ip2s = List("2001:db8:0:0:0:ff00:42:8329", "2001:db8::ff00:42:8329")
      val possibleZones2 = List(
        "0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "0.8.b.d.0.1.0.0.2.ip6.arpa.",
        "8.b.d.0.1.0.0.2.ip6.arpa.",
        "b.d.0.1.0.0.2.ip6.arpa.",
        "d.0.1.0.0.2.ip6.arpa.",
        "0.1.0.0.2.ip6.arpa."
      )

      val ips = ip1 :: ip2s
      val ptrs = ips.map { v6Name =>
        AddChangeInput(v6Name, RecordType.PTR, 100, PTRData("ptr.")).validNel
      }

      val underTestPTRZonesList: ExistingZones = await(underTest.getZonesForRequest(ptrs))

      val zoneNames = underTestPTRZonesList.zones.map(_.name)
      zoneNames should contain theSameElementsAs (possibleZones1 ++ possibleZones2)
    }

    "return a set of distinct zones, given duplicates" in {
      val underTestDistinctZonesList: ExistingZones =
        await(underTest.getZonesForRequest(List(cnameReverseAdd.validNel, ptrAdd.validNel)))

      underTestDistinctZonesList.zones.count(_.id == "nonDelegatedPTR") shouldBe 1
    }
  }

  "zoneDiscovery" should {
    "map the batch change input to the apex zone if both the apex and base zones exist for A records" in {
      val result =
        underTest.zoneDiscovery(List(apexAddA.validNel), ExistingZones(Set(apexZone, baseZone)))

      result should containChangeForValidation(apexAddForVal)
    }

    "map the batch change input to the apex zone if only the apex zone exists for A records" in {
      val result =
        underTest.zoneDiscovery(List(onlyApexAddA.validNel), ExistingZones(Set(onlyApexZone)))

      result should containChangeForValidation(
        AddChangeForValidation(onlyApexZone, "only.apex.exists.", onlyApexAddA))
    }

    "map the batch change input to the base zone if only the base zone exists for A records" in {
      val result =
        underTest.zoneDiscovery(List(onlyBaseAddAAAA.validNel), ExistingZones(Set(onlyBaseZone)))

      result should containChangeForValidation(
        AddChangeForValidation(onlyBaseZone, "have", onlyBaseAddAAAA))
    }

    "map the batch change input to the base zone only for CNAME records" in {
      val result =
        underTest.zoneDiscovery(List(cnameAdd.validNel), ExistingZones(Set(apexZone, baseZone)))

      result should containChangeForValidation(AddChangeForValidation(baseZone, "cname", cnameAdd))
    }

    "return an error if an apex zone is found for CNAME records" in {
      val result =
        underTest.zoneDiscovery(List(cnameApexAdd.validNel), ExistingZones(Set(apexZone)))

      result.head should haveInvalid[DomainValidationError](RecordAlreadyExists("apex.test.com."))
    }

    "return an error if no base zone is found for CNAME records" in {
      val result = underTest.zoneDiscovery(List(cnameAdd.validNel), ExistingZones(Set(apexZone)))

      result.head should haveInvalid[DomainValidationError](ZoneDiscoveryError("cname.test.com."))
    }

    "return an error if no zone is found through zone discovery" in {
      val result = underTest.zoneDiscovery(List(noZoneAddA.validNel), ExistingZones(Set()))

      result.head should haveInvalid[DomainValidationError](ZoneDiscoveryError("no.zone.match."))
    }

    "handle mapping a combination of zone discovery successes and failures" in {
      val result = underTest.zoneDiscovery(
        List(apexAddA.validNel, onlyApexAddA.validNel, onlyBaseAddAAAA.validNel, cnameAdd.validNel),
        ExistingZones(Set(apexZone, baseZone, onlyBaseZone)))

      result.head should beValid[ChangeForValidation](apexAddForVal)
      result(1) should haveInvalid[DomainValidationError](ZoneDiscoveryError("only.apex.exists."))
      result(2) should beValid[ChangeForValidation](
        AddChangeForValidation(onlyBaseZone, "have", onlyBaseAddAAAA))
      result(3) should beValid[ChangeForValidation](
        AddChangeForValidation(baseZone, "cname", cnameAdd))
    }

    "map the batch change input to the delegated PTR zone for PTR records (ipv4)" in {
      val result = underTest.zoneDiscovery(
        List(ptrAdd.validNel),
        ExistingZones(Set(delegatedPTRZone, ptrZone)))

      result should containChangeForValidation(
        AddChangeForValidation(delegatedPTRZone, "11", ptrAdd))
    }

    "map the batch change input to the non delegated PTR zone for PTR records (ipv4)" in {
      val result = underTest.zoneDiscovery(
        List(ptrAdd2.validNel),
        ExistingZones(Set(delegatedPTRZone, ptrZone)))

      result should containChangeForValidation(AddChangeForValidation(ptrZone, "255", ptrAdd2))
    }

    "return an error if no zone is found for PTR records (ipv4)" in {
      val result = underTest.zoneDiscovery(List(ptrAdd.validNel), ExistingZones(Set(apexZone)))

      result.head should haveInvalid[DomainValidationError](ZoneDiscoveryError("10.144.55.11"))
    }

    "return an error for PTR if there are zone matches for the IP but no match on the record name" in {
      val result = underTest.zoneDiscovery(
        List(ptrAdd.validNel),
        ExistingZones(Set(delegatedPTRZone.copy(name = "192/30.55.144.10.in-addr.arpa."))))

      result.head should haveInvalid[DomainValidationError](ZoneDiscoveryError(ptrAdd.inputName))
    }

    "map the batch change input to the delegated PTR zone for PTR records (ipv6)" in {
      val ptrv6ZoneSmall = Zone("0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.", "email", id = "ptrv6small")
      val ptrv6ZoneMed = Zone("0.8.b.d.0.1.0.0.2.ip6.arpa.", "email", id = "ptrv6med")
      val ptrv6ZoneBig = Zone("0.1.0.0.2.ip6.arpa.", "email", id = "ptrv6big")

      val smallZoneAdd =
        AddChangeInput("2001:db8::ff00:42:8329", RecordType.PTR, 100, PTRData("ptr"))
      val medZoneAdd = AddChangeInput(
        "2001:0db8:0111:0000:0000:ff00:0042:8329",
        RecordType.PTR,
        100,
        PTRData("ptr"))
      val bigZoneAdd = AddChangeInput(
        "2001:0000:0000:0000:0000:ff00:0042:8329",
        RecordType.PTR,
        100,
        PTRData("ptr"))
      val notFoundZoneAdd = AddChangeInput("::1", RecordType.PTR, 100, PTRData("ptr"))

      val ptripv6Adds = List(
        smallZoneAdd.validNel,
        medZoneAdd.validNel,
        bigZoneAdd.validNel,
        notFoundZoneAdd.validNel)

      val result = underTest.zoneDiscovery(
        ptripv6Adds,
        ExistingZones(Set(ptrv6ZoneSmall, ptrv6ZoneMed, ptrv6ZoneBig)))

      result should containChangeForValidation(
        AddChangeForValidation(
          ptrv6ZoneSmall,
          "9.2.3.8.2.4.0.0.0.0.f.f.0.0.0.0.0.0.0.0",
          smallZoneAdd))
      result should containChangeForValidation(
        AddChangeForValidation(
          ptrv6ZoneMed,
          "9.2.3.8.2.4.0.0.0.0.f.f.0.0.0.0.0.0.0.0.1.1.1",
          medZoneAdd))
      result should containChangeForValidation(
        AddChangeForValidation(
          ptrv6ZoneBig,
          "9.2.3.8.2.4.0.0.0.0.f.f.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0",
          bigZoneAdd))
      result(3) should haveInvalid[DomainValidationError](
        ZoneDiscoveryError(notFoundZoneAdd.inputName))
    }
  }

  "buildResponse" should {
    "return a BatchChange if all data inputs are valid" in {
      val result = underTest
        .buildResponse(
          BatchChangeInput(None, List(apexAddA, onlyBaseAddAAAA, cnameAdd)),
          List(
            AddChangeForValidation(apexZone, "apex.test.com.", apexAddA).validNel,
            AddChangeForValidation(onlyBaseZone, "have", onlyBaseAddAAAA).validNel,
            AddChangeForValidation(baseZone, "cname", cnameAdd).validNel
          ),
          okAuth
        )
        .toOption
        .get

      result shouldBe a[BatchChange]
      result.changes.head shouldBe SingleAddChange(
        apexZone.id,
        apexZone.name,
        "apex.test.com.",
        "apex.test.com.",
        A,
        100,
        AData("1.1.1.1"),
        SingleChangeStatus.Pending,
        None,
        None,
        None,
        result.changes.head.id)
      result.changes(1) shouldBe SingleAddChange(
        onlyBaseZone.id,
        onlyBaseZone.name,
        "have",
        "have.only.base.",
        AAAA,
        3600,
        AAAAData("1:2:3:4:5:6:7:8"),
        SingleChangeStatus.Pending,
        None,
        None,
        None,
        result.changes(1).id
      )
      result.changes(2) shouldBe SingleAddChange(
        baseZone.id,
        baseZone.name,
        "cname",
        "cname.test.com.",
        CNAME,
        100,
        CNAMEData("testing.test.com."),
        SingleChangeStatus.Pending,
        None,
        None,
        None,
        result.changes(2).id
      )
    }

    "return a BatchChangeErrorList if any data inputs are invalid" in {
      val result = underTest
        .buildResponse(
          BatchChangeInput(None, List(noZoneAddA, nonApexAddA)),
          List(
            ZoneDiscoveryError("no.zone.match.").invalidNel,
            AddChangeForValidation(baseZone, "non-apex", nonApexAddA).validNel),
          okAuth
        )
        .left
        .value

      result shouldBe an[InvalidBatchChangeResponses]
      val ibcr = result.asInstanceOf[InvalidBatchChangeResponses]
      ibcr.changeRequestResponses.head should haveInvalid[DomainValidationError](
        ZoneDiscoveryError("no.zone.match."))
      ibcr.changeRequestResponses(1) shouldBe Valid(
        AddChangeForValidation(baseZone, "non-apex", nonApexAddA))
    }
  }

  "listBatchChangeSummaries" should {
    "return a list of batchChangeSummaries if one exists" in {
      val batchChange =
        BatchChange(auth.userId, auth.signedInUser.userName, None, DateTime.now, List())
      batchChangeRepo.save(batchChange)

      val result = rightResultOf(underTest.listBatchChangeSummaries(auth, maxItems = 100).value)

      result.maxItems shouldBe 100
      result.nextId shouldBe None
      result.startFrom shouldBe None

      result.batchChanges.length shouldBe 1
      result.batchChanges(0).createdTimestamp shouldBe batchChange.createdTimestamp
    }

    "return a list of batchChangeSummaries if some exist" in {
      val batchChangeOne =
        BatchChange(auth.userId, auth.signedInUser.userName, None, DateTime.now, List())
      batchChangeRepo.save(batchChangeOne)

      val batchChangeTwo = BatchChange(
        auth.userId,
        auth.signedInUser.userName,
        None,
        new DateTime(DateTime.now.getMillis + 1000),
        List())
      batchChangeRepo.save(batchChangeTwo)

      val result = rightResultOf(underTest.listBatchChangeSummaries(auth, maxItems = 100).value)

      result.maxItems shouldBe 100
      result.nextId shouldBe None
      result.startFrom shouldBe None

      result.batchChanges.length shouldBe 2
      result.batchChanges(0).createdTimestamp shouldBe batchChangeTwo.createdTimestamp
      result.batchChanges(1).createdTimestamp shouldBe batchChangeOne.createdTimestamp
    }

    "return a limited list of batchChangeSummaries if some exist" in {
      val batchChangeOne =
        BatchChange(auth.userId, auth.signedInUser.userName, None, DateTime.now, List())
      batchChangeRepo.save(batchChangeOne)

      val batchChangeTwo = BatchChange(
        auth.userId,
        auth.signedInUser.userName,
        None,
        new DateTime(DateTime.now.getMillis + 1000),
        List())
      batchChangeRepo.save(batchChangeTwo)

      val result = rightResultOf(underTest.listBatchChangeSummaries(auth, maxItems = 1).value)

      result.maxItems shouldBe 1
      result.nextId shouldBe Some(1)
      result.startFrom shouldBe None

      result.batchChanges.length shouldBe 1
      result.batchChanges(0).createdTimestamp shouldBe batchChangeTwo.createdTimestamp
    }

    "return an offset list of batchChangeSummaries if some exist" in {
      val batchChangeOne =
        BatchChange(auth.userId, auth.signedInUser.userName, None, DateTime.now, List())
      batchChangeRepo.save(batchChangeOne)

      val batchChangeTwo = BatchChange(
        auth.userId,
        auth.signedInUser.userName,
        None,
        new DateTime(DateTime.now.getMillis + 1000),
        List())
      batchChangeRepo.save(batchChangeTwo)

      val result =
        rightResultOf(underTest.listBatchChangeSummaries(auth, startFrom = Some(1)).value)

      result.maxItems shouldBe 100
      result.nextId shouldBe None
      result.startFrom shouldBe Some(1)

      result.batchChanges.length shouldBe 1
      result.batchChanges(0).createdTimestamp shouldBe batchChangeOne.createdTimestamp
    }

    "only return summaries associated with user who called" in {
      val batchChangeUserOne =
        BatchChange(auth.userId, auth.signedInUser.userName, None, DateTime.now, List())
      batchChangeRepo.save(batchChangeUserOne)

      val batchChangeUserTwo = BatchChange(
        notAuth.userId,
        auth.signedInUser.userName,
        None,
        new DateTime(DateTime.now.getMillis + 1000),
        List())
      batchChangeRepo.save(batchChangeUserTwo)

      val result =
        rightResultOf(underTest.listBatchChangeSummaries(auth, maxItems = 100).value).batchChanges

      result.length shouldBe 1
      result(0).createdTimestamp shouldBe batchChangeUserOne.createdTimestamp
    }

    "return an empty list of batchChangeSummaries if none exist" in {
      val result =
        rightResultOf(underTest.listBatchChangeSummaries(auth, maxItems = 100).value).batchChanges

      result.length shouldBe 0
    }

    "return ownerGroupName in batchChangeSummaries" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          ownerGroupId = Some(okGroup.id))
      batchChangeRepo.save(batchChange)

      val result = rightResultOf(underTest.listBatchChangeSummaries(auth, maxItems = 100).value)

      result.maxItems shouldBe 100
      result.nextId shouldBe None
      result.startFrom shouldBe None

      result.batchChanges.length shouldBe 1
      result.batchChanges(0).createdTimestamp shouldBe batchChange.createdTimestamp
      result.batchChanges(0).ownerGroupId shouldBe Some(okGroup.id)
      result.batchChanges(0).ownerGroupName shouldBe Some(okGroup.name)
    }

    "return None for ownerGroupName in batchChangeSummaries if group not found" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          ownerGroupId = Some("no-existo"))
      batchChangeRepo.save(batchChange)

      val result = rightResultOf(underTest.listBatchChangeSummaries(auth, maxItems = 100).value)

      result.maxItems shouldBe 100
      result.nextId shouldBe None
      result.startFrom shouldBe None

      result.batchChanges.length shouldBe 1
      result.batchChanges(0).createdTimestamp shouldBe batchChange.createdTimestamp
      result.batchChanges(0).ownerGroupId shouldBe Some("no-existo")
      result.batchChanges(0).ownerGroupName shouldBe None
    }
  }

  "getOwnerGroup" should {
    "return None if owner group ID is None" in {
      rightResultOf(underTest.getOwnerGroup(None).value) shouldBe None
    }

    "return None if group does not exist for owner group ID" in {
      rightResultOf(underTest.getOwnerGroup(Some("non-existent-group-id")).value) shouldBe None
    }

    "return the group if the group exists for the owner group ID" in {
      rightResultOf(underTest.getOwnerGroup(Some(okGroup.id)).value) shouldBe Some(okGroup)
    }
  }
}
