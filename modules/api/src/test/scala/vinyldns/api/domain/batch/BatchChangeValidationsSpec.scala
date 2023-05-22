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

import cats.implicits._
import cats.scalatest.{EitherMatchers, ValidatedMatchers}

import java.time.Instant
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import vinyldns.api.VinylDNSTestHelpers
import vinyldns.api.config.ScheduledChangesConfig
import vinyldns.api.domain.access.AccessValidations
import vinyldns.api.domain.batch.BatchTransformations._
import vinyldns.api.domain.{DomainValidations, batch}
import vinyldns.core.TestMembershipData._
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.core.domain._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.batch.{BatchChange, BatchChangeApprovalStatus, OwnerType}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{ACLRule, AccessLevel, Zone, ZoneStatus}

import java.time.temporal.ChronoUnit
import scala.util.Random

class BatchChangeValidationsSpec
  extends AnyPropSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with EitherMatchers
    with EitherValues
    with ValidatedMatchers {

  import Gen._
  import vinyldns.api.DomainGenerator._
  import vinyldns.api.IpAddressGenerator._

  private val maxChanges = 1000
  private val accessValidations = new AccessValidations(
    sharedApprovedTypes = VinylDNSTestHelpers.sharedApprovedTypes
  )
  private val defaultTtl = VinylDNSTestHelpers.defaultTtl
  private val underTest =
    new BatchChangeValidations(
      accessValidations,
      VinylDNSTestHelpers.highValueDomainConfig,
      VinylDNSTestHelpers.manualReviewConfig,
      VinylDNSTestHelpers.batchChangeConfig,
      VinylDNSTestHelpers.scheduledChangesConfig,
      VinylDNSTestHelpers.approvedNameServers
    )

  import underTest._

  private val validZone = Zone(
    "ok.zone.recordsets.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = okGroup.id
  )

  private val validIp4ReverseZone = Zone(
    "2.0.192.in-addr.arpa",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = okGroup.id
  )

  private val ttl = Some(100L)

  private val validAChangeGen: Gen[AddChangeInput] = for {
    fqdn <- domainGenerator
    ip <- validIpv4Gen
  } yield AddChangeInput(fqdn, RecordType.A, ttl, AData(ip))

  private val validAAAAChangeGen: Gen[AddChangeInput] = for {
    fqdn <- domainGenerator
    ip <- validIpv6Gen
  } yield AddChangeInput(fqdn, RecordType.AAAA, ttl, AAAAData(ip))

  private val validAddChangeForValidationGen: Gen[AddChangeForValidation] = for {
    recordName <- domainComponentGenerator
    changeInput <- validAChangeGen
  } yield AddChangeForValidation(validZone, recordName, changeInput, defaultTtl)

  private def generateValidAddChangeForValidation(rs: RecordSet): Gen[AddChangeForValidation] =
    for {
      recordName <- domainComponentGenerator
      addChangeInput <- AddChangeInput(recordName, rs.typ, Some(rs.ttl), rs.records.head)
    } yield AddChangeForValidation(validZone, recordName, addChangeInput, defaultTtl)

  private val recordSetList = List(rsOk, aaaa, aaaaOrigin, abcRecord)

  private def validBatchChangeInput(min: Int, max: Int): Gen[BatchChangeInput] =
    for {
      numChanges <- choose(min, max)
      changes <- listOfN(numChanges, validAChangeGen)
    } yield batch.BatchChangeInput(None, changes)

  private val createPrivateAddChange = AddChangeForValidation(
    okZone,
    "private-create",
    AddChangeInput("private-create", RecordType.A, ttl, AData("1.1.1.1")),
    defaultTtl
  )

  private val createSharedAddChange = AddChangeForValidation(
    sharedZone,
    "shared-create",
    AddChangeInput("shared-create", RecordType.A, ttl, AData("1.1.1.1")),
    defaultTtl
  )

  private val updatePrivateAddChange = AddChangeForValidation(
    okZone,
    "private-update",
    AddChangeInput("private-update", RecordType.A, ttl, AAAAData("1.2.3.4")),
    defaultTtl
  )

  private val updatePrivateDeleteChange = DeleteRRSetChangeForValidation(
    okZone,
    "private-update",
    DeleteRRSetChangeInput("private-update", RecordType.A)
  )

  private val updateSharedAddChange = AddChangeForValidation(
    sharedZone,
    "shared-update",
    AddChangeInput("shared-update", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8")),
    defaultTtl
  )

  private val updateSharedDeleteChange = DeleteRRSetChangeForValidation(
    sharedZone,
    "shared-update",
    DeleteRRSetChangeInput("shared-update", RecordType.AAAA)
  )

  private val deleteSingleRecordChange = DeleteRRSetChangeForValidation(
    sharedZone,
    "shared-update",
    DeleteRRSetChangeInput("shared-update", RecordType.AAAA, Some(AAAAData("1:0::1")))
  )

  private val deletePrivateChange = DeleteRRSetChangeForValidation(
    okZone,
    "private-delete",
    DeleteRRSetChangeInput("private-delete", RecordType.A)
  )

  private val deleteSharedChange = DeleteRRSetChangeForValidation(
    sharedZone,
    "shared-delete",
    DeleteRRSetChangeInput("shared-delete", RecordType.AAAA)
  )

  private val validPendingBatchChange = BatchChange(
    okUser.id,
    okUser.userName,
    None,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    List(),
    approvalStatus = BatchChangeApprovalStatus.PendingReview
  )

  private val invalidPendingBatchChange = BatchChange(
    okUser.id,
    okUser.userName,
    None,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    List(),
    approvalStatus = BatchChangeApprovalStatus.AutoApproved
  )

  private def makeAddUpdateRecord(
                                   recordName: String,
                                   aData: AData = AData("1.2.3.4")
                                 ): AddChangeForValidation =
    AddChangeForValidation(
      okZone,
      s"$recordName",
      AddChangeInput(s"$recordName.ok.", RecordType.A, ttl, aData),
      defaultTtl
    )

  private def makeDeleteUpdateDeleteRRSet(
                                           recordName: String,
                                           recordData: Option[RecordData] = None
                                         ): DeleteRRSetChangeForValidation =
    DeleteRRSetChangeForValidation(
      okZone,
      s"$recordName",
      DeleteRRSetChangeInput(s"$recordName.ok.", RecordType.A, recordData)
    )

  property("validateBatchChangeInputSize: should fail if batch has no changes") {
    validateBatchChangeInputSize(BatchChangeInput(None, List())) should
      haveInvalid[DomainValidationError](BatchChangeIsEmpty(maxChanges))
  }

  property(
    "validateBatchChangeInputSize: should succeed with at least one but fewer than max inputs"
  ) {
    forAll(validBatchChangeInput(1, maxChanges)) { input: BatchChangeInput =>
      validateBatchChangeInputSize(input).isValid shouldBe true
    }

    forAll(validBatchChangeInput(maxChanges + 1, 10000)) { input: BatchChangeInput =>
      validateBatchChangeInputSize(input) should haveInvalid[DomainValidationError](
        ChangeLimitExceeded(maxChanges)
      )
    }
  }

  property(
    "isApprovedNameServer: should be valid if the name server is on approved name server list"
  ) {
    isApprovedNameServer(VinylDNSTestHelpers.approvedNameServers, NSData(Fqdn("some.test.ns."))) shouldBe ().validNel
  }

  property(
    "isApprovedNameServer: should throw an error if the name server is not on approved name server list"
  ) {
    val nsData = NSData(Fqdn("not.valid."))
    isApprovedNameServer(VinylDNSTestHelpers.approvedNameServers, nsData) shouldBe NotApprovedNSError(nsData.nsdname.fqdn).invalidNel
  }

  property(
    "containsApprovedNameServers: should be valid if the name server is on approved name server list"
  ) {
    containsApprovedNameServers(NSData(Fqdn("some.test.ns.")), VinylDNSTestHelpers.approvedNameServers) shouldBe ().validNel
  }

  property(
    "containsApprovedNameServers: should throw an error if the name server is not on approved name server list"
  ) {
    val nsData = NSData(Fqdn("not.valid."))
    containsApprovedNameServers(nsData, VinylDNSTestHelpers.approvedNameServers) shouldBe NotApprovedNSError(nsData.nsdname.fqdn).invalidNel
  }

  property(
    "isOriginRecord: should return true if the record is origin"
  ) {
    isOriginRecord("@", "ok.") shouldBe true
    isOriginRecord("ok.", "ok.") shouldBe true
  }

  property(
    "isOriginRecord: should return false if the record is not origin"
  ) {
    isOriginRecord("dummy.ok.", "ok.") shouldBe false
  }

  property(
    "isNotOrigin: should be valid if the record is not origin"
  ) {
    val recordSetName = "ok.zone.recordsets."
    val error = s"Record with name $recordSetName is an NS record at apex and cannot be added"
    isNotOrigin(recordSetName, okZone, error) shouldBe InvalidBatchRequest(error).invalidNel
  }

  property(
    "isNotOrigin: should throw an error if the record is origin"
  ) {
    val recordSetName = "test."
    val error = s"Record with name $recordSetName is an NS record at apex and cannot be added"
    isNotOrigin(recordSetName, okZone, error) shouldBe ().validNel
  }

  property(
    "validateScheduledChange: should fail if batch is scheduled and scheduled change disabled"
  ) {
    val input = BatchChangeInput(None, List(), scheduledTime = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)))
    validateScheduledChange(input, scheduledChangesEnabled = false) should
      beLeft[BatchChangeErrorResponse](ScheduledChangesDisabled)
  }

  property(
    "validateScheduledChange: should succeed if batch is scheduled and scheduled change enabled"
  ) {
    val input = BatchChangeInput(None, List(), scheduledTime = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS).plus(1, ChronoUnit.HOURS)))
    validateScheduledChange(input, scheduledChangesEnabled = true) should beRight(())
  }

  property(
    "validateScheduledChange: should succeed if batch is not scheduled and scheduled change disabled"
  ) {
    val input = BatchChangeInput(None, List(), scheduledTime = None)
    validateScheduledChange(input, scheduledChangesEnabled = false) should beRight(())
  }

  property("validateInputChanges: should succeed if all inputs are good") {
    forAll(listOfN(3, validAChangeGen)) { input: List[ChangeInput] =>
      val result = validateInputChanges(input, false)
      result.map(_ shouldBe valid)
    }
  }

  property("validateOwnerGroupId: should succeed if owner group ID is undefined") {
    validateOwnerGroupId(None, None, okAuth) should beValid(())
  }

  property("validateOwnerGroupId: should succeed if user belongs to owner group") {
    validateOwnerGroupId(Some(okGroup.id), Some(okGroup), okAuth) should beValid(())
  }

  property("validateOwnerGroupId: should succeed if user is a super user") {
    validateOwnerGroupId(Some(okGroup.id), Some(okGroup), superUserAuth) should beValid(())
  }

  property("validateOwnerGroupId: should fail if owner group does not exist") {
    validateOwnerGroupId(Some(okGroup.id), None, okAuth) should
      haveInvalid[DomainValidationError](GroupDoesNotExist(okGroup.id))
  }

  property(
    "validateOwnerGroupId: should fail if user is not an admin and does not belong to owner group"
  ) {
    validateOwnerGroupId(Some(okGroup.id), Some(okGroup), dummyAuth) should
      haveInvalid[DomainValidationError](
        NotAMemberOfOwnerGroup(okGroup.id, dummyAuth.signedInUser.userName)
      )
  }

  property(
    "validateBatchChangeInput: should succeed if input size and owner group ID are both valid"
  ) {
    forAll(validBatchChangeInput(1, 10)) { batchChangeInput =>
      validateBatchChangeInput(batchChangeInput, None, okAuth).value.unsafeRunSync() should be(
        right
      )
    }
  }

  property(
    "validateBatchChangeInput: should fail if input size is invalid and owner group ID is valid"
  ) {
    forAll(validBatchChangeInput(1001, 2000)) { batchChangeInput =>
      validateBatchChangeInput(batchChangeInput, None, okAuth).value
        .unsafeRunSync() shouldBe
        Left(InvalidBatchChangeInput(List(ChangeLimitExceeded(maxChanges))))
    }
  }

  property(
    "validateBatchChangeInput: should fail if input size is valid and owner group ID is invalid"
  ) {
    forAll(validBatchChangeInput(1, 10)) { batchChangeInput =>
      validateBatchChangeInput(
        batchChangeInput.copy(ownerGroupId = Some(okGroup.id)),
        Some(okGroup),
        dummyAuth
      ).value.unsafeRunSync() shouldBe
        Left(
          InvalidBatchChangeInput(
            List(NotAMemberOfOwnerGroup(okGroup.id, dummyAuth.signedInUser.userName))
          )
        )
    }
  }

  property(
    "validateBatchChangeInput: should fail if both input size is valid and owner group ID are invalid"
  ) {
    forAll(validBatchChangeInput(0, 0)) { batchChangeInput =>
      val result = validateBatchChangeInput(
        batchChangeInput.copy(ownerGroupId = Some(dummyGroup.id)),
        None,
        okAuth
      ).value.unsafeRunSync()
      result shouldBe
        Left(
          InvalidBatchChangeInput(
            List(BatchChangeIsEmpty(maxChanges), GroupDoesNotExist(dummyGroup.id))
          )
        )
    }
  }

  property(
    "validateBatchChangeInput: should fail if scheduled is set but scheduled changes disabled"
  ) {
    val input = BatchChangeInput(
      None,
      List(AddChangeInput("private-create", RecordType.A, ttl, AData("1.1.1.1"))),
      scheduledTime = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS))
    )
    val bcv =
      new BatchChangeValidations(
        accessValidations,
        VinylDNSTestHelpers.highValueDomainConfig,
        VinylDNSTestHelpers.manualReviewConfig,
        VinylDNSTestHelpers.batchChangeConfig,
        ScheduledChangesConfig(enabled = false),
        VinylDNSTestHelpers.approvedNameServers
      )
    bcv.validateBatchChangeInput(input, None, okAuth).value.unsafeRunSync() shouldBe Left(
      ScheduledChangesDisabled
    )
  }

  property(
    "validateBatchChangeInput: should fail if scheduled changes is enabled but scheduled time is in the past"
  ) {
    val input = BatchChangeInput(
      None,
      List(AddChangeInput("private-create", RecordType.A, ttl, AData("1.1.1.1"))),
      scheduledTime = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS).minus(1, ChronoUnit.HOURS))
    )
    val bcv =
      new BatchChangeValidations(
        accessValidations,
        VinylDNSTestHelpers.highValueDomainConfig,
        VinylDNSTestHelpers.manualReviewConfig,
        VinylDNSTestHelpers.batchChangeConfig,
        ScheduledChangesConfig(enabled = true),
        VinylDNSTestHelpers.approvedNameServers
      )
    bcv.validateBatchChangeInput(input, None, okAuth).value.unsafeRunSync() shouldBe Left(
      ScheduledTimeMustBeInFuture
    )
  }

  property("validateBatchChangePendingReview: should succeed if batch change is PendingReview") {
    validateBatchChangePendingReview(validPendingBatchChange) should be(right)
  }

  property("validateBatchChangePendingReview: should fail if batch change is not PendingReview") {
    validateBatchChangePendingReview(invalidPendingBatchChange) shouldBe
      Left(BatchChangeNotPendingReview(invalidPendingBatchChange.id))
  }

  property("validateScheduledApproval: should fail if scheduled time is not due") {
    val dt = Instant.now.truncatedTo(ChronoUnit.MILLIS).plus(2, ChronoUnit.DAYS)
    val change = validPendingBatchChange.copy(scheduledTime = Some(dt))
    validateScheduledApproval(change) shouldBe Left(ScheduledChangeNotDue(dt))
  }

  property("validateScheduledApproval: should succeed if scheduled time is due") {
    val dt = Instant.now.truncatedTo(ChronoUnit.MILLIS).minus(2, ChronoUnit.DAYS)
    val change = validPendingBatchChange.copy(scheduledTime = Some(dt))
    validateScheduledApproval(change) should be(right)
  }

  property("validateScheduledApproval: should succeed if scheduled time is not set") {
    val change = validPendingBatchChange.copy(scheduledTime = None)
    validateScheduledApproval(change) should be(right)
  }

  property("validateAuthorizedReviewer: should succeed if the reviewer is a super user") {
    validateAuthorizedReviewer(superUserAuth, validPendingBatchChange, false) should be(right)
  }

  property("validateAuthorizedReviewer: should succeed if the reviewer is a support user") {
    validateAuthorizedReviewer(supportUserAuth, validPendingBatchChange, false) should be(right)
  }

  property(
    "validateAuthorizedReviewer: should fail if a test reviewer tries to approve a non-test change"
  ) {
    val testSupport = supportUser.copy(isTest = true)
    validateAuthorizedReviewer(AuthPrincipal(testSupport, List()), validPendingBatchChange, false) shouldBe
      Left(UserNotAuthorizedError(validPendingBatchChange.id))
  }

  property(
    "validateAuthorizedReviewer: should succeed if a test reviewer tries to approve a test change"
  ) {
    val testSupport = supportUser.copy(isTest = true)
    validateAuthorizedReviewer(AuthPrincipal(testSupport, List()), validPendingBatchChange, true) should be(
      right
    )
  }

  property("validateAuthorizedReviewer: should fail if the reviewer is not a super or support user") {
    validateAuthorizedReviewer(okAuth, validPendingBatchChange, false) shouldBe
      Left(UserNotAuthorizedError(validPendingBatchChange.id))
  }

  property(
    "validateBatchChangeRejection: should succeed if batch change is pending review and reviewer" +
      "is authorized"
  ) {
    validateBatchChangeRejection(validPendingBatchChange, supportUserAuth, false) should be(right)
  }

  property(
    "validateBatchChangeRejection: should fail if a test reviewer tries to reject a non-test change"
  ) {
    val testSupport = supportUser.copy(isTest = true)
    validateBatchChangeRejection(validPendingBatchChange, AuthPrincipal(testSupport, List()), false) shouldBe
      Left(UserNotAuthorizedError(validPendingBatchChange.id))
  }

  property(
    "validateBatchChangeRejection: should succeed if a test reviewer tries to reject a test change"
  ) {
    val testSupport = supportUser.copy(isTest = true)
    validateBatchChangeRejection(validPendingBatchChange, AuthPrincipal(testSupport, List()), true) should be(
      right
    )
  }

  property("validateBatchChangeRejection: should fail if batch change is not pending review") {
    validateBatchChangeRejection(invalidPendingBatchChange, supportUserAuth, false) shouldBe
      Left(BatchChangeNotPendingReview(invalidPendingBatchChange.id))
  }

  property("validateBatchChangeRejection: should fail if reviewer is not authorized") {
    validateBatchChangeRejection(validPendingBatchChange, okAuth, false) shouldBe
      Left(UserNotAuthorizedError(validPendingBatchChange.id))
  }

  property(
    "validateBatchChangeRejection: should fail if batch change is not pending review and reviewer is not" +
      "authorized"
  ) {
    validateBatchChangeRejection(invalidPendingBatchChange, okAuth, false) shouldBe
      Left(UserNotAuthorizedError(invalidPendingBatchChange.id))
  }

  property(
    "validateBatchChangeCancellation: should succeed if batch change is pending review" +
      " and user was the creator"
  ) {
    validateBatchChangeCancellation(validPendingBatchChange, okAuth) should be(right)
  }

  property(
    "validateBatchChangeCancellation: should fail if user was the creator" +
      " but batch change is not pending review"
  ) {
    validateBatchChangeCancellation(invalidPendingBatchChange, okAuth) shouldBe
      Left(BatchChangeNotPendingReview(invalidPendingBatchChange.id))
  }

  property(
    "validateBatchChangeCancellation: should fail if batch change is pending review" +
      " but user was not the creator"
  ) {
    validateBatchChangeCancellation(validPendingBatchChange, supportUserAuth) shouldBe
      Left(UserNotAuthorizedError(validPendingBatchChange.id))
  }

  property("validateInputChanges: should fail with mix of success and failure inputs") {
    val goodNSInput = AddChangeInput("test-ns.example.com.", RecordType.NS, ttl, NSData(Fqdn("some.test.ns.")))
    val goodNAPTRInput = AddChangeInput("test-naptr.example.com.", RecordType.NAPTR, ttl, NAPTRData(1, 2, "S", "E2U+sip", "", Fqdn("target")))
    val goodSRVInput = AddChangeInput("test-srv.example.com.", RecordType.SRV, ttl, SRVData(1, 2, 3, Fqdn("target.vinyldns.")))
    val badNSInput = AddChangeInput("test-bad-ns.example.com.", RecordType.NS, ttl, NSData(Fqdn("some.te$st.ns.")))
    val badNAPTRInput = AddChangeInput("test-bad-naptr.example.com.", RecordType.NAPTR, ttl, NAPTRData(99999, 2, "S", "E2U+sip", "", Fqdn("target")))
    val badNAPTRFlagInput = AddChangeInput("test-bad-flag-naptr.example.com.", RecordType.NAPTR, ttl, NAPTRData(1, 2, "t", "E2U+sip", "", Fqdn("target")))
    val badSRVInput = AddChangeInput("test-bad-srv.example.com.", RecordType.SRV, ttl, SRVData(99999, 2, 3, Fqdn("target.vinyldns.")))
    val goodInput = AddChangeInput("test.example.com.", RecordType.A, ttl, AData("1.1.1.1"))
    val goodAAAAInput =
      AddChangeInput("testAAAA.example.com.", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8"))
    val invalidDomainNameInput =
      AddChangeInput("invalidDomainName$", RecordType.A, ttl, AAAAData("1:2:3:4:5:6:7:8"))
    val invalidIpv6Input =
      AddChangeInput("testbad.example.com.", RecordType.AAAA, ttl, AAAAData("invalidIpv6:123"))
    val result =
      validateInputChanges(
        List(goodNSInput, goodNAPTRInput, goodSRVInput, goodInput, goodAAAAInput, invalidDomainNameInput, invalidIpv6Input, badNSInput, badNAPTRInput, badNAPTRFlagInput, badSRVInput),
        false
      )
    result(0) shouldBe valid
    result(1) shouldBe valid
    result(2) shouldBe valid
    result(3) shouldBe valid
    result(4) shouldBe valid
    result(5) should haveInvalid[DomainValidationError](InvalidDomainName("invalidDomainName$."))
    result(6) should haveInvalid[DomainValidationError](InvalidIpv6Address("invalidIpv6:123"))
    result(7) should haveInvalid[DomainValidationError](InvalidDomainName("some.te$st.ns."))
    result(8) should haveInvalid[DomainValidationError](InvalidMX_NAPTR_SRVData(99999, 0, 65535, "order", "NAPTR"))
    result(9) should haveInvalid[DomainValidationError](InvalidNaptrFlag("t"))
    result(10) should haveInvalid[DomainValidationError](InvalidMX_NAPTR_SRVData(99999, 0, 65535, "priority", "SRV"))
  }

  property("""validateInputName: should fail with a HighValueDomainError
             |if inputName is a High Value Domain""".stripMargin) {
    val changeA = AddChangeInput("high-value-domain.foo.", RecordType.A, ttl, AData("1.1.1.1"))
    val changeIpV4 = AddChangeInput("192.0.2.252", RecordType.PTR, ttl, PTRData(Fqdn("test.")))
    val changeIpV6 =
      AddChangeInput("fd69:27cc:fe91:0:0:0:0:ffff", RecordType.PTR, ttl, PTRData(Fqdn("test.")))

    val resultA = validateInputName(changeA, false)
    val resultIpV4 = validateInputName(changeIpV4, false)
    val resultIpV6 = validateInputName(changeIpV6, false)

    resultA should haveInvalid[DomainValidationError](
      HighValueDomainError("high-value-domain.foo.")
    )
    resultIpV4 should haveInvalid[DomainValidationError](HighValueDomainError("192.0.2.252"))
    resultIpV6 should haveInvalid[DomainValidationError](
      HighValueDomainError("fd69:27cc:fe91:0:0:0:0:ffff")
    )
  }

  property("""validateInputName: should fail with a RecordRequiresManualReview
             |if inputName is matches domain requiring manual review""".stripMargin) {
    val changeA = AddChangeInput("needs-review.foo.", RecordType.A, ttl, AData("1.1.1.1"))
    val changeIpV4 = AddChangeInput("192.0.2.254", RecordType.PTR, ttl, PTRData(Fqdn("test.")))
    val changeIpV6 =
      AddChangeInput("fd69:27cc:fe91:0:0:0:ffff:1", RecordType.PTR, ttl, PTRData(Fqdn("test.")))

    val resultA = validateInputName(changeA, false)
    val resultIpV4 = validateInputName(changeIpV4, false)
    val resultIpV6 = validateInputName(changeIpV6, false)

    resultA should haveInvalid[DomainValidationError](
      RecordRequiresManualReview("needs-review.foo.")
    )
    resultIpV4 should haveInvalid[DomainValidationError](RecordRequiresManualReview("192.0.2.254"))
    resultIpV6 should haveInvalid[DomainValidationError](
      RecordRequiresManualReview("fd69:27cc:fe91:0:0:0:ffff:1")
    )
  }

  property("doesNotRequireManualReview: should succeed if user is reviewing") {
    val changeA = AddChangeInput("needs-review.foo.", RecordType.A, ttl, AData("1.1.1.1"))
    validateInputName(changeA, true) should beValid(())
  }

  property("""zoneDoesNotRequireManualReview: should fail with RecordRequiresManualReview
             |if zone name matches domain requiring manual review""".stripMargin) {
    val addChangeInput =
      AddChangeInput("not-allowed.zone.NEEDS.review", RecordType.A, ttl, AData("1.1.1.1"))
    val addChangeForValidation = AddChangeForValidation(
      Zone("Zone.needs.review", "some@email.com"),
      "not-allowed",
      addChangeInput,
      defaultTtl
    )
    zoneDoesNotRequireManualReview(addChangeForValidation, false) should
      haveInvalid[DomainValidationError](
        RecordRequiresManualReview("not-allowed.zone.NEEDS.review.")
      )
  }

  property("""zoneDoesNotRequireManualReview: should succeed if user is reviewing""") {
    val addChangeInput =
      AddChangeInput("not-allowed.zone.NEEDS.review", RecordType.A, ttl, AData("1.1.1.1"))
    val addChangeForValidation = AddChangeForValidation(
      Zone("Zone.needs.review", "some@email.com"),
      "not-allowed",
      addChangeInput,
      defaultTtl
    )
    zoneDoesNotRequireManualReview(addChangeForValidation, true) shouldBe valid
  }

  property("""validateInputName: should fail with a DomainValidationError for deletes
             |if validateHostName fails for an invalid domain name""".stripMargin) {
    val change = DeleteRRSetChangeInput("invalidDomainName$", RecordType.A)
    val result = validateInputName(change, false)
    result should haveInvalid[DomainValidationError](InvalidDomainName("invalidDomainName$."))
  }

  property("""validateInputName: should fail with a DomainValidationError for deletes
             |if validateHostName fails for an invalid domain name length""".stripMargin) {
    val invalidDomainName = Random.alphanumeric.take(256).mkString
    val change = DeleteRRSetChangeInput(invalidDomainName, RecordType.AAAA)
    val result = validateInputName(change, false)
    result should haveInvalid[DomainValidationError](InvalidDomainName(s"$invalidDomainName."))
      .and(haveInvalid[DomainValidationError](InvalidLength(s"$invalidDomainName.", 2, 255)))
  }

  property("""validateInputName: PTR should fail with InvalidIPAddress for deletes
             |if inputName is not a valid ipv4 or ipv6 address""".stripMargin) {
    val invalidIp = "invalidIp.111"
    val change = DeleteRRSetChangeInput(invalidIp, RecordType.PTR)
    val result = validateInputName(change, false)
    result should haveInvalid[DomainValidationError](InvalidIPAddress(invalidIp))
  }

  property("validateAddChangeInput: should succeed if single addChangeInput is good for A Record") {
    forAll(validAChangeGen) { input: AddChangeInput =>
      val result = validateAddChangeInput(input, false)
      result shouldBe valid
    }
  }

  property(
    "validateAddChangeInput: should succeed if single addChangeInput is good for AAAA Record"
  ) {
    forAll(validAAAAChangeGen) { input: AddChangeInput =>
      val result = validateAddChangeInput(input, false)
      result shouldBe valid
    }
  }

  property("""validateAddChangeInput: should fail with a DomainValidationError
             |if validateHostName fails for an invalid domain name""".stripMargin) {
    val change = AddChangeInput("invalidDomainName$", RecordType.A, ttl, AData("1.1.1.1"))
    val result = validateAddChangeInput(change, false)
    result should haveInvalid[DomainValidationError](InvalidDomainName("invalidDomainName$."))
  }

  property("""validateAddChangeInput: should fail with a DomainValidationError
             |if validateHostName fails for an invalid domain name length""".stripMargin) {
    val invalidDomainName = Random.alphanumeric.take(256).mkString
    val change = AddChangeInput(invalidDomainName, RecordType.A, ttl, AData("1.1.1.1"))
    val result = validateAddChangeInput(change, false)
    result should haveInvalid[DomainValidationError](InvalidDomainName(s"$invalidDomainName."))
      .and(haveInvalid[DomainValidationError](InvalidLength(s"$invalidDomainName.", 2, 255)))
  }

  property(
    "validateAddChangeInput: should fail with InvalidRange if validateRange fails for an addChangeInput"
  ) {
    forAll(choose[Long](0, 29)) { invalidTTL: Long =>
      val change =
        AddChangeInput("test.comcast.com.", RecordType.A, Some(invalidTTL), AData("1.1.1.1"))
      val result = validateAddChangeInput(change, false)
      result should haveInvalid[DomainValidationError](
        InvalidTTL(invalidTTL, DomainValidations.TTL_MIN_LENGTH, DomainValidations.TTL_MAX_LENGTH)
      )
    }
  }

  property("""validateAddChangeInput: should fail with InvalidIpv4Address
             |if validateRecordData fails for an invalid ipv4 address""".stripMargin) {
    val invalidIpv4 = "invalidIpv4:123"
    val change = AddChangeInput("test.comcast.com.", RecordType.A, ttl, AData(invalidIpv4))
    val result = validateAddChangeInput(change, false)
    result should haveInvalid[DomainValidationError](InvalidIpv4Address(invalidIpv4))
  }

  property("""validateAddChangeInput: should fail with InvalidIpv6Address
             |if validateRecordData fails for an invalid ipv6 address""".stripMargin) {
    val invalidIpv6 = "invalidIpv6:123"
    val change = AddChangeInput("test.comcast.com.", RecordType.AAAA, ttl, AAAAData(invalidIpv6))
    val result = validateAddChangeInput(change, false)
    result should haveInvalid[DomainValidationError](InvalidIpv6Address(invalidIpv6))
  }

  property("validateAddChangeInput: should fail if A inputName includes a reverse zone address") {
    val invalidInputName = "test.1.2.3.in-addr.arpa."
    val badAChange = AddChangeInput(invalidInputName, RecordType.A, ttl, AData("1.1.1.1"))
    val result = validateAddChangeInput(badAChange, false)
    result should haveInvalid[DomainValidationError](
      RecordInReverseZoneError(invalidInputName, RecordType.A.toString)
    )
  }

  property("validateAddChangeInput: should fail if AAAA inputName includes a reverse zone address") {
    val invalidInputName = "test.1.2.3.ip6.arpa."
    val badAAAAChange =
      AddChangeInput(invalidInputName, RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8"))
    val result = validateAddChangeInput(badAAAAChange, false)
    result should haveInvalid[DomainValidationError](
      RecordInReverseZoneError(invalidInputName, RecordType.AAAA.toString)
    )
  }

  property("""validateAddChangeInput: should fail with InvalidDomainName
             |if validateRecordData fails for invalid CNAME record data""".stripMargin) {
    val invalidCNAMERecordData = "$$$"
    val change =
      AddChangeInput(
        "test.comcast.com.",
        RecordType.CNAME,
        ttl,
        CNAMEData(Fqdn(invalidCNAMERecordData))
      )
    val result = validateAddChangeInput(change, false)

    result should haveInvalid[DomainValidationError](InvalidCname(s"$invalidCNAMERecordData.",false))
  }

  property("""validateAddChangeInput: should fail with Invalid CNAME
             |if validateRecordData fails for IPv4 Address in CNAME record data""".stripMargin) {
    val invalidCNAMERecordData = "1.2.3.4"
    val change =
      AddChangeInput(
        "test.comcast.com.",
        RecordType.CNAME,
        ttl,
        CNAMEData(Fqdn(invalidCNAMERecordData))
      )
    val result = validateAddChangeInput(change, false)

    result should haveInvalid[DomainValidationError](InvalidIPv4CName(s"Fqdn($invalidCNAMERecordData.)"))
  }

  property("""validateAddChangeInput: should fail with InvalidLength
             |if validateRecordData fails for invalid CNAME record data""".stripMargin) {
    val invalidCNAMERecordData = "s" * 256
    val change =
      AddChangeInput(
        "test.comcast.com.",
        RecordType.CNAME,
        ttl,
        CNAMEData(Fqdn(invalidCNAMERecordData))
      )
    val result = validateAddChangeInput(change, false)

    result should haveInvalid[DomainValidationError](
      InvalidLength(s"$invalidCNAMERecordData.", 2, 255)
    )
  }

  property("""validateAddChangeInput: PTR should fail with InvalidIPAddress
             |if inputName is not a valid ipv4 or ipv6 address""".stripMargin) {
    val invalidIp = "invalidip.111."
    val change = AddChangeInput(invalidIp, RecordType.PTR, ttl, PTRData(Fqdn("test.comcast.com")))
    val result = validateAddChangeInput(change, false)

    result should haveInvalid[DomainValidationError](InvalidIPAddress(invalidIp))
  }

  property("validateAddChangeInput: should fail with InvalidDomainName for invalid PTR record data") {
    val invalidPTRDname = "*invalidptrdname"
    val change = AddChangeInput("4.5.6.7", RecordType.PTR, ttl, PTRData(Fqdn(invalidPTRDname)))
    val result = validateAddChangeInput(change, false)

    result should haveInvalid[DomainValidationError](InvalidDomainName(s"$invalidPTRDname."))
  }

  property(
    "validateChangesWithContext: should properly validate with mix of success and failure inputs"
  ) {
    val authZone = okZone
    val reverseZone = okZone.copy(name = "2.0.192.in-addr.arpa.")
    val addNsRecord = AddChangeForValidation(
      okZone,
      "ns-add",
      AddChangeInput("ns-add.ok.", RecordType.NS, ttl, NSData(Fqdn("some.test.ns."))),
      defaultTtl
    )
    val addNaptrRecord = AddChangeForValidation(
      okZone,
      "naptr-add",
      AddChangeInput("naptr-add.ok.", RecordType.NAPTR, ttl, NAPTRData(1, 2, "S", "E2U+sip", "", Fqdn("target"))),
      defaultTtl
    )
    val addSrvRecord = AddChangeForValidation(
      okZone,
      "srv-add",
      AddChangeInput("srv-add.ok.", RecordType.SRV, ttl, SRVData(1, 2, 3, Fqdn("target.vinyldns."))),
      defaultTtl
    )
    val addA1 = AddChangeForValidation(
      authZone,
      "valid",
      AddChangeInput("valid.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )
    val existingA = AddChangeForValidation(
      authZone,
      "existingA",
      AddChangeInput("existingA.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )
    val existingCname = AddChangeForValidation(
      authZone,
      "existingCname",
      AddChangeInput("existingCname.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("cname"))),
      defaultTtl
    )
    val addA2 = AddChangeForValidation(
      okZone,
      "valid2",
      AddChangeInput("valid2.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )
    val duplicateNameCname = AddChangeForValidation(
      reverseZone,
      "199",
      AddChangeInput(
        "199.2.0.192.in-addr.arpa.",
        RecordType.CNAME,
        ttl,
        CNAMEData(Fqdn("199.192/30.2.0.192.in-addr.arpa"))
      ),
      defaultTtl
    )
    val duplicateNamePTR = AddChangeForValidation(
      reverseZone,
      "199",
      AddChangeInput("192.0.2.199", RecordType.PTR, ttl, PTRData(Fqdn("ptr.ok."))),
      defaultTtl
    )

    val existingRsList: List[RecordSet] = List(
      rsOk.copy(zoneId = existingA.zone.id, name = existingA.recordName),
      rsOk.copy(
        zoneId = existingCname.zone.id,
        name = existingCname.recordName,
        typ = RecordType.CNAME
      )
    )

    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(
          addNsRecord.validNel,
          addNaptrRecord.validNel,
          addSrvRecord.validNel,
          addA1.validNel,
          existingA.validNel,
          existingCname.validNel,
          addA2.validNel,
          duplicateNameCname.validNel,
          duplicateNamePTR.validNel
        ),
        ExistingRecordSets(existingRsList)
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) shouldBe valid
    result(2) shouldBe valid
    result(3) shouldBe valid
    result(4) should haveInvalid[DomainValidationError](
      RecordAlreadyExists(existingA.inputChange.inputName, existingA.inputChange.record, false)
    )
    result(5) should haveInvalid[DomainValidationError](
      RecordAlreadyExists(existingCname.inputChange.inputName, existingCname.inputChange.record, false)
    ).and(
      haveInvalid[DomainValidationError](
        CnameIsNotUniqueError(existingCname.inputChange.inputName, existingCname.inputChange.typ)
      )
    )
    result(6) shouldBe valid
    result(7) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch("199.2.0.192.in-addr.arpa.", RecordType.CNAME)
    )
    result(8) shouldBe valid
  }

  property("validateChangesWithContext: should succeed for valid update inputs") {
    // Existing records
    val deleteRRSet =
      rsOk.copy(name = "deleteRRSet", records = List(AData("1.1.1.1"), AData("1.1.1.2")))
    val deleteSingleEntry = deleteRRSet.copy(name = "deleteSingleEntry")
    val deleteSingleEntryAndRRSet = deleteRRSet.copy(name = "deleteSingleEntryAndRRSet")
    val deleteAllEntries = deleteRRSet.copy(name = "deleteAllEntries")
    val deleteAllEntriesAndRRSet = deleteRRSet.copy(name = "deleteAllEntriesAndRRSet")
    val deleteSingleEntryMultipleAdd = deleteRRSet.copy(name = "deleteSingleEntryMultipleAdd")

    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(
          makeAddUpdateRecord("deleteRRSet"), // DeleteRRSet
          makeDeleteUpdateDeleteRRSet("deleteRRSet"),
          makeAddUpdateRecord("deleteSingleEntry"), // Single entry
          makeDeleteUpdateDeleteRRSet("deleteSingleEntry", Some(AData("1.1.1.1"))),
          makeAddUpdateRecord("deleteSingleEntryAndRRSet"), // Single entry and DeleteRRSet
          makeDeleteUpdateDeleteRRSet("deleteSingleEntryAndRRSet", Some(AData("1.1.1.1"))),
          makeDeleteUpdateDeleteRRSet("deleteSingleEntryAndRRSet"),
          makeAddUpdateRecord("deleteAllEntries"), // Delete all entries
          makeDeleteUpdateDeleteRRSet("deleteAllEntries", Some(AData("1.1.1.1"))),
          makeDeleteUpdateDeleteRRSet("deleteAllEntries", Some(AData("1.1.1.2"))),
          makeAddUpdateRecord("deleteAllEntriesAndRRSet"), // Delete all entries and DeleteRRSet
          makeDeleteUpdateDeleteRRSet("deleteAllEntriesAndRRSet", Some(AData("1.1.1.1"))),
          makeDeleteUpdateDeleteRRSet("deleteAllEntriesAndRRSet", Some(AData("1.1.1.2"))),
          makeDeleteUpdateDeleteRRSet("deleteAllEntriesAndRRSet"),
          makeAddUpdateRecord("deleteSingleEntryMultipleAdd"), // Delete single entry and multiple adds
          makeAddUpdateRecord("deleteSingleEntryMultipleAdd", AData("2.3.4.5")),
          makeDeleteUpdateDeleteRRSet("deleteSingleEntryMultipleAdd", Some(AData("1.1.1.1")))
        ).map(_.validNel),
        ExistingRecordSets(
          List(
            deleteRRSet,
            deleteSingleEntry,
            deleteSingleEntryAndRRSet,
            deleteAllEntries,
            deleteAllEntriesAndRRSet,
            deleteSingleEntryMultipleAdd
          )
        )
      ),
      okAuth,
      false,
      None
    )

    result.foreach(_ shouldBe valid)
  }

  property("validateChangesWithContext: should succeed for valid delete inputs") {
    // Existing records
    val deleteRRSet =
      rsOk.copy(name = "deleteRRSet", records = List(AData("1.1.1.1"), AData("1.1.1.2")))
    val deleteSingleEntry = deleteRRSet.copy(name = "deleteSingleEntry")
    val deleteSingleEntryAndRRSet = deleteRRSet.copy(name = "deleteSingleEntryAndRRSet")
    val deleteAllEntries = deleteRRSet.copy(name = "deleteAllEntries")
    val deleteAllEntriesAndRRSet = deleteRRSet.copy(name = "deleteAllEntriesAndRRSet")

    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(
          makeDeleteUpdateDeleteRRSet("deleteRRSet"), // DeleteRRSet
          makeDeleteUpdateDeleteRRSet("deleteSingleEntry", Some(AData("1.1.1.1"))), // Single entry
          makeDeleteUpdateDeleteRRSet("deleteSingleEntryAndRRSet"), // Single entry and DeleteRRSet
          makeDeleteUpdateDeleteRRSet("deleteSingleEntryAndRRSet", Some(AData("1.1.1.1"))),
          makeDeleteUpdateDeleteRRSet("deleteAllEntries", Some(AData("1.1.1.1"))), // Delete all entries
          makeDeleteUpdateDeleteRRSet("deleteAllEntries", Some(AData("1.1.1.2"))),
          makeDeleteUpdateDeleteRRSet("deleteAllEntriesAndRRSet"), // Delete all entries and DeleteRRSet
          makeDeleteUpdateDeleteRRSet("deleteAllEntriesAndRRSet", Some(AData("1.1.1.1"))),
          makeDeleteUpdateDeleteRRSet("deleteAllEntriesAndRRSet", Some(AData("1.1.1.2")))
        ).map(_.validNel),
        ExistingRecordSets(
          List(
            deleteRRSet,
            deleteSingleEntry,
            deleteSingleEntryAndRRSet,
            deleteAllEntries,
            deleteAllEntriesAndRRSet
          )
        )
      ),
      okAuth,
      false,
      None
    )

    result.foreach(_ shouldBe valid)
  }

  property("validateChangesWithContext: should succeed for update for user with only write access") {
    val writeAcl = ACLRule(accessLevel = AccessLevel.Write, userId = Some(notAuth.userId))
    val existingRecord = rsOk.copy(name = "update", ttl = 300)
    val addUpdateA = AddChangeForValidation(
      okZone.addACLRule(writeAcl),
      "update",
      AddChangeInput("update.ok.", RecordType.A, ttl, AData("1.2.3.4")),
      defaultTtl
    )
    val deleteUpdateA = DeleteRRSetChangeForValidation(
      okZone.addACLRule(writeAcl),
      "update",
      DeleteRRSetChangeInput("update.ok.", RecordType.A)
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(addUpdateA.validNel, deleteUpdateA.validNel),
        ExistingRecordSets(List(existingRecord))
      ),
      notAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) shouldBe valid
  }

  property(
    "validateChangesWithContext: should fail for update if user does not have sufficient access"
  ) {
    val readAcl =
      ACLRule(accessLevel = AccessLevel.Read, userId = Some(notAuth.signedInUser.userName))
    val existingRecord = rsOk.copy(name = "update", ttl = 300)
    val addUpdateA = AddChangeForValidation(
      okZone.addACLRule(readAcl),
      "update",
      AddChangeInput("update.ok.", RecordType.A, ttl, AData("1.2.3.4")),
      defaultTtl
    )
    val deleteUpdateA = DeleteRRSetChangeForValidation(
      okZone.addACLRule(readAcl),
      "update",
      DeleteRRSetChangeInput("update.ok.", RecordType.A)
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(addUpdateA.validNel, deleteUpdateA.validNel),
        ExistingRecordSets(List(existingRecord))
      ),
      notAuth,
      false,
      None
    )

    result(0) should haveInvalid[DomainValidationError](
      UserIsNotAuthorizedError(
        notAuth.signedInUser.userName,
        addUpdateA.zone.adminGroupId,
        OwnerType.Zone,
        Some(addUpdateA.zone.email)
      )
    )

    result(1) should haveInvalid[DomainValidationError](
      UserIsNotAuthorizedError(
        notAuth.signedInUser.userName,
        deleteUpdateA.zone.adminGroupId,
        OwnerType.Zone,
        Some(deleteUpdateA.zone.email)
      )
    )
  }

  property("validateChangesWithContext: should fail for update if same record data is provided for add and delete") {
    val deleteRecord = makeDeleteUpdateDeleteRRSet("deleteRecord", Some(AData("1.2.3.4")))
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(
          makeAddUpdateRecord("deleteRecord"), // Record does not exist
          deleteRecord
        ).map(_.validNel),
        ExistingRecordSets(List(rsOk))
      ),
      okAuth,
      false,
      None
    )

    result(0) should haveInvalid[DomainValidationError](
      InvalidUpdateRequest(makeAddUpdateRecord("deleteRecord").inputChange.inputName)
    )
    result(1) should haveInvalid[DomainValidationError](
      InvalidUpdateRequest(deleteRecord.inputChange.inputName)
    )
  }

  property("validateChangesWithContext: should complete for update if record does not exist") {
    val deleteRRSet = makeDeleteUpdateDeleteRRSet("deleteRRSet")
    val deleteRecord = makeDeleteUpdateDeleteRRSet("deleteRecord", Some(AData("1.1.1.1")))
    val deleteNonExistentEntry = makeDeleteUpdateDeleteRRSet("ok", Some(AData("1.1.1.1")))
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(
          makeAddUpdateRecord("deleteRRSet"), // Record does not exist
          deleteRRSet,
          makeAddUpdateRecord("deleteRecord"), // Record does not exist
          deleteRecord,
          makeAddUpdateRecord("ok"), // Entry does not exist
          deleteNonExistentEntry
        ).map(_.validNel),
        ExistingRecordSets(List(rsOk))
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) shouldBe valid
    result(3) shouldBe valid
    result(4) shouldBe valid
    result(5) shouldBe valid
  }

  property(
    """validateChangesWithContext: should succeed for update in shared zone if user belongs to record
      | owner group""".stripMargin
  ) {
    val existingRecord =
      sharedZoneRecord.copy(
        name = "mx",
        typ = RecordType.MX,
        records = List(MXData(200, Fqdn("mx")))
      )
    val addUpdateA = AddChangeForValidation(
      sharedZone,
      "mx",
      AddChangeInput("mx.shared.", RecordType.MX, ttl, MXData(200, Fqdn("mx"))),
      defaultTtl
    )
    val deleteUpdateA =
      DeleteRRSetChangeForValidation(
        sharedZone,
        "mx",
        DeleteRRSetChangeInput("mx.shared.", RecordType.MX)
      )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(addUpdateA.validNel, deleteUpdateA.validNel),
        ExistingRecordSets(List(existingRecord))
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) shouldBe valid
  }

  property("""validateChangesWithContext: should succeed adding a record
             |if an existing CNAME with the same name exists but is being deleted""".stripMargin) {
    val existingCname = rsOk.copy(name = "deleteRRSet", typ = RecordType.CNAME)
    val existingCname2 =
      existingCname.copy(name = "deleteRecord", records = List(CNAMEData(Fqdn("cname.data."))))
    val deleteCnameRRSet = DeleteRRSetChangeForValidation(
      okZone,
      "deleteRRSet",
      DeleteRRSetChangeInput("deleteRRSet.ok.", RecordType.CNAME)
    )
    val deleteCnameEntry = DeleteRRSetChangeForValidation(
      okZone,
      "deleteRecord",
      DeleteRRSetChangeInput(
        "deleteRecord.ok.",
        RecordType.CNAME,
        Some(CNAMEData(Fqdn("cname.data.")))
      )
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(
          makeAddUpdateRecord("deleteRRSet"), // DeleteRRSet
          deleteCnameRRSet,
          makeAddUpdateRecord("deleteRecord"), // Delete record
          deleteCnameEntry
        ).map(_.validNel),
        ExistingRecordSets(List(existingCname, existingCname2))
      ),
      okAuth,
      false,
      None
    )

    result.foreach(_ shouldBe valid)
  }

  property(
    """validateChangesWithContext: should fail AddChangeForValidation with
      |CnameWithRecordNameAlreadyExists if record already exists as CNAME record type""".stripMargin
  ) {
    List(rsOk, aaaa, ptrIp4, ptrIp6).foreach { recordSet =>
      forAll(generateValidAddChangeForValidation(recordSet)) { input: AddChangeForValidation =>
        val existingCNAMERecord = recordSet.copy(
          zoneId = input.zone.id,
          name = input.recordName,
          typ = RecordType.CNAME,
          records = List(CNAMEData(Fqdn("cname")))
        )
        val newRecordSetList = existingCNAMERecord :: recordSetList
        val result = validateChangesWithContext(
          ChangeForValidationMap(List(input.validNel), ExistingRecordSets(newRecordSetList)),
          okAuth,
          false,
          None
        )

        result(0) should haveInvalid[DomainValidationError](
          CnameIsNotUniqueError(input.inputChange.inputName, RecordType.CNAME)
        )
      }
    }
  }

  property("validateChangesWithContext: should succeed if all inputs are good") {
    forAll(validAddChangeForValidationGen) { input: AddChangeForValidation =>
      val result =
        validateChangesWithContext(
          ChangeForValidationMap(List(input.validNel), ExistingRecordSets(recordSetList)),
          okAuth,
          false,
          None
        )

      result(0) shouldBe valid
    }
  }

  property(
    "validateChangesWithContext: should succeed if all inputs of different record types are good"
  ) {
    List(rsOk, aaaa, ptrIp4, ptrIp6).foreach { recordSet =>
      forAll(generateValidAddChangeForValidation(recordSet)) { input: AddChangeForValidation =>
        val result = validateChangesWithContext(
          ChangeForValidationMap(List(input.validNel), ExistingRecordSets(recordSetList)),
          okAuth,
          false,
          None
        )
        result(0) shouldBe valid
      }
    }
  }

  property(
    "validateChangesWithContext: should fail with RecordAlreadyExists if record already exists"
  ) {
    forAll(validAddChangeForValidationGen) { input: AddChangeForValidation =>
      val existingRecordSetList = rsOk.copy(
        zoneId = input.zone.id,
        name = input.recordName.toUpperCase
      ) :: recordSetList
      val result = validateChangesWithContext(
        ChangeForValidationMap(List(input.validNel), ExistingRecordSets(existingRecordSetList)),
        okAuth,
        false,
        None
      )

      result(0) should haveInvalid[DomainValidationError](
        RecordAlreadyExists(input.inputChange.inputName, input.inputChange.record, false)
      )
    }
  }

  property(
    "validateChangesWithContext: should succeed if CNAME record name already exists but is being deleted"
  ) {
    val addCname = AddChangeForValidation(
      validZone,
      "existingCname",
      AddChangeInput("existingCname.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("cname"))),
      defaultTtl
    )
    val deleteA = DeleteRRSetChangeForValidation(
      validZone,
      "existingCname",
      DeleteRRSetChangeInput("existingCname.ok.", RecordType.A)
    )
    val existingA = rsOk.copy(zoneId = addCname.zone.id, name = addCname.recordName)
    val newRecordSetList = existingA :: recordSetList
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(addCname.validNel, deleteA.validNel),
        ExistingRecordSets(newRecordSetList)
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) shouldBe valid
  }

  property("""validateChangesWithContext: should fail with CnameIsNotUniqueError
             |if CNAME record name already exists""".stripMargin) {
    val addCname = AddChangeForValidation(
      validZone,
      "existingCname",
      AddChangeInput("existingCname.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("cname"))),
      defaultTtl
    )
    val existingA = rsOk.copy(zoneId = addCname.zone.id, name = addCname.recordName)
    val newRecordSetList = existingA :: recordSetList
    val result = validateChangesWithContext(
      ChangeForValidationMap(List(addCname.validNel), ExistingRecordSets(newRecordSetList)),
      okAuth,
      false,
      None
    )

    result(0) should haveInvalid[DomainValidationError](
      CnameIsNotUniqueError(addCname.inputChange.inputName, existingA.typ)
    )
  }

  property("""validateChangesWithContext: should succeed for CNAME record
             |if there's a duplicate PTR ipv4 record that is being deleted""".stripMargin) {
    val addCname = AddChangeForValidation(
      validIp4ReverseZone,
      "30",
      AddChangeInput("30.2.0.192.in-addr.arpa.", RecordType.CNAME, ttl, CNAMEData(Fqdn("cname"))),
      defaultTtl
    )
    val deletePtr = DeleteRRSetChangeForValidation(
      validIp4ReverseZone,
      "30",
      DeleteRRSetChangeInput("192.0.2.30", RecordType.PTR)
    )
    val ptr4 = ptrIp4.copy(zoneId = validIp4ReverseZone.id)
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(addCname.validNel, deletePtr.validNel),
        ExistingRecordSets(List(ptr4))
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) shouldBe valid
  }

  property("""validateChangesWithContext: should fail with CnameIsNotUniqueError for CNAME record
             |if there's a duplicate PTR ipv6 record""".stripMargin) {
    val addCname = AddChangeForValidation(
      validZone,
      "0.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0",
      AddChangeInput(
        "0.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.1.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.",
        RecordType.CNAME,
        ttl,
        CNAMEData(Fqdn("cname"))
      ),
      defaultTtl
    )
    val existingRecordPTR = ptrIp6.copy(zoneId = addCname.zone.id, name = addCname.recordName)
    val result = validateChangesWithContext(
      ChangeForValidationMap(List(addCname.validNel), ExistingRecordSets(List(existingRecordPTR))),
      okAuth,
      false,
      None
    )

    result(0) should haveInvalid[DomainValidationError](
      CnameIsNotUniqueError(addCname.inputChange.inputName, existingRecordPTR.typ)
    )
  }

  property("""validateChangesWithContext: CNAME record should pass
             |if no other changes in batch change have same record name""".stripMargin) {
    val addA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )
    val addAAAA = AddChangeForValidation(
      okZone,
      "testAAAA",
      AddChangeInput("testAAAA.ok.", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8")),
      defaultTtl
    )
    val addCname = AddChangeForValidation(
      okZone,
      "new",
      AddChangeInput("new.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("hey.ok.com."))),
      defaultTtl
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(addA.validNel, addAAAA.validNel, addCname.validNel),
        ExistingRecordSets(List())
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) shouldBe valid
    result(2) shouldBe valid
  }

  property("""validateChangesWithContext: CNAME record should fail
             |if another add change in batch change has the same record name""".stripMargin) {
    val addA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )
    val addDuplicateCname = AddChangeForValidation(
      okZone,
      "testAAAA",
      AddChangeInput("testAAAA.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("hey.ok.com."))),
      defaultTtl
    )
    val addAAAA = AddChangeForValidation(
      okZone,
      "testAAAA",
      AddChangeInput("testAAAA.ok.", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8")),
      defaultTtl
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(addA.validNel, addAAAA.validNel, addDuplicateCname.validNel),
        ExistingRecordSets(List())
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) shouldBe valid
    result(2) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch(
        addDuplicateCname.inputChange.inputName,
        addDuplicateCname.inputChange.typ
      )
    )
  }

  property("""validateChangesWithContext: both CNAME records should fail
             |if there are duplicate CNAME add change inputs""".stripMargin) {
    val addA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )
    val addCname = AddChangeForValidation(
      okZone,
      "testAAAA",
      AddChangeInput("testAAAA.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("hey.ok.com."))),
      defaultTtl
    )
    val addDuplicateCname = AddChangeForValidation(
      okZone,
      "testAAAA",
      AddChangeInput("testAAAA.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("hey2.ok.com."))),
      defaultTtl
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(addA.validNel, addCname.validNel, addDuplicateCname.validNel),
        ExistingRecordSets(List())
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch(addCname.inputChange.inputName, addCname.inputChange.typ)
    )
    result(2) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch(
        addDuplicateCname.inputChange.inputName,
        addDuplicateCname.inputChange.typ
      )
    )
  }

  property("""validateChangesWithContext: both PTR records should succeed
             |if there are duplicate PTR add change inputs""".stripMargin) {
    val addA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )
    val addPtr = AddChangeForValidation(
      okZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData(Fqdn("test.ok."))),
      defaultTtl
    )
    val addDuplicatePtr = AddChangeForValidation(
      okZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData(Fqdn("hey.ok.com."))),
      defaultTtl
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(addA.validNel, addPtr.validNel, addDuplicatePtr.validNel),
        ExistingRecordSets(List())
      ),
      okAuth,
      false,
      None
    )

    result.map(_ shouldBe valid)
  }

  property("""validateChangesWithContext: should succeed for AddChangeForValidation
             |if user has group admin access""".stripMargin) {
    val addA = AddChangeForValidation(
      validZone,
      "valid",
      AddChangeInput("valid.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )
    val result =
      validateChangesWithContext(
        ChangeForValidationMap(List(addA.validNel), ExistingRecordSets(recordSetList)),
        okAuth,
        false,
        None
      )

    result(0) shouldBe valid
  }

  property(
    "validateChangesWithContext: should fail for AddChangeForValidation if user is a superUser with no other access"
  ) {
    val addA = AddChangeForValidation(
      validZone,
      "valid",
      AddChangeInput("valid.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(List(addA.validNel), ExistingRecordSets(recordSetList)),
      AuthPrincipal(superUser, Seq.empty),
      false,
      None
    )

    result(0) should haveInvalid[DomainValidationError](
      UserIsNotAuthorizedError(
        superUser.userName,
        addA.zone.adminGroupId,
        OwnerType.Zone,
        Some(addA.zone.email)
      )
    )
  }

  property(
    "validateChangesWithContext: should succeed for AddChangeForValidation if user has necessary ACL rule"
  ) {
    val addA = AddChangeForValidation(
      validZone.addACLRule(ACLRule(accessLevel = AccessLevel.Write, userId = Some(notAuth.userId))),
      "valid",
      AddChangeInput("valid.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )
    val result =
      validateChangesWithContext(
        ChangeForValidationMap(List(addA.validNel), ExistingRecordSets(recordSetList)),
        notAuth,
        false,
        None
      )

    result(0) shouldBe valid
  }

  property(
    """validateChangesWithContext: should fail AddChangeForValidation with UserIsNotAuthorized if user
      |is not a superuser, doesn't have group admin access, or doesn't have necessary ACL rule""".stripMargin
  ) {
    forAll(validAddChangeForValidationGen) { input: AddChangeForValidation =>
      val result =
        validateChangesWithContext(
          ChangeForValidationMap(List(input.validNel), ExistingRecordSets(recordSetList)),
          notAuth,
          false,
          None
        )

      result(0) should haveInvalid[DomainValidationError](
        UserIsNotAuthorizedError(
          notAuth.signedInUser.userName,
          input.zone.adminGroupId,
          OwnerType.Zone,
          Some(input.zone.email)
        )
      )
    }
  }

  property("""validateChangesWithContext: should fail with RecordNameNotUniqueInBatch for PTR record
             |if valid CNAME with same name exists in batch""".stripMargin) {
    val addCname = AddChangeForValidation(
      validZone,
      "existing",
      AddChangeInput("existing.ok.", RecordType.CNAME, ttl, PTRData(Fqdn("orders.vinyldns."))),
      defaultTtl
    )
    val addPtr = AddChangeForValidation(
      validZone,
      "existing",
      AddChangeInput("existing.ok.", RecordType.PTR, ttl, CNAMEData(Fqdn("ptrdname."))),
      defaultTtl
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(List(addCname.validNel, addPtr.validNel), ExistingRecordSets(List())),
      okAuth,
      false,
      None
    )

    result(0) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch("existing.ok.", RecordType.CNAME)
    )
  }

  property(
    "validateChangesWithContext: should succeed for DeleteChangeForValidation if record exists"
  ) {
    val deleteA = DeleteRRSetChangeForValidation(
      validZone,
      "Record-exists",
      DeleteRRSetChangeInput("record-exists.ok.", RecordType.A)
    )
    val existingDeleteRecord =
      rsOk.copy(zoneId = deleteA.zone.id, name = deleteA.recordName.toLowerCase)
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteA.validNel),
        ExistingRecordSets(List(existingDeleteRecord))
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
  }

  property(
    """validateChangesWithContext: should complete DeleteChangeForValidation
      |if record does not exist""".stripMargin
  ) {
    val deleteRRSet = makeDeleteUpdateDeleteRRSet("record-does-not-exist")
    val deleteRecord =
      makeDeleteUpdateDeleteRRSet("record-also-does-not-exist", Some(AData("1.1.1.1")))
    val result =
      validateChangesWithContext(
        ChangeForValidationMap(
          List(deleteRRSet.validNel, deleteRecord.validNel),
          ExistingRecordSets(recordSetList)
        ),
        okAuth,
        false,
        None
      )

    result(0) shouldBe valid
    result(1) shouldBe valid
  }

  property("""validateChangesWithContext: should succeed for DeleteChangeForValidation
             |if record set status is Active""".stripMargin) {
    val deleteA = DeleteRRSetChangeForValidation(
      validZone,
      "Active-record-status",
      DeleteRRSetChangeInput("active-record-status", RecordType.A)
    )
    val existingDeleteRecord = rsOk.copy(
      zoneId = deleteA.zone.id,
      name = deleteA.recordName.toLowerCase,
      status = RecordSetStatus.Active
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteA.validNel),
        ExistingRecordSets(List(existingDeleteRecord))
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
  }

  property("""validateChangesWithContext: should succeed for DeleteChangeForValidation
             |if user has group admin access"""".stripMargin) {
    val deleteA =
      DeleteRRSetChangeForValidation(
        validZone,
        "valid",
        DeleteRRSetChangeInput("valid.ok.", RecordType.A)
      )
    val existingDeleteRecord = rsOk.copy(zoneId = deleteA.zone.id, name = deleteA.recordName)
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteA.validNel),
        ExistingRecordSets(List(existingDeleteRecord))
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
  }

  property(""" validateChangesWithContext: should fail for DeleteChangeForValidation
             | if user is superUser with no other access""".stripMargin) {
    val deleteA =
      DeleteRRSetChangeForValidation(
        validZone,
        "valid",
        DeleteRRSetChangeInput("valid.ok.", RecordType.A)
      )
    val existingDeleteRecord = rsOk.copy(zoneId = deleteA.zone.id, name = deleteA.recordName)
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteA.validNel),
        ExistingRecordSets(List(existingDeleteRecord))
      ),
      AuthPrincipal(superUser, Seq.empty),
      false,
      None
    )

    result(0) should haveInvalid[DomainValidationError](
      UserIsNotAuthorizedError(
        superUser.userName,
        deleteA.zone.adminGroupId,
        OwnerType.Zone,
        Some(deleteA.zone.email)
      )
    )
  }

  property(
    "validateChangesWithContext: should succeed for DeleteChangeForValidation if user has necessary ACL rule"
  ) {
    val deleteA = DeleteRRSetChangeForValidation(
      validZone.addACLRule(
        ACLRule(accessLevel = AccessLevel.Delete, userId = Some(notAuth.userId))
      ),
      "valid",
      DeleteRRSetChangeInput("valid.ok.", RecordType.A)
    )
    val existingDeleteRecord = rsOk.copy(zoneId = deleteA.zone.id, name = deleteA.recordName)
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteA.validNel),
        ExistingRecordSets(List(existingDeleteRecord))
      ),
      notAuth,
      false,
      None
    )

    result(0) shouldBe valid
  }

  property(
    """validateChangesWithContext: should fail DeleteChangeForValidation with UserIsNotAuthorized if user
      |is not a superuser, doesn't have group admin access, or doesn't have necessary ACL rule""".stripMargin
  ) {
    val deleteA = DeleteRRSetChangeForValidation(
      validZone.addACLRule(ACLRule(accessLevel = AccessLevel.Write, userId = Some(notAuth.userId))),
      "valid",
      DeleteRRSetChangeInput("valid.ok.", RecordType.A)
    )
    val existingDeleteRecord = rsOk.copy(zoneId = deleteA.zone.id, name = deleteA.recordName)
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteA.validNel),
        ExistingRecordSets(List(existingDeleteRecord))
      ),
      notAuth,
      false,
      None
    )

    result(0) should haveInvalid[DomainValidationError](
      UserIsNotAuthorizedError(
        notAuth.signedInUser.userName,
        deleteA.zone.adminGroupId,
        OwnerType.Zone,
        Some(deleteA.zone.email)
      )
    )
  }

  property("""validateChangesWithContext: should properly process batch that contains
             |a CNAME and different type record with the same name""".stripMargin) {
    val addDuplicateA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.com.", RecordType.A, ttl, AData("10.1.1.1")),
      defaultTtl
    )
    val addDuplicateCname = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.com.", RecordType.CNAME, ttl, CNAMEData(Fqdn("thing.com."))),
      defaultTtl
    )

    val deleteA =
      DeleteRRSetChangeForValidation(
        okZone,
        "delete",
        DeleteRRSetChangeInput("delete.ok.", RecordType.A)
      )
    val addCname = AddChangeForValidation(
      okZone,
      "delete",
      AddChangeInput("delete.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("thing.com."))),
      defaultTtl
    )
    val addA = AddChangeForValidation(
      okZone,
      "delete-this",
      AddChangeInput("delete-this.ok.", RecordType.A, ttl, AData("10.1.1.1")),
      defaultTtl
    )
    val deleteCname = DeleteRRSetChangeForValidation(
      okZone,
      "delete",
      DeleteRRSetChangeInput("delete-this.ok.", RecordType.CNAME)
    )
    val existingA = rsOk.copy(zoneId = deleteA.zone.id, name = deleteA.recordName)
    val existingCname =
      rsOk.copy(zoneId = deleteCname.zone.id, name = deleteCname.recordName, typ = RecordType.CNAME)
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(
          addDuplicateA.validNel,
          addDuplicateCname.validNel,
          deleteA.validNel,
          addCname.validNel,
          addA.validNel,
          deleteCname.validNel
        ),
        ExistingRecordSets(List(existingA, existingCname))
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch(addDuplicateCname.inputChange.inputName, RecordType.CNAME)
    )
    result(2) shouldBe valid
    result(3) shouldBe valid
    result(4) shouldBe valid
    result(5) shouldBe valid
  }

  property("validateChangesWithContext: should succeed with add CNAME, delete A of the same name") {
    val existingA = rsOk.copy(name = "new")

    val deleteA =
      DeleteRRSetChangeForValidation(okZone, "new", DeleteRRSetChangeInput("new.ok.", RecordType.A))
    val addA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )
    val addAAAA = AddChangeForValidation(
      okZone,
      "testAAAA",
      AddChangeInput("testAAAA.ok.", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8")),
      defaultTtl
    )
    val addCname = AddChangeForValidation(
      okZone,
      "new",
      AddChangeInput("new.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("hey.ok."))),
      defaultTtl
    )
    val addPtr = AddChangeForValidation(
      okZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData(Fqdn("test.ok."))),
      defaultTtl
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteA.validNel, addA.validNel, addAAAA.validNel, addCname.validNel, addPtr.validNel),
        ExistingRecordSets(List(existingA))
      ),
      okAuth,
      false,
      None
    )
    result.map(_ shouldBe valid)
  }

  property(
    "validateChangesWithContext: should succeed with add AAAA, delete CNAME of the same name"
  ) {
    val existingCname =
      rsOk.copy(name = "new", typ = RecordType.CNAME, records = List(CNAMEData(Fqdn("hey.ok."))))

    val deleteCname =
      DeleteRRSetChangeForValidation(
        okZone,
        "new",
        DeleteRRSetChangeInput("new.ok.", RecordType.CNAME)
      )
    val addA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )
    val addAAAA = AddChangeForValidation(
      okZone,
      "new",
      AddChangeInput("new.ok.", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8")),
      defaultTtl
    )
    val addPtr = AddChangeForValidation(
      okZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData(Fqdn("test.ok."))),
      defaultTtl
    )

    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteCname.validNel, addA.validNel, addAAAA.validNel, addPtr.validNel),
        ExistingRecordSets(List(existingCname))
      ),
      okAuth,
      false,
      None
    )
    result.map(_ shouldBe valid)
  }

  property(
    "validateChangesWithContext: should succeed with delete and add (update) of same CNAME input name"
  ) {
    val existingCname =
      rsOk.copy(name = "new", typ = RecordType.CNAME, records = List(CNAMEData(Fqdn("hey.ok."))))

    val deleteCname =
      DeleteRRSetChangeForValidation(
        okZone,
        "new",
        DeleteRRSetChangeInput("new.ok.", RecordType.CNAME)
      )
    val addCname = AddChangeForValidation(
      okZone,
      "new",
      AddChangeInput("new.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("updateData.com"))),
      defaultTtl
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteCname.validNel, addCname.validNel),
        ExistingRecordSets(List(existingCname))
      ),
      okAuth,
      false,
      None
    )
    result.map(_ shouldBe valid)
  }

  property("validateChangesWithContext: should fail on CNAME update including multiple adds") {
    val existingCname = rsOk.copy(
      zoneId = okZone.id,
      name = "name-conflict",
      typ = RecordType.CNAME,
      records = List(CNAMEData(Fqdn("existing.cname.")))
    )

    val deleteUpdateCname = DeleteRRSetChangeForValidation(
      okZone,
      "name-conflict",
      DeleteRRSetChangeInput("existing.ok.", RecordType.CNAME)
    )
    val addUpdateCname = AddChangeForValidation(
      okZone,
      "name-conflict",
      AddChangeInput("add.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("updated.cname."))),
      defaultTtl
    )
    val addCname = AddChangeForValidation(
      okZone,
      "name-conflict",
      AddChangeInput("add.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("new.add.cname."))),
      defaultTtl
    )

    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteUpdateCname.validNel, addUpdateCname.validNel, addCname.validNel),
        ExistingRecordSets(List(existingCname))
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch("add.ok.", RecordType.CNAME)
    )
    result(2) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch("add.ok.", RecordType.CNAME)
    )
  }

  property("validateChangesWithContext: should succeed with add CNAME, delete PTR of the same name") {
    val existingPtr = rsOk.copy(
      zoneId = validIp4ReverseZone.id,
      name = "193",
      typ = RecordType.PTR,
      records = List(PTRData(Fqdn("hey.there.")))
    )

    val deletePtr = DeleteRRSetChangeForValidation(
      validIp4ReverseZone,
      "193",
      DeleteRRSetChangeInput("192.0.2.193", RecordType.PTR)
    )
    val addCname = AddChangeForValidation(
      validIp4ReverseZone,
      "193",
      AddChangeInput("test.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("hey2.there."))),
      defaultTtl
    )

    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deletePtr.validNel, addCname.validNel),
        ExistingRecordSets(List(existingPtr))
      ),
      okAuth,
      false,
      None
    )
    result.map(_ shouldBe valid)
  }

  property(
    "validateChangesWithContext: should succeed with delete and add (update) of same PTR input name"
  ) {
    val existingPtr = rsOk.copy(
      zoneId = validIp4ReverseZone.id,
      name = "193",
      typ = RecordType.PTR,
      records = List(PTRData(Fqdn("hey.ok.")))
    )

    val deletePtr = DeleteRRSetChangeForValidation(
      validIp4ReverseZone,
      "193",
      DeleteRRSetChangeInput("192.0.2.193", RecordType.PTR)
    )
    val addPtr = AddChangeForValidation(
      validIp4ReverseZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData(Fqdn("updateData.com"))),
      defaultTtl
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deletePtr.validNel, addPtr.validNel),
        ExistingRecordSets(List(existingPtr))
      ),
      okAuth,
      false,
      None
    )
    result.map(_ shouldBe valid)
  }

  property("validateChangesWithContext: should succeed on PTR update including multiple adds") {
    val existingPtr = rsOk.copy(
      zoneId = validIp4ReverseZone.id,
      name = "193",
      typ = RecordType.PTR,
      records = List(PTRData(Fqdn("existing.ptr.")))
    )

    val deleteUpdatePtr = DeleteRRSetChangeForValidation(
      validIp4ReverseZone,
      "193",
      DeleteRRSetChangeInput("192.0.2.193", RecordType.PTR)
    )
    val addUpdatePtr = AddChangeForValidation(
      validIp4ReverseZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData(Fqdn("updated.ptr."))),
      defaultTtl
    )
    val addPtr = AddChangeForValidation(
      validIp4ReverseZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData(Fqdn("new.add.ptr."))),
      defaultTtl
    )

    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteUpdatePtr.validNel, addUpdatePtr.validNel, addPtr.validNel),
        ExistingRecordSets(List(existingPtr))
      ),
      okAuth,
      false,
      None
    )

    result.map(_ shouldBe valid)
  }

  property("validateAddChangeInput: should succeed for a valid TXT addChangeInput") {
    val input = AddChangeInput("txt.ok.", RecordType.TXT, ttl, TXTData("test"))
    val result = validateAddChangeInput(input, false)
    result shouldBe valid
  }

  property("validateAddChangeInput: should fail for a TXT addChangeInput with empty TXTData") {
    val input = AddChangeInput("txt.ok.", RecordType.TXT, ttl, TXTData(""))
    val result = validateAddChangeInput(input, false)
    result should haveInvalid[DomainValidationError](InvalidLength("", 1, 64764))
  }

  property(
    "validateAddChangeInput: should fail for a TXT addChangeInput with TXTData that is too many characters"
  ) {
    val txtData = "x" * 64765
    val input = AddChangeInput("txt.ok.", RecordType.TXT, ttl, TXTData(txtData))
    val result = validateAddChangeInput(input, false)
    result should haveInvalid[DomainValidationError](InvalidLength(txtData, 1, 64764))
  }

  property("validateAddChangeInput: should succeed for a valid MX addChangeInput") {
    val input = AddChangeInput("mx.ok.", RecordType.MX, ttl, MXData(1, Fqdn("foo.bar.")))
    val result = validateAddChangeInput(input, false)
    result shouldBe valid
  }

  property("validateAddChangeInput: should fail for a MX addChangeInput with invalid preference") {
    val inputSmall = AddChangeInput("mx.ok.", RecordType.MX, ttl, MXData(-1, Fqdn("foo.bar.")))
    val inputLarge = AddChangeInput("mx.ok.", RecordType.MX, ttl, MXData(1000000, Fqdn("foo.bar.")))
    val resultSmall = validateAddChangeInput(inputSmall, false)
    val resultLarge = validateAddChangeInput(inputLarge, false)

    resultSmall should haveInvalid[DomainValidationError](
      InvalidMX_NAPTR_SRVData(
        -1,
        DomainValidations.INTEGER_MIN_VALUE,
        DomainValidations.INTEGER_MAX_VALUE,
        "preference",
        "MX"
      )
    )
    resultLarge should haveInvalid[DomainValidationError](
      InvalidMX_NAPTR_SRVData(
        1000000,
        DomainValidations.INTEGER_MIN_VALUE,
        DomainValidations.INTEGER_MAX_VALUE,
        "preference",
        "MX"
      )
    )
  }

  property("validateAddChangeInput: should fail for a MX addChangeInput with invalid exchange") {
    val input = AddChangeInput("mx.ok.", RecordType.MX, ttl, MXData(1, Fqdn("foo$.bar.")))
    val result = validateAddChangeInput(input, false)
    result should haveInvalid[DomainValidationError](InvalidDomainName("foo$.bar."))
  }

  property(
    "validateAddChangeInput: should fail for a MX addChangeInput with invalid preference and exchange"
  ) {
    val input = AddChangeInput("mx.ok.", RecordType.MX, ttl, MXData(-1, Fqdn("foo$.bar.")))
    val result = validateAddChangeInput(input, false)
    result should haveInvalid[DomainValidationError](
      InvalidMX_NAPTR_SRVData(
        -1,
        DomainValidations.INTEGER_MIN_VALUE,
        DomainValidations.INTEGER_MAX_VALUE,
        "preference",
        "MX"
      )
    )
    result should haveInvalid[DomainValidationError](InvalidDomainName("foo$.bar."))
  }

  property(
    "validateDeleteChangeInput: should succeed for valid data when no record data is passed in"
  ) {
    val input = DeleteRRSetChangeInput("a.ok.", RecordType.A, None)
    validateDeleteRRSetChangeInput(input, false) shouldBe valid
  }

  property("validateDeleteChangeInput: should succeed for valid data when record data is passed in") {
    val input = DeleteRRSetChangeInput("a.ok.", RecordType.A, Some(AData("1.1.1.1")))
    validateDeleteRRSetChangeInput(input, false) shouldBe valid
  }

  property("validateDeleteChangeInput: should fail when invalid record data is passed in") {
    val invalidIp = "invalid IP address"
    val input = DeleteRRSetChangeInput("a.ok.", RecordType.A, Some(AData(invalidIp)))
    val result = validateDeleteRRSetChangeInput(input, false)
    result should haveInvalid[DomainValidationError](InvalidIpv4Address(invalidIp))
  }

  property("validateChangesWithContext: should Success if MX record in batch already exists") {
    val existingMX = rsOk.copy(
      zoneId = okZone.id,
      name = "name-conflict",
      typ = RecordType.MX,
      records = List(MXData(1, Fqdn("foo.bar.")))
    )
    val addMX = AddChangeForValidation(
      okZone,
      "name-conflict",
      AddChangeInput("name-conflict", RecordType.MX, ttl, MXData(1, Fqdn("foo.bar."))),
      defaultTtl
    )

    val result =
      validateChangesWithContext(
        ChangeForValidationMap(List(addMX.validNel), ExistingRecordSets(List(existingMX))),
        okAuth,
        false,
        None
      )
    result(0) shouldBe valid
  }

  property("validateChangesWithContext: should succeed if duplicate MX records in batch") {
    val addMx = AddChangeForValidation(
      okZone,
      "name-conflict",
      AddChangeInput("name-conflict", RecordType.MX, ttl, MXData(1, Fqdn("foo.bar."))),
      defaultTtl
    )
    val addMx2 = AddChangeForValidation(
      okZone,
      "name-conflict",
      AddChangeInput("name-conflict", RecordType.MX, ttl, MXData(2, Fqdn("foo.bar."))),
      defaultTtl
    )

    val result = validateChangesWithContext(
      ChangeForValidationMap(List(addMx.validNel, addMx2.validNel), ExistingRecordSets(List())),
      okAuth,
      false,
      None
    )
    result(0) shouldBe valid
  }

  property("validateChangesWithContext: should succeed if MX already exists and is deleted first") {
    val existingMx = rsOk.copy(
      zoneId = okZone.id,
      name = "name-conflict",
      typ = RecordType.MX,
      records = List(MXData(1, Fqdn("foo.bar.")))
    )
    val deleteMx = DeleteRRSetChangeForValidation(
      okZone,
      "name-conflict",
      DeleteRRSetChangeInput("name-conflict", RecordType.MX)
    )
    val addMx = AddChangeForValidation(
      okZone,
      "name-conflict",
      AddChangeInput("name-conflict", RecordType.MX, ttl, MXData(1, Fqdn("foo.bar."))),
      defaultTtl
    )

    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteMx.validNel, addMx.validNel),
        ExistingRecordSets(List(existingMx))
      ),
      okAuth,
      false,
      None
    )
    result(0) shouldBe valid
  }

  property("validateChangesWithContext: should properly validate changes with owner group ID") {
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(
          createPrivateAddChange,
          createSharedAddChange,
          updatePrivateAddChange,
          updatePrivateDeleteChange,
          updateSharedAddChange,
          updateSharedDeleteChange,
          deletePrivateChange,
          deleteSharedChange
        ).map(_.validNel),
        ExistingRecordSets(
          List(
            rsOk.copy(name = "private-update"),
            sharedZoneRecord.copy(name = "shared-update"),
            rsOk.copy(name = "private-delete"),
            sharedZoneRecord.copy(name = "shared-delete")
          )
        )
      ),
      AuthPrincipal(okUser, Seq(abcGroup.id, okGroup.id)),
      false,
      Some("some-owner-group-id")
    )

    result.foreach(_ shouldBe valid)
  }

  property("validateChangesWithContext: should properly validate changes without owner group ID") {
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(
          createPrivateAddChange,
          createSharedAddChange,
          updatePrivateAddChange,
          updatePrivateDeleteChange,
          updateSharedAddChange,
          updateSharedDeleteChange,
          deletePrivateChange,
          deleteSharedChange
        ).map(_.validNel),
        ExistingRecordSets(
          List(
            rsOk.copy(name = "private-update"),
            sharedZoneRecordNoOwnerGroup.copy(name = "shared-update"),
            rsOk.copy(name = "private-delete"),
            sharedZoneRecord.copy(name = "shared-delete")
          )
        )
      ),
      AuthPrincipal(okUser, Seq(abcGroup.id, okGroup.id)),
      false,
      None
    )

    result(0) shouldBe valid
    result(1) should
      haveInvalid[DomainValidationError](
        MissingOwnerGroupId(createSharedAddChange.recordName, createSharedAddChange.zone.name)
      )
    result(2) shouldBe valid
    result(3) shouldBe valid
    result(4) should
      haveInvalid[DomainValidationError](
        MissingOwnerGroupId(updateSharedAddChange.recordName, updateSharedAddChange.zone.name)
      )
    result(5) shouldBe valid
    result(6) shouldBe valid
    result(7) shouldBe valid
  }

  property(
    "validateChangesWithContext: should fail deleting record for normal user not in owner group in shared zone"
  ) {
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteSharedChange.validNel),
        ExistingRecordSets(List(sharedZoneRecord.copy(name = "shared-delete")))
      ),
      dummyAuth,
      false,
      None
    )

    result(0) should
      haveInvalid[DomainValidationError](
        UserIsNotAuthorizedError(
          dummyAuth.signedInUser.userName,
          sharedZoneRecord.ownerGroupId.get,
          OwnerType.Record,
          None
        )
      )
  }

  property(
    "validateChangesWithContext: should delete record without owner group for normal user in shared zone"
  ) {
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteSharedChange.validNel),
        ExistingRecordSets(List(sharedZoneRecord.copy(name = "shared-delete")))
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
  }

  property("validateChangesWithContext: should delete record for zone admin in shared zone") {
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(deleteSharedChange.validNel),
        ExistingRecordSets(List(sharedZoneRecord.copy(name = "shared-delete")))
      ),
      sharedAuth,
      false,
      None
    )

    result(0) shouldBe valid
  }

  property("validateChangesWithContext: succeed update/delete to a multi record existing RecordSet") {
    val existing = List(
      sharedZoneRecord.copy(
        name = updateSharedAddChange.recordName,
        records = List(AAAAData("1::1"), AAAAData("2::2"))
      ),
      sharedZoneRecord.copy(
        name = deleteSharedChange.recordName,
        records = List(AAAAData("1::1"), AAAAData("2::2"))
      ),
      rsOk.copy(name = updatePrivateAddChange.recordName),
      rsOk.copy(name = deletePrivateChange.recordName)
    )

    val result = underTest.validateChangesWithContext(
      ChangeForValidationMap(
        List(
          updateSharedAddChange.validNel,
          deleteSingleRecordChange.validNel,
          deleteSharedChange.validNel,
          updatePrivateAddChange.validNel,
          updatePrivateDeleteChange.validNel,
          deletePrivateChange.validNel
        ),
        ExistingRecordSets(existing)
      ),
      okAuth,
      false,
      Some(okGroup.id)
    )

    result(0) shouldBe valid
    result(1) shouldBe valid
    result(2) shouldBe valid
    // non duplicate
    result(3) shouldBe valid
    result(4) shouldBe valid
    result(5) shouldBe valid
  }

  property("validateChangesWithContext: succeed on add/update to a multi record") {
    val existing = List(
      sharedZoneRecord.copy(name = updateSharedAddChange.recordName)
    )

    val update1 = updateSharedAddChange.copy(
      inputChange =
        AddChangeInput("shared-update.shared", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8"))
    )
    val update2 = updateSharedAddChange.copy(
      inputChange = AddChangeInput("shared-update.shared", RecordType.AAAA, ttl, AAAAData("1::1"))
    )
    val add1 = createSharedAddChange.copy(
      inputChange = AddChangeInput("shared-add.shared", RecordType.A, ttl, AData("1.2.3.4"))
    )
    val add2 = createSharedAddChange.copy(
      inputChange = AddChangeInput("shared-add.shared", RecordType.A, ttl, AData("5.6.7.8"))
    )

    val result = underTest.validateChangesWithContext(
      ChangeForValidationMap(
        List(
          updateSharedDeleteChange.validNel,
          update1.validNel,
          update2.validNel,
          add1.validNel,
          add2.validNel,
          updatePrivateAddChange.validNel
        ),
        ExistingRecordSets(existing)
      ),
      okAuth,
      false,
      Some(okGroup.id)
    )

    result(0) shouldBe valid
    result(1) shouldBe valid
    result(2) shouldBe valid
    result(3) shouldBe valid
    result(4) shouldBe valid
    // non duplicate
    result(5) shouldBe valid
  }

  property(
    """validateChangesWithContext: should fail validateAddWithContext with
      |ZoneDiscoveryError if new record is dotted host but not a TXT record type""".stripMargin
  ) {
    val addA = AddChangeForValidation(
      okZone,
      "dotted.a",
      AddChangeInput("dotted.a.ok.", RecordType.A, ttl, AData("1.1.1.1")),
      defaultTtl
    )

    val addAAAA = AddChangeForValidation(
      okZone,
      "dotted.aaaa",
      AddChangeInput("dotted.aaaa.ok.", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8")),
      defaultTtl
    )

    val addCNAME = AddChangeForValidation(
      okZone,
      "dotted.cname",
      AddChangeInput("dotted.cname.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("foo.com"))),
      defaultTtl
    )

    val addMX = AddChangeForValidation(
      okZone,
      "dotted.mx",
      AddChangeInput("dotted.mx.ok.", RecordType.MX, ttl, MXData(1, Fqdn("foo.bar."))),
      defaultTtl
    )

    val addTXT = AddChangeForValidation(
      okZone,
      "dotted.txt",
      AddChangeInput("dotted.txt.ok.", RecordType.TXT, ttl, TXTData("test")),
      defaultTtl
    )

    val result =
      validateChangesWithContext(
        ChangeForValidationMap(
          List(addA.validNel, addAAAA.validNel, addCNAME.validNel, addMX.validNel, addTXT.validNel),
          ExistingRecordSets(List())
        ),
        okAuth,
        false,
        None
      )

    result(0) should haveInvalid[DomainValidationError](ZoneDiscoveryError("dotted.a.ok."))
    result(1) should haveInvalid[DomainValidationError](ZoneDiscoveryError("dotted.aaaa.ok."))
    result(2) should haveInvalid[DomainValidationError](ZoneDiscoveryError("dotted.cname.ok."))
    result(3) should haveInvalid[DomainValidationError](ZoneDiscoveryError("dotted.mx.ok."))
    result(4) shouldBe valid
  }

  property("validateChangesWithContext: should succeed deleting existing dotted host records") {
    val existingA = rsOk.copy(name = "existing.dotted.a")
    val existingAAAA = aaaa.copy(name = "existing.dotted.aaaa")
    val existingCname = cname.copy(name = "existing.dotted.cname")
    val existingMX = mx.copy(name = "existing.dotted.mx")
    val existingTXT = txt.copy(name = "existing.dotted.txt")
    val deleteA = DeleteRRSetChangeForValidation(
      okZone,
      "existing.dotted.a",
      DeleteRRSetChangeInput("existing.dotted.a.ok.", RecordType.A)
    )
    val deleteAAAA = DeleteRRSetChangeForValidation(
      okZone,
      "existing.dotted.aaaa",
      DeleteRRSetChangeInput("existing.dotted.aaaa.ok.", RecordType.AAAA)
    )
    val deleteCname = DeleteRRSetChangeForValidation(
      okZone,
      "existing.dotted.cname",
      DeleteRRSetChangeInput("existing.dotted.cname.ok.", RecordType.CNAME)
    )
    val deleteMX = DeleteRRSetChangeForValidation(
      okZone,
      "existing.dotted.mx",
      DeleteRRSetChangeInput("existing.dotted.mx.ok.", RecordType.MX)
    )
    val deleteTXT = DeleteRRSetChangeForValidation(
      okZone,
      "existing.dotted.txt",
      DeleteRRSetChangeInput("existing.dotted.txt.ok.", RecordType.TXT)
    )
    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(
          deleteA.validNel,
          deleteAAAA.validNel,
          deleteCname.validNel,
          deleteMX.validNel,
          deleteTXT.validNel
        ),
        ExistingRecordSets(List(existingA, existingAAAA, existingCname, existingMX, existingTXT))
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) shouldBe valid
    result(2) shouldBe valid
    result(3) shouldBe valid
    result(4) shouldBe valid
  }

  property("validateChangesWithContext: should succeed updating existing dotted host records") {
    val existingA = rsOk.copy(name = "existing.dotted.a")
    val existingAAAA = aaaa.copy(name = "existing.dotted.aaaa")
    val existingCname = cname.copy(name = "existing.dotted.cname")
    val existingMX = mx.copy(name = "existing.dotted.mx")
    val existingTXT = txt.copy(name = "existing.dotted.txt")

    val deleteA = DeleteRRSetChangeForValidation(
      okZone,
      "existing.dotted.a",
      DeleteRRSetChangeInput("existing.dotted.a.ok.", RecordType.A)
    )
    val deleteAAAA = DeleteRRSetChangeForValidation(
      okZone,
      "existing.dotted.aaaa",
      DeleteRRSetChangeInput("existing.dotted.aaaa.ok.", RecordType.AAAA)
    )
    val deleteCname = DeleteRRSetChangeForValidation(
      okZone,
      "existing.dotted.cname",
      DeleteRRSetChangeInput("existing.dotted.cname.ok.", RecordType.CNAME)
    )
    val deleteMX = DeleteRRSetChangeForValidation(
      okZone,
      "existing.dotted.mx",
      DeleteRRSetChangeInput("existing.dotted.mx.ok.", RecordType.MX)
    )
    val deleteTXT = DeleteRRSetChangeForValidation(
      okZone,
      "existing.dotted.txt",
      DeleteRRSetChangeInput("existing.dotted.txt.ok.", RecordType.TXT)
    )

    val addUpdateA = AddChangeForValidation(
      okZone,
      "existing.dotted.a",
      AddChangeInput("existing.dotted.a.ok.", RecordType.A, ttl, AData("1.2.3.4")),
      defaultTtl
    )
    val addUpdateAAAA = AddChangeForValidation(
      okZone,
      "existing.dotted.aaaa",
      AddChangeInput(
        "existing.dotted.aaaa.ok.",
        RecordType.AAAA,
        Some(700),
        AAAAData("1:2:3:4:5:6:7:8")
      ),
      defaultTtl
    )
    val addUpdateCNAME = AddChangeForValidation(
      okZone,
      "existing.dotted.cname",
      AddChangeInput(
        "existing.dotted.cname.ok.",
        RecordType.CNAME,
        Some(700),
        CNAMEData(Fqdn("test"))
      ),
      defaultTtl
    )
    val addUpdateMX = AddChangeForValidation(
      okZone,
      "existing.dotted.mx",
      AddChangeInput("existing.dotted.mx.ok.", RecordType.MX, Some(700), MXData(3, Fqdn("mx"))),
      defaultTtl
    )
    val addUpdateTXT = AddChangeForValidation(
      okZone,
      "existing.dotted.txt",
      AddChangeInput("existing.dotted.txt.ok.", RecordType.TXT, Some(700), TXTData("testing")),
      defaultTtl
    )

    val result = validateChangesWithContext(
      ChangeForValidationMap(
        List(
          addUpdateA.validNel,
          addUpdateAAAA.validNel,
          addUpdateCNAME.validNel,
          addUpdateMX.validNel,
          addUpdateTXT.validNel,
          deleteA.validNel,
          deleteAAAA.validNel,
          deleteCname.validNel,
          deleteMX.validNel,
          deleteTXT.validNel
        ),
        ExistingRecordSets(List(existingA, existingAAAA, existingCname, existingMX, existingTXT))
      ),
      okAuth,
      false,
      None
    )

    result(0) shouldBe valid
    result(1) shouldBe valid
    result(2) shouldBe valid
    result(3) shouldBe valid
    result(4) shouldBe valid
  }

  property("validateAddChangeInput:  should fail for a CNAME addChangeInput with forward slash for forward zone") {
    val cnameWithForwardSlash = AddChangeInput("cname.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("cname/")))
    val result = validateAddChangeInput(cnameWithForwardSlash, false)
    result should haveInvalid[DomainValidationError](InvalidCname("cname/.",false))
  }
  property("validateAddChangeInput: should succeed for a valid CNAME addChangeInput without forward slash for forward zone") {
    val cname = AddChangeInput("cname.ok.", RecordType.CNAME, ttl, CNAMEData(Fqdn("cname")))
    val result = validateAddChangeInput(cname, false)
    result shouldBe valid
  }
  property("validateAddChangeInput: should succeed for a valid CNAME addChangeInput with forward slash for reverse zone") {
    val cnameWithForwardSlash = AddChangeInput("2.0.192.in-addr.arpa.", RecordType.CNAME, ttl, CNAMEData(Fqdn("cname/")))
    val result = validateAddChangeInput(cnameWithForwardSlash, true)
    result shouldBe valid
  }

}
