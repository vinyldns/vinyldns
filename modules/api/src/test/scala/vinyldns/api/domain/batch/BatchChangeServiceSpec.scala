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
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, EitherValues, Matchers, WordSpec}
import vinyldns.api.ValidatedBatchMatcherImprovements.containChangeForValidation
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
import vinyldns.core
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.batch._
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
    with ValidatedMatchers
    with MockitoSugar {

  private val nonFatalError = mock[DomainValidationError]
  doReturn(false).when(nonFatalError).isFatal

  private val validations = new BatchChangeValidations(10, AccessValidations)
  private val ttl = Some(200L)

  private val apexAddA = AddChangeInput("apex.test.com", RecordType.A, ttl, AData("1.1.1.1"))
  private val nonApexAddA =
    AddChangeInput("non-apex.test.com", RecordType.A, ttl, AData("1.1.1.1"))
  private val onlyApexAddA =
    AddChangeInput("only.apex.exists", RecordType.A, ttl, AData("1.1.1.1"))
  private val onlyBaseAddAAAA =
    AddChangeInput("have.only.base", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8"))
  private val noZoneAddA = AddChangeInput("no.zone.match.", RecordType.A, ttl, AData("1.1.1.1"))
  private val dottedAddA =
    AddChangeInput("dot.ted.apex.test.com", RecordType.A, ttl, AData("1.1.1.1"))
  private val cnameAdd =
    AddChangeInput("cname.test.com", RecordType.CNAME, ttl, CNAMEData("testing.test.com."))
  private val cnameApexAdd =
    AddChangeInput("apex.test.com", RecordType.CNAME, ttl, CNAMEData("testing.test.com."))
  private val cnameReverseAdd = AddChangeInput(
    "cname.55.144.10.in-addr.arpa",
    RecordType.CNAME,
    ttl,
    CNAMEData("testing.cname.com."))
  private val ptrAdd = AddChangeInput("10.144.55.11", RecordType.PTR, ttl, PTRData("ptr"))
  private val ptrAdd2 = AddChangeInput("10.144.55.255", RecordType.PTR, ttl, PTRData("ptr"))
  private val ptrDelegatedAdd = AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData("ptr"))
  private val ptrV6Add =
    AddChangeInput("2001:0000:0000:0000:0000:ff00:0042:8329", RecordType.PTR, ttl, PTRData("ptr"))

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
    Some("zoneid"),
    Some("zonename"),
    Some("rname"),
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
        case Some("checkConverter") =>
          // hacking reviewComment to determine if things were sent to the converter
          BatchConversionOutput(
            batchChange.copy(reviewComment = Some("batchSentToConverter")),
            List()).toRightBatchResult
        case _ => BatchConversionOutput(batchChange, List()).toRightBatchResult
      }
  }

  override protected def beforeEach(): Unit = batchChangeRepo.clear()

  private def makeRS(zoneId: String, name: String, typ: RecordType): RecordSet =
    RecordSet(zoneId, name, typ, 100, RecordSetStatus.Active, DateTime.now())

  private val existingApex: RecordSet =
    makeRS(apexAddForVal.zone.id, apexAddForVal.recordName, SOA)
  private val existingNonApex: RecordSet =
    makeRS(nonApexAddForVal.zone.id, nonApexAddForVal.recordName, TXT)
  private val existingPtr: RecordSet =
    makeRS(ptrAddForVal.zone.id, ptrAddForVal.recordName, PTR)
  private val existingPtrDelegated: RecordSet =
    makeRS(ptrDelegatedAddForVal.zone.id, ptrDelegatedAddForVal.recordName, PTR)
  private val existingPtrV6: RecordSet =
    makeRS(ptrV6AddForVal.zone.id, ptrV6AddForVal.recordName, PTR)
  private val deletedZoneApex: RecordSet =
    makeRS("deletedZone", apexAddForVal.recordName, SOA)

  object TestRecordSetRepo extends EmptyRecordSetRepo {
    val dbRecordSets: Set[(RecordSet, String)] =
      Set(
        (existingApex, "apex.test.com."),
        (existingNonApex, "non-apex.test.com."),
        (existingPtr, "11.55.144.10.in-addr.arpa."),
        (existingPtrDelegated, "193.64/25.55.144.10.in-addr.arpa."),
        (
          existingPtrV6,
          "9.2.3.8.2.4.0.0.0.0.f.f.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.1.0.0.2.ip6.arpa."),
        (deletedZoneApex, "apex.test.com.")
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
    EmptyBatchConverter,
    false)

  private val underTestManualEnabled = new BatchChangeService(
    TestZoneRepo,
    TestRecordSetRepo,
    TestGroupRepo,
    validations,
    batchChangeRepo,
    EmptyBatchConverter,
    true)

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
        rightResultOf(
          underTest
            .applyBatchChange(input, AuthPrincipal(superUser, Seq(baseZone.adminGroupId)))
            .value)

      result.changes.length shouldBe 1
    }

    "succeed with excluded TTL" in {
      val noTtl = AddChangeInput("no-ttl-add.test.com", RecordType.A, None, AData("1.1.1.1"))
      val withTtl =
        AddChangeInput("with-ttl-add-2.test.com", RecordType.A, Some(900), AData("1.1.1.1"))
      val noTtlDel = DeleteChangeInput("non-apex.test.com.", RecordType.TXT)
      val noTtlUpdate =
        AddChangeInput("non-apex.test.com.", RecordType.TXT, None, TXTData("hello"))

      val input = BatchChangeInput(None, List(noTtl, withTtl, noTtlDel, noTtlUpdate))
      val result = rightResultOf(underTest.applyBatchChange(input, auth).value)

      result.changes.length shouldBe 4
      result.changes(0).asInstanceOf[SingleAddChange].ttl shouldBe VinylDNSConfig.defaultTtl
      result.changes(1).asInstanceOf[SingleAddChange].ttl shouldBe 900
      result.changes(3).asInstanceOf[SingleAddChange].ttl shouldBe existingApex.ttl
    }
  }

  "rejectBatchChange" should {
    "succeed if the batchChange is PendingApproval and reviewer is authorized" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(pendingChange),
          approvalStatus = BatchChangeApprovalStatus.PendingApproval)
      batchChangeRepo.save(batchChange)

      val result =
        rightResultOf(
          underTest
            .rejectBatchChange(
              batchChange.id,
              supportUserAuth,
              Some(RejectBatchChangeInput(Some("review comment"))))
            .value)

      result.status shouldBe BatchChangeStatus.Failed
      result.approvalStatus shouldBe BatchChangeApprovalStatus.ManuallyRejected
      result.changes.foreach(_.status shouldBe SingleChangeStatus.Rejected)
      result.reviewComment shouldBe Some("review comment")
      result.reviewerId shouldBe Some(supportUserAuth.userId)
      result.reviewTimestamp should not be None
    }

    "fail if the batchChange is not PendingApproval" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChange)

      val result =
        leftResultOf(underTest.rejectBatchChange(batchChange.id, supportUserAuth, None).value)

      result shouldBe BatchChangeNotPendingApproval(batchChange.id)
    }

    "fail if the batchChange reviewer is not authorized" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.PendingApproval)
      batchChangeRepo.save(batchChange)

      val result =
        leftResultOf(underTest.rejectBatchChange(batchChange.id, auth, None).value)

      result shouldBe UserNotAuthorizedError(batchChange.id)
    }

    "fail if the batchChange reviewer is not authorized and the batchChange is not Pending Approval" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChange)

      val result =
        leftResultOf(underTest.rejectBatchChange(batchChange.id, auth, None).value)

      result shouldBe UserNotAuthorizedError(batchChange.id)
    }
  }

  "approveBatchChange" should {
    "succeed if the batchChange is PendingApproval and reviewer is authorized" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.PendingApproval)
      batchChangeRepo.save(batchChange)

      val result =
        rightResultOf(underTest.approveBatchChange(batchChange.id, supportUserAuth, None).value)

      result shouldBe batchChange
    }

    "fail if the batchChange is not PendingApproval" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChange)

      val result =
        leftResultOf(underTest.approveBatchChange(batchChange.id, supportUserAuth, None).value)

      result shouldBe BatchChangeNotPendingApproval(batchChange.id)
    }

    "fail if the batchChange reviewer is not authorized" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.PendingApproval)
      batchChangeRepo.save(batchChange)

      val result =
        leftResultOf(underTest.approveBatchChange(batchChange.id, auth, None).value)

      result shouldBe UserNotAuthorizedError(batchChange.id)
    }

    "fail if the batchChange reviewer is not authorized and the batchChange is not Pending Approval" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChange)

      val result =
        leftResultOf(underTest.approveBatchChange(batchChange.id, auth, None).value)

      result shouldBe UserNotAuthorizedError(batchChange.id)
    }
  }

  "getBatchChange" should {
    "Succeed if batchChange id exists" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChange)

      val result = rightResultOf(underTest.getBatchChange(batchChange.id, auth).value)

      result shouldBe BatchChangeInfo(batchChange)
    }

    "Fail if batchChange id does not exist" in {
      val result = leftResultOf(underTest.getBatchChange("badId", auth).value)

      result shouldBe BatchChangeNotFound("badId")
    }

    "Fail if user did not create the batch change" in {
      val batchChange = BatchChange(
        "badID",
        "badUN",
        None,
        DateTime.now,
        List(),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChange)

      val result = leftResultOf(underTest.getBatchChange(batchChange.id, notAuth).value)

      result shouldBe UserNotAuthorizedError(batchChange.id)
    }

    "Succeed if user is a super user" in {
      val batchChange = BatchChange(
        "badID",
        "badUN",
        None,
        DateTime.now,
        List(),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChange)

      val authSuper = notAuth.copy(signedInUser = notAuth.signedInUser.copy(isSuper = true))

      val result = rightResultOf(underTest.getBatchChange(batchChange.id, authSuper).value)

      result shouldBe BatchChangeInfo(batchChange)
    }

    "Succeed if user is a support user" in {
      val batchChange = BatchChange(
        "badID",
        "badUN",
        None,
        DateTime.now,
        List(),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved)
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
          ownerGroupId = Some(okGroup.id),
          BatchChangeApprovalStatus.AutoApproved)
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
          ownerGroupId = Some("no-existo"),
          BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChange)

      val result = rightResultOf(underTest.getBatchChange(batchChange.id, auth).value)
      result shouldBe BatchChangeInfo(batchChange)
    }
  }

  "getExistingRecordSets" should {
    val error =
      InvalidTTL(0, DomainValidations.TTL_MIN_LENGTH, DomainValidations.TTL_MAX_LENGTH).invalidNel

    "combine gets for each valid record" in {
      val in = List(
        apexAddForVal.validNel,
        nonApexAddForVal.validNel,
        ptrAddForVal.validNel,
        ptrDelegatedAddForVal.validNel,
        ptrV6AddForVal.validNel,
        error)
      val zoneMap = ExistingZones(Set(apexZone, baseZone, ptrZone, delegatedPTRZone, ipv6PTRZone))
      val result = await(underTest.getExistingRecordSets(in, zoneMap))

      val expected =
        List(existingApex, existingNonApex, existingPtr, existingPtrDelegated, existingPtrV6)
      result.recordSets should contain theSameElementsAs expected
    }

    "combine gets for each valid record with existing zone" in {
      val in = List(
        apexAddForVal.validNel,
        nonApexAddForVal.validNel,
        ptrAddForVal.validNel,
        ptrDelegatedAddForVal.validNel,
        ptrV6AddForVal.validNel,
        error)
      val zoneMap = ExistingZones(Set(apexZone, baseZone, ptrZone, ipv6PTRZone))
      val result = await(underTest.getExistingRecordSets(in, zoneMap))

      val expected =
        List(existingApex, existingNonApex, existingPtr, existingPtrV6)
      result.recordSets should contain theSameElementsAs expected
    }

    "not fail if gets all lefts" in {
      val errors = List(error)
      val zoneMap = ExistingZones(Set(apexZone, baseZone, ptrZone, delegatedPTRZone, ipv6PTRZone))
      val result = await(underTest.getExistingRecordSets(errors, zoneMap))

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

    "return all possible zones for a dotted host" in {
      val underTestZonesList: ExistingZones =
        await(underTest.getZonesForRequest(List(dottedAddA.validNel)))

      (underTestZonesList.zones should contain).allOf(apexZone, baseZone)
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
        EmptyBatchConverter,
        false)

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

      val ptr = AddChangeInput(ip, RecordType.PTR, ttl, PTRData("ptr.")).validNel
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
        EmptyBatchConverter,
        false)

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
        AddChangeInput(v6Name, RecordType.PTR, ttl, PTRData("ptr.")).validNel
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

    "properly discover records in forward zones" in {
      val apex = apexZone.name

      val aApex = AddChangeInput(apex, RecordType.A, ttl, AData("1.2.3.4"))
      val aNormal = AddChangeInput(s"record.$apex", RecordType.A, ttl, AData("1.2.3.4"))
      val aDotted =
        AddChangeInput(s"some.dotted.record.$apex", RecordType.A, ttl, AData("1.2.3.4"))

      val expected = List(
        AddChangeForValidation(apexZone, apex, aApex),
        AddChangeForValidation(apexZone, "record", aNormal),
        AddChangeForValidation(apexZone, "some.dotted.record", aDotted)
      )

      val discovered = underTest.zoneDiscovery(
        List(aApex.validNel, aNormal.validNel, aDotted.validNel),
        ExistingZones(Set(apexZone, baseZone)))

      discovered.getValid shouldBe expected
    }

    "properly discover TXT records" in {
      val apex = apexZone.name

      val txtApex = AddChangeInput(apex, RecordType.TXT, ttl, TXTData("test"))
      val txtNormal = AddChangeInput(s"record.$apex", RecordType.TXT, ttl, TXTData("test"))
      val txtDotted =
        AddChangeInput(s"some.dotted.record.$apex", RecordType.TXT, ttl, TXTData("test"))

      val expected = List(
        AddChangeForValidation(apexZone, apex, txtApex),
        AddChangeForValidation(apexZone, "record", txtNormal),
        AddChangeForValidation(apexZone, "some.dotted.record", txtDotted)
      )

      val discovered = underTest.zoneDiscovery(
        List(txtApex.validNel, txtNormal.validNel, txtDotted.validNel),
        ExistingZones(Set(apexZone, baseZone)))

      discovered.getValid shouldBe expected
    }

    "return an error if an apex zone is found for CNAME records" in {
      val result =
        underTest.zoneDiscovery(List(cnameApexAdd.validNel), ExistingZones(Set(apexZone)))

      result.head should haveInvalid[DomainValidationError](CnameAtZoneApexError(apexZone.name))
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
        AddChangeInput("2001:db8::ff00:42:8329", RecordType.PTR, ttl, PTRData("ptr"))
      val medZoneAdd = AddChangeInput(
        "2001:0db8:0111:0000:0000:ff00:0042:8329",
        RecordType.PTR,
        ttl,
        PTRData("ptr"))
      val bigZoneAdd = AddChangeInput(
        "2001:0000:0000:0000:0000:ff00:0042:8329",
        RecordType.PTR,
        ttl,
        PTRData("ptr"))
      val notFoundZoneAdd = AddChangeInput("::1", RecordType.PTR, ttl, PTRData("ptr"))

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
        Some(apexZone.id),
        Some(apexZone.name),
        Some("apex.test.com."),
        "apex.test.com.",
        A,
        ttl.get,
        AData("1.1.1.1"),
        SingleChangeStatus.Pending,
        None,
        None,
        None,
        List.empty,
        result.changes.head.id
      )
      result.changes(1) shouldBe SingleAddChange(
        Some(onlyBaseZone.id),
        Some(onlyBaseZone.name),
        Some("have"),
        "have.only.base.",
        AAAA,
        ttl.get,
        AAAAData("1:2:3:4:5:6:7:8"),
        SingleChangeStatus.Pending,
        None,
        None,
        None,
        List.empty,
        result.changes(1).id
      )
      result.changes(2) shouldBe SingleAddChange(
        Some(baseZone.id),
        Some(baseZone.name),
        Some("cname"),
        "cname.test.com.",
        CNAME,
        ttl.get,
        CNAMEData("testing.test.com."),
        SingleChangeStatus.Pending,
        None,
        None,
        None,
        List.empty,
        result.changes(2).id
      )
    }
    "return a BatchChange if all data inputs are valid/soft failures and manual review is enabled" in {
      val delete = DeleteChangeInput("some.test.delete.", RecordType.TXT)
      val result = underTestManualEnabled
        .buildResponse(
          BatchChangeInput(None, List(apexAddA, onlyBaseAddAAAA, delete)),
          List(
            AddChangeForValidation(apexZone, "apex.test.com.", apexAddA).validNel,
            nonFatalError.invalidNel,
            nonFatalError.invalidNel
          ),
          okAuth
        )
        .toOption
        .get

      result shouldBe a[BatchChange]
      result.changes.head shouldBe SingleAddChange(
        Some(apexZone.id),
        Some(apexZone.name),
        Some("apex.test.com."),
        "apex.test.com.",
        A,
        ttl.get,
        AData("1.1.1.1"),
        SingleChangeStatus.Pending,
        None,
        None,
        None,
        List.empty,
        result.changes.head.id
      )
      result.changes(1) shouldBe SingleAddChange(
        None,
        None,
        None,
        "have.only.base.",
        AAAA,
        ttl.get,
        AAAAData("1:2:3:4:5:6:7:8"),
        SingleChangeStatus.NeedsReview,
        None,
        None,
        None,
        List(DomainValidationStoredError(nonFatalError)),
        result.changes(1).id
      )
      result.changes(2) shouldBe core.domain.batch.SingleDeleteChange(
        None,
        None,
        None,
        "some.test.delete.",
        TXT,
        SingleChangeStatus.NeedsReview,
        None,
        None,
        None,
        List(DomainValidationStoredError(nonFatalError)),
        result.changes(2).id
      )
    }
    "return a BatchChangeErrorList if all data inputs are valid/soft failures and manual review is disabled" in {
      val delete = DeleteChangeInput("some.test.delete.", RecordType.TXT)
      val result = underTest
        .buildResponse(
          BatchChangeInput(None, List(apexAddA, onlyBaseAddAAAA, delete)),
          List(
            AddChangeForValidation(apexZone, "apex.test.com.", apexAddA).validNel,
            nonFatalError.invalidNel,
            nonFatalError.invalidNel
          ),
          okAuth
        )
        .left
        .value

      result shouldBe an[InvalidBatchChangeResponses]
    }
    "return a BatchChangeErrorList if any data inputs are invalid" in {
      val result = underTest
        .buildResponse(
          BatchChangeInput(None, List(noZoneAddA, nonApexAddA)),
          List(
            ZoneDiscoveryError("no.zone.match.").invalidNel,
            AddChangeForValidation(baseZone, "non-apex", nonApexAddA).validNel,
            nonFatalError.invalidNel),
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
      ibcr.changeRequestResponses(2) should haveInvalid[DomainValidationError](nonFatalError)
    }
  }

  "listBatchChangeSummaries" should {
    "return a list of batchChangeSummaries if one exists" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChange)

      val result = rightResultOf(underTest.listBatchChangeSummaries(auth, maxItems = 100).value)

      result.maxItems shouldBe 100
      result.nextId shouldBe None
      result.startFrom shouldBe None
      result.ignoreAccess shouldBe false

      result.batchChanges.length shouldBe 1
      result.batchChanges(0).createdTimestamp shouldBe batchChange.createdTimestamp
    }

    "return a list of batchChangeSummaries if some exist" in {
      val batchChangeOne =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeOne)

      val batchChangeTwo = BatchChange(
        auth.userId,
        auth.signedInUser.userName,
        None,
        new DateTime(DateTime.now.getMillis + 1000),
        List(),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeTwo)

      val result = rightResultOf(underTest.listBatchChangeSummaries(auth, maxItems = 100).value)

      result.maxItems shouldBe 100
      result.nextId shouldBe None
      result.startFrom shouldBe None
      result.ignoreAccess shouldBe false

      result.batchChanges.length shouldBe 2
      result.batchChanges(0).createdTimestamp shouldBe batchChangeTwo.createdTimestamp
      result.batchChanges(1).createdTimestamp shouldBe batchChangeOne.createdTimestamp
    }

    "return a limited list of batchChangeSummaries if some exist" in {
      val batchChangeOne =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeOne)

      val batchChangeTwo = BatchChange(
        auth.userId,
        auth.signedInUser.userName,
        None,
        new DateTime(DateTime.now.getMillis + 1000),
        List(),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeTwo)

      val result = rightResultOf(underTest.listBatchChangeSummaries(auth, maxItems = 1).value)

      result.maxItems shouldBe 1
      result.nextId shouldBe Some(1)
      result.startFrom shouldBe None
      result.ignoreAccess shouldBe false

      result.batchChanges.length shouldBe 1
      result.batchChanges(0).createdTimestamp shouldBe batchChangeTwo.createdTimestamp
    }

    "return list of batchChangeSummaries filtered by approvalStatus if some exist" in {
      val batchChangeOne =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.PendingApproval)
      batchChangeRepo.save(batchChangeOne)

      val batchChangeTwo = BatchChange(
        auth.userId,
        auth.signedInUser.userName,
        None,
        new DateTime(DateTime.now.getMillis + 1000),
        List(),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeTwo)

      val result = rightResultOf(
        underTest
          .listBatchChangeSummaries(
            auth,
            approvalStatus = Some(BatchChangeApprovalStatus.PendingApproval))
          .value)

      result.maxItems shouldBe 100
      result.nextId shouldBe None
      result.startFrom shouldBe None
      result.ignoreAccess shouldBe false
      result.approvalStatus shouldBe Some(BatchChangeApprovalStatus.PendingApproval)

      result.batchChanges.length shouldBe 1
      result.batchChanges(0).createdTimestamp shouldBe batchChangeOne.createdTimestamp
    }

    "return an offset list of batchChangeSummaries if some exist" in {
      val batchChangeOne =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeOne)

      val batchChangeTwo = BatchChange(
        auth.userId,
        auth.signedInUser.userName,
        None,
        new DateTime(DateTime.now.getMillis + 1000),
        List(),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeTwo)

      val result =
        rightResultOf(underTest.listBatchChangeSummaries(auth, startFrom = Some(1)).value)

      result.maxItems shouldBe 100
      result.nextId shouldBe None
      result.startFrom shouldBe Some(1)
      result.ignoreAccess shouldBe false

      result.batchChanges.length shouldBe 1
      result.batchChanges(0).createdTimestamp shouldBe batchChangeOne.createdTimestamp
    }

    "only return summaries associated with user who called" in {
      val batchChangeUserOne =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeUserOne)

      val batchChangeUserTwo = BatchChange(
        notAuth.userId,
        auth.signedInUser.userName,
        None,
        new DateTime(DateTime.now.getMillis + 1000),
        List(),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeUserTwo)

      val result =
        rightResultOf(underTest.listBatchChangeSummaries(auth, maxItems = 100).value).batchChanges

      result.length shouldBe 1
      result(0).createdTimestamp shouldBe batchChangeUserOne.createdTimestamp
    }

    "only return summaries associated with user who called even if ignoreAccess is true if user is not super" in {
      val batchChangeUserOne =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeUserOne)

      val batchChangeUserTwo = BatchChange(
        notAuth.userId,
        auth.signedInUser.userName,
        None,
        new DateTime(DateTime.now.getMillis + 1000),
        List(),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeUserTwo)

      val result =
        rightResultOf(underTest.listBatchChangeSummaries(auth, ignoreAccess = true).value).batchChanges

      result.length shouldBe 1
      result(0).createdTimestamp shouldBe batchChangeUserOne.createdTimestamp
    }

    "return all summaries if user is super and requests all" in {
      val batchChangeUserOne =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeUserOne)

      val batchChangeUserTwo = BatchChange(
        notAuth.userId,
        auth.signedInUser.userName,
        None,
        new DateTime(DateTime.now.getMillis + 1000),
        List(),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      batchChangeRepo.save(batchChangeUserTwo)

      val result =
        rightResultOf(underTest.listBatchChangeSummaries(superUserAuth, ignoreAccess = true).value)

      result.maxItems shouldBe 100
      result.nextId shouldBe None
      result.startFrom shouldBe None
      result.ignoreAccess shouldBe true

      result.batchChanges.length shouldBe 2
      result.batchChanges(0).createdTimestamp shouldBe batchChangeUserTwo.createdTimestamp
      result.batchChanges(1).createdTimestamp shouldBe batchChangeUserOne.createdTimestamp
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
          ownerGroupId = Some(okGroup.id),
          BatchChangeApprovalStatus.AutoApproved)
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
          ownerGroupId = Some("no-existo"),
          BatchChangeApprovalStatus.AutoApproved)
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

  "convertOrSave" should {
    "send to the converter if approved" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          Some("checkConverter"),
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)

      val result = rightResultOf(
        underTestManualEnabled
          .convertOrSave(batchChange, ExistingZones(Set()), ExistingRecordSets(List()), None)
          .value)
      result.reviewComment shouldBe Some("batchSentToConverter")
    }
    "not send to the converter, save the change if PendingApproval and MA enabled" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          Some("checkConverter"),
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.PendingApproval)

      val result = rightResultOf(
        underTestManualEnabled
          .convertOrSave(batchChange, ExistingZones(Set()), ExistingRecordSets(List()), None)
          .value)

      // not sent to converter
      result.reviewComment shouldBe None
      // saved in DB
      batchChangeRepo.getBatchChange(batchChange.id).unsafeRunSync() shouldBe defined
    }
    "error if PendingApproval but MA disabled" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          Some("checkConverter"),
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.PendingApproval)

      val result = leftResultOf(
        underTest
          .convertOrSave(batchChange, ExistingZones(Set()), ExistingRecordSets(List()), None)
          .value)

      result shouldBe an[UnknownConversionError]
    }
    "error if ManuallyApproved but MA disabled" in {
      val batchChange =
        BatchChange(
          auth.userId,
          auth.signedInUser.userName,
          Some("checkConverter"),
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.ManuallyApproved)

      val result = leftResultOf(
        underTest
          .convertOrSave(batchChange, ExistingZones(Set()), ExistingRecordSets(List()), None)
          .value)
      result shouldBe an[UnknownConversionError]
    }
  }

}
