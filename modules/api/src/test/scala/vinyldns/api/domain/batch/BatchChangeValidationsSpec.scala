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
import org.joda.time.DateTime
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{EitherValues, Matchers, PropSpec}
import vinyldns.api.domain.batch.BatchTransformations._
import vinyldns.api.domain.{AccessValidations, batch, _}
import vinyldns.core.TestZoneData._
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.batch.{BatchChange, BatchChangeApprovalStatus}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{ACLRule, AccessLevel, Zone, ZoneStatus}

import scala.util.Random

class BatchChangeValidationsSpec
    extends PropSpec
    with Matchers
    with GeneratorDrivenPropertyChecks
    with EitherMatchers
    with EitherValues
    with ValidatedMatchers {

  import Gen._
  import vinyldns.api.DomainGenerator._
  import vinyldns.api.IpAddressGenerator._

  private val maxChanges = 10
  private val underTest =
    new BatchChangeValidations(maxChanges, AccessValidations, multiRecordEnabled = true)
  private val underTestMultiDisabled =
    new BatchChangeValidations(maxChanges, AccessValidations, multiRecordEnabled = false)

  import underTest._

  private val validZone = Zone(
    "ok.zone.recordsets.",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = okGroup.id)

  private val validIp4ReverseZone = Zone(
    "2.0.192.in-addr.arpa",
    "test@test.com",
    status = ZoneStatus.Active,
    connection = testConnection,
    adminGroupId = okGroup.id)

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
  } yield AddChangeForValidation(validZone, recordName, changeInput)

  private def generateValidAddChangeForValidation(rs: RecordSet): Gen[AddChangeForValidation] =
    for {
      recordName <- domainComponentGenerator
      addChangeInput <- AddChangeInput(recordName, rs.typ, Some(rs.ttl), rs.records.head)
    } yield AddChangeForValidation(validZone, recordName, addChangeInput)

  private val recordSetList = List(rsOk, aaaa, aaaaOrigin, abcRecord)

  private def validBatchChangeInput(min: Int, max: Int): Gen[BatchChangeInput] =
    for {
      numChanges <- choose(min, max)
      changes <- listOfN(numChanges, validAChangeGen)
    } yield batch.BatchChangeInput(None, changes)

  private val createPrivateAddChange = AddChangeForValidation(
    okZone,
    "private-create",
    AddChangeInput("private-create", RecordType.A, ttl, AData("1.1.1.1")))

  private val createSharedAddChange = AddChangeForValidation(
    sharedZone,
    "shared-create",
    AddChangeInput("shared-create", RecordType.A, ttl, AData("1.1.1.1")))

  private val updatePrivateAddChange = AddChangeForValidation(
    okZone,
    "private-update",
    AddChangeInput("private-update", RecordType.A, ttl, AAAAData("1.2.3.4")))

  private val updatePrivateDeleteChange = DeleteChangeForValidation(
    okZone,
    "private-update",
    DeleteChangeInput("private-update", RecordType.A))

  private val updateSharedAddChange = AddChangeForValidation(
    sharedZone,
    "shared-update",
    AddChangeInput("shared-update", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8")))

  private val updateSharedDeleteChange = DeleteChangeForValidation(
    sharedZone,
    "shared-update",
    DeleteChangeInput("shared-update", RecordType.AAAA))

  private val deletePrivateChange = DeleteChangeForValidation(
    okZone,
    "private-delete",
    DeleteChangeInput("private-delete", RecordType.A)
  )

  private val deleteSharedChange = DeleteChangeForValidation(
    sharedZone,
    "shared-delete",
    DeleteChangeInput("shared-delete", RecordType.AAAA)
  )

  private val validPendingBatchChange = BatchChange(
    okUser.id,
    okUser.userName,
    None,
    DateTime.now,
    List(),
    approvalStatus = BatchChangeApprovalStatus.PendingApproval)

  private val invalidPendingBatchChange = BatchChange(
    okUser.id,
    okUser.userName,
    None,
    DateTime.now,
    List(),
    approvalStatus = BatchChangeApprovalStatus.AutoApproved)

  property("validateBatchChangeInputSize: should fail if batch has no changes") {
    validateBatchChangeInputSize(BatchChangeInput(None, List())) should
      haveInvalid[DomainValidationError](BatchChangeIsEmpty(maxChanges))
  }

  property(
    "validateBatchChangeInputSize: should succeed with at least one but fewer than max inputs") {
    forAll(validBatchChangeInput(1, maxChanges)) { input: BatchChangeInput =>
      validateBatchChangeInputSize(input).isValid shouldBe true
    }

    forAll(validBatchChangeInput(maxChanges + 1, 100)) { input: BatchChangeInput =>
      validateBatchChangeInputSize(input) should haveInvalid[DomainValidationError](
        ChangeLimitExceeded(maxChanges))
    }
  }

  property("validateInputChanges: should succeed if all inputs are good") {
    forAll(listOfN(3, validAChangeGen)) { input: List[ChangeInput] =>
      val result = validateInputChanges(input)
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
    "validateOwnerGroupId: should fail if user is not an admin and does not belong to owner group") {
    validateOwnerGroupId(Some(okGroup.id), Some(okGroup), dummyAuth) should
      haveInvalid[DomainValidationError](
        NotAMemberOfOwnerGroup(okGroup.id, dummyAuth.signedInUser.userName))
  }

  property(
    "validateBatchChangeInput: should succeed if input size and owner group ID are both valid") {
    forAll(validBatchChangeInput(1, 10)) { batchChangeInput =>
      validateBatchChangeInput(batchChangeInput, None, okAuth).value.unsafeRunSync() should be(
        right)
    }
  }

  property(
    "validateBatchChangeInput: should fail if input size is invalid and owner group ID is valid") {
    forAll(validBatchChangeInput(11, 20)) { batchChangeInput =>
      validateBatchChangeInput(batchChangeInput, None, okAuth).value
        .unsafeRunSync() shouldBe
        Left(InvalidBatchChangeInput(List(ChangeLimitExceeded(maxChanges))))
    }
  }

  property(
    "validateBatchChangeInput: should fail if input size is valid and owner group ID is invalid") {
    forAll(validBatchChangeInput(1, 10)) { batchChangeInput =>
      validateBatchChangeInput(
        batchChangeInput.copy(ownerGroupId = Some(okGroup.id)),
        Some(okGroup),
        dummyAuth).value.unsafeRunSync() shouldBe
        Left(
          InvalidBatchChangeInput(
            List(NotAMemberOfOwnerGroup(okGroup.id, dummyAuth.signedInUser.userName))))
    }
  }

  property(
    "validateBatchChangeInput: should fail if both input size is valid and owner group ID aew invalid") {
    forAll(validBatchChangeInput(0, 0)) { batchChangeInput =>
      val result = validateBatchChangeInput(
        batchChangeInput.copy(ownerGroupId = Some(dummyGroup.id)),
        None,
        okAuth).value.unsafeRunSync()
      result shouldBe
        Left(
          InvalidBatchChangeInput(
            List(BatchChangeIsEmpty(maxChanges), GroupDoesNotExist(dummyGroup.id))))
    }
  }

  property("validateBatchChangePendingApproval: should succeed if batch change is PendingApproval") {
    validateBatchChangePendingApproval(validPendingBatchChange) should beValid(())
  }

  property("validateBatchChangePendingApproval: should fail if batch change is not PendingApproval") {
    validateBatchChangePendingApproval(invalidPendingBatchChange) should
      haveInvalid[BatchChangeErrorResponse](
        BatchChangeNotPendingApproval(invalidPendingBatchChange.id))
  }

  property("validateAuthorizedReviewer: should succeed if the reviewer is a super user") {
    validateAuthorizedReviewer(superUserAuth, validPendingBatchChange) should beValid(())
  }

  property("validateAuthorizedReviewer: should succeed if the reviewer is a support user") {
    validateAuthorizedReviewer(supportUserAuth, validPendingBatchChange) should beValid(())
  }

  property("validateAuthorizedReviewer: should fail if the reviewer is not a super or support user") {
    validateAuthorizedReviewer(okAuth, validPendingBatchChange) should
      haveInvalid[BatchChangeErrorResponse](UserNotAuthorizedError(validPendingBatchChange.id))
  }

  property(
    "validateRejectedBatchChange: should succeed if batch change is pending approval and reviewer" +
      "is authorized") {
    validateRejectedBatchChange(validPendingBatchChange, supportUserAuth).value
      .unsafeRunSync() should be(right)
  }

  property("validateRejectedBatchChange: should fail if batch change is not pending approval") {
    validateRejectedBatchChange(invalidPendingBatchChange, supportUserAuth).value
      .unsafeRunSync() shouldBe
      Left(
        InvalidBatchChangeReview(List(BatchChangeNotPendingApproval(invalidPendingBatchChange.id))))
  }

  property("validateRejectedBatchChange: should fail if reviewer is not authorized") {
    validateRejectedBatchChange(validPendingBatchChange, okAuth).value.unsafeRunSync() shouldBe
      Left(InvalidBatchChangeReview(List(UserNotAuthorizedError(validPendingBatchChange.id))))
  }

  property(
    "validateRejectedBatchChange: should fail if batch change is not pending approval and reviewer is not" +
      "authorized") {
    validateRejectedBatchChange(invalidPendingBatchChange, okAuth).value.unsafeRunSync() shouldBe
      Left(
        InvalidBatchChangeReview(
          List(
            BatchChangeNotPendingApproval(invalidPendingBatchChange.id),
            UserNotAuthorizedError(invalidPendingBatchChange.id))))
  }

  property("validateInputChanges: should fail with mix of success and failure inputs") {
    val goodInput = AddChangeInput("test.example.com.", RecordType.A, ttl, AData("1.1.1.1"))
    val goodAAAAInput =
      AddChangeInput("testAAAA.example.com.", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8"))
    val invalidDomainNameInput =
      AddChangeInput("invalidDomainName$", RecordType.A, ttl, AAAAData("1:2:3:4:5:6:7:8"))
    val invalidIpv6Input =
      AddChangeInput("testbad.example.com.", RecordType.AAAA, ttl, AAAAData("invalidIpv6:123"))
    val result =
      validateInputChanges(List(goodInput, goodAAAAInput, invalidDomainNameInput, invalidIpv6Input))
    result(0) shouldBe valid
    result(1) shouldBe valid
    result(2) should haveInvalid[DomainValidationError](InvalidDomainName("invalidDomainName$."))
    result(3) should haveInvalid[DomainValidationError](InvalidIpv6Address("invalidIpv6:123"))
  }

  property("""validateInputName: should fail with a HighValueDomainError
      |if inputName is a High Value Domain""".stripMargin) {
    val changeA = AddChangeInput("high-value-domain.foo.", RecordType.A, ttl, AData("1.1.1.1"))
    val changeIpV4 = AddChangeInput("192.0.2.252", RecordType.PTR, ttl, PTRData("test."))
    val changeIpV6 =
      AddChangeInput("fd69:27cc:fe91:0:0:0:0:ffff", RecordType.PTR, ttl, PTRData("test."))

    val resultA = validateInputName(changeA)
    val resultIpV4 = validateInputName(changeIpV4)
    val resultIpV6 = validateInputName(changeIpV6)

    resultA should haveInvalid[DomainValidationError](
      HighValueDomainError("high-value-domain.foo."))
    resultIpV4 should haveInvalid[DomainValidationError](HighValueDomainError("192.0.2.252"))
    resultIpV6 should haveInvalid[DomainValidationError](
      HighValueDomainError("fd69:27cc:fe91:0:0:0:0:ffff"))
  }

  property("""validateInputName: should fail with a DomainValidationError for deletes
      |if validateHostName fails for an invalid domain name""".stripMargin) {
    val change = DeleteChangeInput("invalidDomainName$", RecordType.A)
    val result = validateInputName(change)
    result should haveInvalid[DomainValidationError](InvalidDomainName("invalidDomainName$."))
  }

  property("""validateInputName: should fail with a DomainValidationError for deletes
      |if validateHostName fails for an invalid domain name length""".stripMargin) {
    val invalidDomainName = Random.alphanumeric.take(256).mkString
    val change = DeleteChangeInput(invalidDomainName, RecordType.AAAA)
    val result = validateInputName(change)
    result should (haveInvalid[DomainValidationError](InvalidDomainName(s"$invalidDomainName."))
      .and(haveInvalid[DomainValidationError](InvalidLength(s"$invalidDomainName.", 2, 255))))
  }

  property("""validateInputName: PTR should fail with InvalidIPAddress for deletes
      |if inputName is not a valid ipv4 or ipv6 address""".stripMargin) {
    val invalidIp = "invalidIp.111"
    val change = DeleteChangeInput(invalidIp, RecordType.PTR)
    val result = validateInputName(change)
    result should haveInvalid[DomainValidationError](InvalidIPAddress(invalidIp))
  }

  property("validateAddChangeInput: should succeed if single addChangeInput is good for A Record") {
    forAll(validAChangeGen) { input: AddChangeInput =>
      val result = validateAddChangeInput(input)
      result shouldBe valid
    }
  }

  property(
    "validateAddChangeInput: should succeed if single addChangeInput is good for AAAA Record") {
    forAll(validAAAAChangeGen) { input: AddChangeInput =>
      val result = validateAddChangeInput(input)
      result shouldBe valid
    }
  }

  property("""validateAddChangeInput: should fail with a DomainValidationError
      |if validateHostName fails for an invalid domain name""".stripMargin) {
    val change = AddChangeInput("invalidDomainName$", RecordType.A, ttl, AData("1.1.1.1"))
    val result = validateAddChangeInput(change)
    result should haveInvalid[DomainValidationError](InvalidDomainName("invalidDomainName$."))
  }

  property("""validateAddChangeInput: should fail with a DomainValidationError
      |if validateHostName fails for an invalid domain name length""".stripMargin) {
    val invalidDomainName = Random.alphanumeric.take(256).mkString
    val change = AddChangeInput(invalidDomainName, RecordType.A, ttl, AData("1.1.1.1"))
    val result = validateAddChangeInput(change)
    result should haveInvalid[DomainValidationError](InvalidDomainName(s"$invalidDomainName."))
      .and(haveInvalid[DomainValidationError](InvalidLength(s"$invalidDomainName.", 2, 255)))
  }

  property(
    "validateAddChangeInput: should fail with InvalidRange if validateRange fails for an addChangeInput") {
    forAll(choose[Long](0, 29)) { invalidTTL: Long =>
      val change =
        AddChangeInput("test.comcast.com.", RecordType.A, Some(invalidTTL), AData("1.1.1.1"))
      val result = validateAddChangeInput(change)
      result should haveInvalid[DomainValidationError](InvalidTTL(invalidTTL))
    }
  }

  property("""validateAddChangeInput: should fail with InvalidIpv4Address
      |if validateRecordData fails for an invalid ipv4 address""".stripMargin) {
    val invalidIpv4 = "invalidIpv4:123"
    val change = AddChangeInput("test.comcast.com.", RecordType.A, ttl, AData(invalidIpv4))
    val result = validateAddChangeInput(change)
    result should haveInvalid[DomainValidationError](InvalidIpv4Address(invalidIpv4))
  }

  property("""validateAddChangeInput: should fail with InvalidIpv6Address
      |if validateRecordData fails for an invalid ipv6 address""".stripMargin) {
    val invalidIpv6 = "invalidIpv6:123"
    val change = AddChangeInput("test.comcast.com.", RecordType.AAAA, ttl, AAAAData(invalidIpv6))
    val result = validateAddChangeInput(change)
    result should haveInvalid[DomainValidationError](InvalidIpv6Address(invalidIpv6))
  }

  property("validateAddChangeInput: should fail if A inputName includes a reverse zone address") {
    val invalidInputName = "test.1.2.3.in-addr.arpa."
    val badAChange = AddChangeInput(invalidInputName, RecordType.A, ttl, AData("1.1.1.1"))
    val result = validateAddChangeInput(badAChange)
    result should haveInvalid[DomainValidationError](
      RecordInReverseZoneError(invalidInputName, RecordType.A.toString))
  }

  property("validateAddChangeInput: should fail if AAAA inputName includes a reverse zone address") {
    val invalidInputName = "test.1.2.3.ip6.arpa."
    val badAAAAChange =
      AddChangeInput(invalidInputName, RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8"))
    val result = validateAddChangeInput(badAAAAChange)
    result should haveInvalid[DomainValidationError](
      RecordInReverseZoneError(invalidInputName, RecordType.AAAA.toString))
  }

  property("""validateAddChangeInput: should fail with InvalidDomainName
      |if validateRecordData fails for invalid CNAME record data""".stripMargin) {
    val invalidCNAMERecordData = "$$$"
    val change =
      AddChangeInput("test.comcast.com.", RecordType.CNAME, ttl, CNAMEData(invalidCNAMERecordData))
    val result = validateAddChangeInput(change)

    result should haveInvalid[DomainValidationError](InvalidDomainName(s"$invalidCNAMERecordData."))
  }

  property("""validateAddChangeInput: should fail with InvalidLength
      |if validateRecordData fails for invalid CNAME record data""".stripMargin) {
    val invalidCNAMERecordData = "s" * 256
    val change =
      AddChangeInput("test.comcast.com.", RecordType.CNAME, ttl, CNAMEData(invalidCNAMERecordData))
    val result = validateAddChangeInput(change)

    result should haveInvalid[DomainValidationError](
      InvalidLength(s"$invalidCNAMERecordData.", 2, 255))
  }

  property("""validateAddChangeInput: PTR should fail with InvalidIPAddress
      |if inputName is not a valid ipv4 or ipv6 address""".stripMargin) {
    val invalidIp = "invalidip.111."
    val change = AddChangeInput(invalidIp, RecordType.PTR, ttl, PTRData("test.comcast.com"))
    val result = validateAddChangeInput(change)

    result should haveInvalid[DomainValidationError](InvalidIPAddress(invalidIp))
  }

  property("validateAddChangeInput: should fail with InvalidDomainName for invalid PTR record data") {
    val invalidPTRDname = "*invalidptrdname"
    val change = AddChangeInput("4.5.6.7", RecordType.PTR, ttl, PTRData(invalidPTRDname))
    val result = validateAddChangeInput(change)

    result should haveInvalid[DomainValidationError](InvalidDomainName(s"$invalidPTRDname."))
  }

  property(
    "validateChangesWithContext: should properly validate with mix of success and failure inputs") {
    val authZone = okZone
    val reverseZone = okZone.copy(name = "2.0.192.in-addr.arpa.")
    val addA1 = AddChangeForValidation(
      authZone,
      "valid",
      AddChangeInput("valid.ok.", RecordType.A, ttl, AData("1.1.1.1")))
    val existingA = AddChangeForValidation(
      authZone,
      "existingA",
      AddChangeInput("existingA.ok.", RecordType.A, ttl, AData("1.1.1.1")))
    val existingCname = AddChangeForValidation(
      authZone,
      "existingCname",
      AddChangeInput("existingCname.ok.", RecordType.CNAME, ttl, CNAMEData("cname")))
    val addA2 = AddChangeForValidation(
      okZone,
      "valid2",
      AddChangeInput("valid2.ok.", RecordType.A, ttl, AData("1.1.1.1")))
    val duplicateNameCname = AddChangeForValidation(
      reverseZone,
      "199",
      AddChangeInput(
        "199.2.0.192.in-addr.arpa.",
        RecordType.CNAME,
        ttl,
        CNAMEData("199.192/30.2.0.192.in-addr.arpa")))
    val duplicateNamePTR = AddChangeForValidation(
      reverseZone,
      "199",
      AddChangeInput("192.0.2.199", RecordType.PTR, ttl, PTRData("ptr.ok.")))

    val existingRsList: List[RecordSet] = List(
      rsOk.copy(zoneId = existingA.zone.id, name = existingA.recordName),
      rsOk.copy(
        zoneId = existingCname.zone.id,
        name = existingCname.recordName,
        typ = RecordType.CNAME)
    )

    val result = validateChangesWithContext(
      List(
        addA1.validNel,
        existingA.validNel,
        existingCname.validNel,
        addA2.validNel,
        duplicateNameCname.validNel,
        duplicateNamePTR.validNel),
      ExistingRecordSets(existingRsList),
      okAuth,
      None
    )

    result(0) shouldBe valid
    result(1) should haveInvalid[DomainValidationError](
      RecordAlreadyExists(existingA.inputChange.inputName))
    result(2) should haveInvalid[DomainValidationError](
      RecordAlreadyExists(existingCname.inputChange.inputName)).and(
      haveInvalid[DomainValidationError](
        CnameIsNotUniqueError(existingCname.inputChange.inputName, existingCname.inputChange.typ)))
    result(3) shouldBe valid
    result(4) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch("199.2.0.192.in-addr.arpa.", RecordType.CNAME))
    result(5) shouldBe valid
  }

  property("validateChangesWithContext: should succeed for valid update inputs") {
    val existingRecord = rsOk.copy(zoneId = okZone.id, name = "update", ttl = 300)
    val addUpdateA = AddChangeForValidation(
      okZone,
      "update",
      AddChangeInput("update.ok.", RecordType.A, ttl, AData("1.2.3.4")))
    val deleteUpdateA =
      DeleteChangeForValidation(okZone, "Update", DeleteChangeInput("update.ok.", RecordType.A))
    val result = validateChangesWithContext(
      List(addUpdateA.validNel, deleteUpdateA.validNel),
      ExistingRecordSets(List(existingRecord)),
      okAuth,
      None)

    result(0) shouldBe valid
    result(1) shouldBe valid
  }

  property("validateChangesWithContext: should succeed for update for user with only write access") {
    val writeAcl = ACLRule(accessLevel = AccessLevel.Write, userId = Some(notAuth.userId))
    val existingRecord = rsOk.copy(zoneId = okZone.id, name = "update", ttl = 300)
    val addUpdateA = AddChangeForValidation(
      okZone.addACLRule(writeAcl),
      "update",
      AddChangeInput("update.ok.", RecordType.A, ttl, AData("1.2.3.4")))
    val deleteUpdateA = DeleteChangeForValidation(
      okZone.addACLRule(writeAcl),
      "update",
      DeleteChangeInput("update.ok.", RecordType.A))
    val result = validateChangesWithContext(
      List(addUpdateA.validNel, deleteUpdateA.validNel),
      ExistingRecordSets(List(existingRecord)),
      notAuth,
      None)

    result(0) shouldBe valid
    result(1) shouldBe valid
  }

  property(
    "validateChangesWithContext: should fail for update if user does not have sufficient access") {
    val readAcl =
      ACLRule(accessLevel = AccessLevel.Read, userId = Some(notAuth.signedInUser.userName))
    val existingRecord = rsOk.copy(zoneId = okZone.id, name = "update", ttl = 300)
    val addUpdateA = AddChangeForValidation(
      okZone.addACLRule(readAcl),
      "update",
      AddChangeInput("update.ok.", RecordType.A, ttl, AData("1.2.3.4")))
    val deleteUpdateA = DeleteChangeForValidation(
      okZone.addACLRule(readAcl),
      "update",
      DeleteChangeInput("update.ok.", RecordType.A))
    val result = validateChangesWithContext(
      List(addUpdateA.validNel, deleteUpdateA.validNel),
      ExistingRecordSets(List(existingRecord)),
      notAuth,
      None)

    result(0) should haveInvalid[DomainValidationError](
      UserIsNotAuthorized(notAuth.signedInUser.userName))
    result(1) should haveInvalid[DomainValidationError](
      UserIsNotAuthorized(notAuth.signedInUser.userName))
  }

  property("validateChangesWithContext: should fail for update if record does not exist") {
    val addUpdateA = AddChangeForValidation(
      okZone,
      "does-not-exist",
      AddChangeInput("does-not-exist.ok.", RecordType.A, ttl, AData("1.2.3.4")))
    val deleteUpdateA = DeleteChangeForValidation(
      okZone,
      "does-not-exist",
      DeleteChangeInput("does-not-exist.ok.", RecordType.A))
    val result = validateChangesWithContext(
      List(addUpdateA.validNel, deleteUpdateA.validNel),
      ExistingRecordSets(List()),
      okAuth,
      None)

    result(0) should haveInvalid[DomainValidationError](
      RecordDoesNotExist(deleteUpdateA.inputChange.inputName))
    result(1) should haveInvalid[DomainValidationError](
      RecordDoesNotExist(deleteUpdateA.inputChange.inputName))
  }

  property(
    """validateChangesWithContext: should succeed for update in shared zone if user belongs to record
             | owner group""".stripMargin) {
    val existingRecord =
      sharedZoneRecord.copy(name = "mx", typ = RecordType.MX, records = List(MXData(200, "mx")))
    val addUpdateA = AddChangeForValidation(
      sharedZone,
      "mx",
      AddChangeInput("mx.shared.", RecordType.MX, ttl, MXData(200, "mx")))
    val deleteUpdateA =
      DeleteChangeForValidation(sharedZone, "mx", DeleteChangeInput("mx.shared.", RecordType.MX))
    val result = validateChangesWithContext(
      List(addUpdateA.validNel, deleteUpdateA.validNel),
      ExistingRecordSets(List(existingRecord)),
      okAuth,
      None)

    result(0) shouldBe valid
    result(1) shouldBe valid
  }

  property("""validateChangesWithContext: should succeed adding a record
      |if an existing CNAME with the same name exists but is being deleted""".stripMargin) {
    val existingCname = rsOk.copy(zoneId = okZone.id, name = "existing", typ = RecordType.CNAME)
    val addA = AddChangeForValidation(
      okZone,
      "existing",
      AddChangeInput("existing.ok.", RecordType.A, ttl, AData("1.2.3.4")))
    val deleteCname = DeleteChangeForValidation(
      okZone,
      "existing",
      DeleteChangeInput("existing.ok.", RecordType.CNAME))
    val result = validateChangesWithContext(
      List(addA.validNel, deleteCname.validNel),
      ExistingRecordSets(List(existingCname)),
      okAuth,
      None)

    result(0) shouldBe valid
    result(1) shouldBe valid
  }

  property("""validateChangesWithContext: should fail AddChangeForValidation with
      |CnameWithRecordNameAlreadyExists if record already exists as CNAME record type""".stripMargin) {
    List(rsOk, aaaa, ptrIp4, ptrIp6).foreach { recordSet =>
      forAll(generateValidAddChangeForValidation(recordSet)) { input: AddChangeForValidation =>
        val existingCNAMERecord = recordSet.copy(
          zoneId = input.zone.id,
          name = input.recordName,
          typ = RecordType.CNAME,
          records = List(CNAMEData("cname")))
        val newRecordSetList = existingCNAMERecord :: recordSetList
        val result = validateChangesWithContext(
          List(input.validNel),
          ExistingRecordSets(newRecordSetList),
          okAuth,
          None)

        result(0) should haveInvalid[DomainValidationError](
          CnameIsNotUniqueError(input.inputChange.inputName, RecordType.CNAME))
      }
    }
  }

  property("validateChangesWithContext: should succeed if all inputs are good") {
    forAll(validAddChangeForValidationGen) { input: AddChangeForValidation =>
      val result =
        validateChangesWithContext(
          List(input.validNel),
          ExistingRecordSets(recordSetList),
          okAuth,
          None)

      result(0) shouldBe valid
    }
  }

  property(
    "validateChangesWithContext: should succeed if all inputs of different record types are good") {
    List(rsOk, aaaa, ptrIp4, ptrIp6).foreach { recordSet =>
      forAll(generateValidAddChangeForValidation(recordSet)) { input: AddChangeForValidation =>
        val result = validateChangesWithContext(
          List(input.validNel),
          ExistingRecordSets(recordSetList),
          okAuth,
          None)
        result(0) shouldBe valid
      }
    }
  }

  property(
    "validateChangesWithContext: should fail with RecordAlreadyExists if record already exists") {
    forAll(validAddChangeForValidationGen) { input: AddChangeForValidation =>
      val existingRecordSetList = rsOk.copy(
        zoneId = input.zone.id,
        name = input.recordName.toUpperCase) :: recordSetList
      val result = validateChangesWithContext(
        List(input.validNel),
        ExistingRecordSets(existingRecordSetList),
        okAuth,
        None)

      result(0) should haveInvalid[DomainValidationError](
        RecordAlreadyExists(input.inputChange.inputName))
    }
  }

  property(
    "validateChangesWithContext: should succeed if CNAME record name already exists but is being deleted") {
    val addCname = AddChangeForValidation(
      validZone,
      "existingCname",
      AddChangeInput("existingCname.ok.", RecordType.CNAME, ttl, CNAMEData("cname")))
    val deleteA = DeleteChangeForValidation(
      validZone,
      "existingCname",
      DeleteChangeInput("existingCname.ok.", RecordType.A))
    val existingA = rsOk.copy(zoneId = addCname.zone.id, name = addCname.recordName)
    val newRecordSetList = existingA :: recordSetList
    val result = validateChangesWithContext(
      List(addCname.validNel, deleteA.validNel),
      ExistingRecordSets(newRecordSetList),
      okAuth,
      None)

    result(0) shouldBe valid
    result(1) shouldBe valid
  }

  property("""validateChangesWithContext: should fail with CnameIsNotUniqueError
      |if CNAME record name already exists""".stripMargin) {
    val addCname = AddChangeForValidation(
      validZone,
      "existingCname",
      AddChangeInput("existingCname.ok.", RecordType.CNAME, ttl, CNAMEData("cname")))
    val existingA = rsOk.copy(zoneId = addCname.zone.id, name = addCname.recordName)
    val newRecordSetList = existingA :: recordSetList
    val result = validateChangesWithContext(
      List(addCname.validNel),
      ExistingRecordSets(newRecordSetList),
      okAuth,
      None)

    result(0) should haveInvalid[DomainValidationError](
      CnameIsNotUniqueError(addCname.inputChange.inputName, existingA.typ))
  }

  property("""validateChangesWithContext: should succeed for CNAME record
      |if there's a duplicate PTR ipv6 record that is being deleted""".stripMargin) {
    val addCname = AddChangeForValidation(
      validZone,
      "0.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0",
      AddChangeInput(
        "0.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.1.9.e.f.c.c.7.2.9.6.d.f.ip6.arpa.",
        RecordType.CNAME,
        ttl,
        CNAMEData("cname"))
    )
    val deletePtr = DeleteChangeForValidation(
      validZone,
      "0.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0",
      DeleteChangeInput("0.6.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0", RecordType.PTR))
    val existingRecordPTR = ptrIp6.copy(zoneId = addCname.zone.id, name = addCname.recordName)
    val result = validateChangesWithContext(
      List(addCname.validNel, deletePtr.validNel),
      ExistingRecordSets(List(existingRecordPTR)),
      okAuth,
      None)

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
        CNAMEData("cname"))
    )
    val existingRecordPTR = ptrIp6.copy(zoneId = addCname.zone.id, name = addCname.recordName)
    val result = validateChangesWithContext(
      List(addCname.validNel),
      ExistingRecordSets(List(existingRecordPTR)),
      okAuth,
      None)

    result(0) should haveInvalid[DomainValidationError](
      CnameIsNotUniqueError(addCname.inputChange.inputName, existingRecordPTR.typ))
  }

  property("""validateChangesWithContext: CNAME record should pass
      |if no other changes in batch change have same record name""".stripMargin) {
    val addA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.ok.", RecordType.A, ttl, AData("1.1.1.1")))
    val addAAAA = AddChangeForValidation(
      okZone,
      "testAAAA",
      AddChangeInput("testAAAA.ok.", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8")))
    val addCname = AddChangeForValidation(
      okZone,
      "new",
      AddChangeInput("new.ok.", RecordType.CNAME, ttl, CNAMEData("hey.ok.com.")))
    val result = validateChangesWithContext(
      List(addA.validNel, addAAAA.validNel, addCname.validNel),
      ExistingRecordSets(List()),
      okAuth,
      None)

    result(0) shouldBe valid
    result(1) shouldBe valid
    result(2) shouldBe valid
  }

  property("""validateChangesWithContext: CNAME record should fail
      |if another add change in batch change has the same record name""".stripMargin) {
    val addA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.ok.", RecordType.A, ttl, AData("1.1.1.1")))
    val addDuplicateCname = AddChangeForValidation(
      okZone,
      "testAAAA",
      AddChangeInput("testAAAA.ok.", RecordType.CNAME, ttl, CNAMEData("hey.ok.com.")))
    val addAAAA = AddChangeForValidation(
      okZone,
      "testAAAA",
      AddChangeInput("testAAAA.ok.", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8")))
    val result = validateChangesWithContext(
      List(addA.validNel, addAAAA.validNel, addDuplicateCname.validNel),
      ExistingRecordSets(List()),
      okAuth,
      None)

    result(0) shouldBe valid
    result(1) shouldBe valid
    result(2) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch(
        addDuplicateCname.inputChange.inputName,
        addDuplicateCname.inputChange.typ))
  }

  property("""validateChangesWithContext: both CNAME records should fail
      |if there are duplicate CNAME add change inputs""".stripMargin) {
    val addA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.ok.", RecordType.A, ttl, AData("1.1.1.1")))
    val addCname = AddChangeForValidation(
      okZone,
      "testAAAA",
      AddChangeInput("testAAAA.ok.", RecordType.CNAME, ttl, CNAMEData("hey.ok.com.")))
    val addDuplicateCname = AddChangeForValidation(
      okZone,
      "testAAAA",
      AddChangeInput("testAAAA.ok.", RecordType.CNAME, ttl, CNAMEData("hey2.ok.com.")))
    val result = validateChangesWithContext(
      List(addA.validNel, addCname.validNel, addDuplicateCname.validNel),
      ExistingRecordSets(List()),
      okAuth,
      None)

    result(0) shouldBe valid
    result(1) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch(addCname.inputChange.inputName, addCname.inputChange.typ))
    result(2) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch(
        addDuplicateCname.inputChange.inputName,
        addDuplicateCname.inputChange.typ))
  }

  property("""validateChangesWithContext: both PTR records should succeed
      |if there are duplicate PTR add change inputs""".stripMargin) {
    val addA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.ok.", RecordType.A, ttl, AData("1.1.1.1")))
    val addPtr = AddChangeForValidation(
      okZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData("test.ok.")))
    val addDuplicatePtr = AddChangeForValidation(
      okZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData("hey.ok.com.")))
    val result = validateChangesWithContext(
      List(addA.validNel, addPtr.validNel, addDuplicatePtr.validNel),
      ExistingRecordSets(List()),
      okAuth,
      None)

    result.map(_ shouldBe valid)
  }

  property("""validateChangesWithContext: should succeed for AddChangeForValidation
      |if user has group admin access""".stripMargin) {
    val addA = AddChangeForValidation(
      validZone,
      "valid",
      AddChangeInput("valid.ok.", RecordType.A, ttl, AData("1.1.1.1")))
    val result =
      validateChangesWithContext(
        List(addA.validNel),
        ExistingRecordSets(recordSetList),
        okAuth,
        None)

    result(0) shouldBe valid
  }

  property(
    "validateChangesWithContext: should fail for AddChangeForValidation if user is a superUser with no other access") {
    val addA = AddChangeForValidation(
      validZone,
      "valid",
      AddChangeInput("valid.ok.", RecordType.A, ttl, AData("1.1.1.1")))
    val result = validateChangesWithContext(
      List(addA.validNel),
      ExistingRecordSets(recordSetList),
      AuthPrincipal(superUser, Seq.empty),
      None)

    result(0) should haveInvalid[DomainValidationError](UserIsNotAuthorized(superUser.userName))
  }

  property(
    "validateChangesWithContext: should succeed for AddChangeForValidation if user has necessary ACL rule") {
    val addA = AddChangeForValidation(
      validZone.addACLRule(ACLRule(accessLevel = AccessLevel.Write, userId = Some(notAuth.userId))),
      "valid",
      AddChangeInput("valid.ok.", RecordType.A, ttl, AData("1.1.1.1"))
    )
    val result =
      validateChangesWithContext(
        List(addA.validNel),
        ExistingRecordSets(recordSetList),
        notAuth,
        None)

    result(0) shouldBe valid
  }

  property(
    """validateChangesWithContext: should fail AddChangeForValidation with UserIsNotAuthorized if user
      |is not a superuser, doesn't have group admin access, or doesn't have necessary ACL rule""".stripMargin) {
    forAll(validAddChangeForValidationGen) { input: AddChangeForValidation =>
      val result =
        validateChangesWithContext(
          List(input.validNel),
          ExistingRecordSets(recordSetList),
          notAuth,
          None)

      result(0) should haveInvalid[DomainValidationError](
        UserIsNotAuthorized(notAuth.signedInUser.userName))
    }
  }

  property("""validateChangesWithContext: should fail with RecordNameNotUniqueInBatch for PTR record
      |if valid CNAME with same name exists in batch""".stripMargin) {
    val addCname = AddChangeForValidation(
      validZone,
      "existing",
      AddChangeInput("existing.ok.", RecordType.CNAME, ttl, PTRData("orders.vinyldns.")))
    val addPtr = AddChangeForValidation(
      validZone,
      "existing",
      AddChangeInput("existing.ok.", RecordType.PTR, ttl, CNAMEData("ptrdname.")))
    val result = validateChangesWithContext(
      List(addCname.validNel, addPtr.validNel),
      ExistingRecordSets(List()),
      okAuth,
      None)

    result(0) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch("existing.ok.", RecordType.CNAME))
  }

  property(
    "validateChangesWithContext: should succeed for DeleteChangeForValidation if record exists") {
    val deleteA = DeleteChangeForValidation(
      validZone,
      "Record-exists",
      DeleteChangeInput("record-exists.ok.", RecordType.A))
    val existingDeleteRecord =
      rsOk.copy(zoneId = deleteA.zone.id, name = deleteA.recordName.toLowerCase)
    val result = validateChangesWithContext(
      List(deleteA.validNel),
      ExistingRecordSets(List(existingDeleteRecord)),
      okAuth,
      None)

    result(0) shouldBe valid
  }

  property(
    """validateChangesWithContext: should fail DeleteChangeForValidation with RecordDoesNotExist
      |if record does not exist""".stripMargin) {
    val deleteA = DeleteChangeForValidation(
      validZone,
      "record-does-not-exist",
      DeleteChangeInput("record-does-not-exist.ok.", RecordType.A))
    val result =
      validateChangesWithContext(
        List(deleteA.validNel),
        ExistingRecordSets(recordSetList),
        okAuth,
        None)

    result(0) should haveInvalid[DomainValidationError](
      RecordDoesNotExist(deleteA.inputChange.inputName))
  }

  property("""validateChangesWithContext: should succeed for DeleteChangeForValidation
      |if record set status is Active""".stripMargin) {
    val deleteA = DeleteChangeForValidation(
      validZone,
      "Active-record-status",
      DeleteChangeInput("active-record-status", RecordType.A))
    val existingDeleteRecord = rsOk.copy(
      zoneId = deleteA.zone.id,
      name = deleteA.recordName.toLowerCase,
      status = RecordSetStatus.Active)
    val result = validateChangesWithContext(
      List(deleteA.validNel),
      ExistingRecordSets(List(existingDeleteRecord)),
      okAuth,
      None)

    result(0) shouldBe valid
  }

  property("""validateChangesWithContext: should succeed for DeleteChangeForValidation
      |if user has group admin access"""".stripMargin) {
    val deleteA =
      DeleteChangeForValidation(validZone, "valid", DeleteChangeInput("valid.ok.", RecordType.A))
    val existingDeleteRecord = rsOk.copy(zoneId = deleteA.zone.id, name = deleteA.recordName)
    val result = validateChangesWithContext(
      List(deleteA.validNel),
      ExistingRecordSets(List(existingDeleteRecord)),
      okAuth,
      None)

    result(0) shouldBe valid
  }

  property(""" validateChangesWithContext: should fail for DeleteChangeForValidation
      | if user is superUser with no other access""".stripMargin) {
    val deleteA =
      DeleteChangeForValidation(validZone, "valid", DeleteChangeInput("valid.ok.", RecordType.A))
    val existingDeleteRecord = rsOk.copy(zoneId = deleteA.zone.id, name = deleteA.recordName)
    val result = validateChangesWithContext(
      List(deleteA.validNel),
      ExistingRecordSets(List(existingDeleteRecord)),
      AuthPrincipal(superUser, Seq.empty),
      None)

    result(0) should haveInvalid[DomainValidationError](UserIsNotAuthorized(superUser.userName))
  }

  property(
    "validateChangesWithContext: should succeed for DeleteChangeForValidation if user has necessary ACL rule") {
    val deleteA = DeleteChangeForValidation(
      validZone.addACLRule(
        ACLRule(accessLevel = AccessLevel.Delete, userId = Some(notAuth.userId))),
      "valid",
      DeleteChangeInput("valid.ok.", RecordType.A))
    val existingDeleteRecord = rsOk.copy(zoneId = deleteA.zone.id, name = deleteA.recordName)
    val result = validateChangesWithContext(
      List(deleteA.validNel),
      ExistingRecordSets(List(existingDeleteRecord)),
      notAuth,
      None)

    result(0) shouldBe valid
  }

  property(
    """validateChangesWithContext: should fail DeleteChangeForValidation with UserIsNotAuthorized if user
      |is not a superuser, doesn't have group admin access, or doesn't have necessary ACL rule""".stripMargin) {
    val deleteA = DeleteChangeForValidation(
      validZone.addACLRule(ACLRule(accessLevel = AccessLevel.Write, userId = Some(notAuth.userId))),
      "valid",
      DeleteChangeInput("valid.ok.", RecordType.A))
    val existingDeleteRecord = rsOk.copy(zoneId = deleteA.zone.id, name = deleteA.recordName)
    val result = validateChangesWithContext(
      List(deleteA.validNel),
      ExistingRecordSets(List(existingDeleteRecord)),
      notAuth,
      None)

    result(0) should haveInvalid[DomainValidationError](
      UserIsNotAuthorized(notAuth.signedInUser.userName))
  }

  property("""validateChangesWithContext: should properly process batch that contains
      |a CNAME and different type record with the same name""".stripMargin) {
    val addDuplicateA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.com.", RecordType.A, ttl, AData("10.1.1.1")))
    val addDuplicateCname = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.com.", RecordType.CNAME, ttl, CNAMEData("thing.com.")))

    val deleteA =
      DeleteChangeForValidation(okZone, "delete", DeleteChangeInput("delete.ok.", RecordType.A))
    val addCname = AddChangeForValidation(
      okZone,
      "delete",
      AddChangeInput("delete.ok.", RecordType.CNAME, ttl, CNAMEData("thing.com.")))
    val addA = AddChangeForValidation(
      okZone,
      "delete-this",
      AddChangeInput("delete-this.ok.", RecordType.A, ttl, AData("10.1.1.1")))
    val deleteCname = DeleteChangeForValidation(
      okZone,
      "delete",
      DeleteChangeInput("delete-this.ok.", RecordType.CNAME))
    val existingA = rsOk.copy(zoneId = deleteA.zone.id, name = deleteA.recordName)
    val existingCname =
      rsOk.copy(zoneId = deleteCname.zone.id, name = deleteCname.recordName, typ = RecordType.CNAME)
    val result = validateChangesWithContext(
      List(
        addDuplicateA.validNel,
        addDuplicateCname.validNel,
        deleteA.validNel,
        addCname.validNel,
        addA.validNel,
        deleteCname.validNel),
      ExistingRecordSets(List(existingA, existingCname)),
      okAuth,
      None
    )

    result(0) shouldBe valid
    result(1) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch(addDuplicateCname.inputChange.inputName, RecordType.CNAME))
    result(2) shouldBe valid
    result(3) shouldBe valid
    result(4) shouldBe valid
    result(5) shouldBe valid
  }

  property("validateChangesWithContext: should succeed with add CNAME, delete A of the same name") {
    val existingA = rsOk.copy(name = "new")

    val deleteA =
      DeleteChangeForValidation(okZone, "new", DeleteChangeInput("new.ok.", RecordType.A))
    val addA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.ok.", RecordType.A, ttl, AData("1.1.1.1")))
    val addAAAA = AddChangeForValidation(
      okZone,
      "testAAAA",
      AddChangeInput("testAAAA.ok.", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8")))
    val addCname = AddChangeForValidation(
      okZone,
      "new",
      AddChangeInput("new.ok.", RecordType.CNAME, ttl, CNAMEData("hey.ok.")))
    val addPtr = AddChangeForValidation(
      okZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData("test.ok.")))
    val result = validateChangesWithContext(
      List(deleteA.validNel, addA.validNel, addAAAA.validNel, addCname.validNel, addPtr.validNel),
      ExistingRecordSets(List(existingA)),
      okAuth,
      None)
    result.map(_ shouldBe valid)
  }

  property(
    "validateChangesWithContext: should succeed with add AAAA, delete CNAME of the same name") {
    val existingCname =
      rsOk.copy(name = "new", typ = RecordType.CNAME, records = List(CNAMEData("hey.ok.")))

    val deleteCname =
      DeleteChangeForValidation(okZone, "new", DeleteChangeInput("new.ok.", RecordType.CNAME))
    val addA = AddChangeForValidation(
      okZone,
      "test",
      AddChangeInput("test.ok.", RecordType.A, ttl, AData("1.1.1.1")))
    val addAAAA = AddChangeForValidation(
      okZone,
      "new",
      AddChangeInput("new.ok.", RecordType.AAAA, ttl, AAAAData("1:2:3:4:5:6:7:8")))
    val addPtr = AddChangeForValidation(
      okZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData("test.ok.")))

    val result = validateChangesWithContext(
      List(deleteCname.validNel, addA.validNel, addAAAA.validNel, addPtr.validNel),
      ExistingRecordSets(List(existingCname)),
      okAuth,
      None)
    result.map(_ shouldBe valid)
  }

  property(
    "validateChangesWithContext: should succeed with delete and add (update) of same CNAME input name") {
    val existingCname =
      rsOk.copy(name = "new", typ = RecordType.CNAME, records = List(CNAMEData("hey.ok.")))

    val deleteCname =
      DeleteChangeForValidation(okZone, "new", DeleteChangeInput("new.ok.", RecordType.CNAME))
    val addCname = AddChangeForValidation(
      okZone,
      "new",
      AddChangeInput("new.ok.", RecordType.CNAME, ttl, CNAMEData("updateData.com")))
    val result = validateChangesWithContext(
      List(deleteCname.validNel, addCname.validNel),
      ExistingRecordSets(List(existingCname)),
      okAuth,
      None)
    result.map(_ shouldBe valid)
  }

  property("validateChangesWithContext: should fail on CNAME update including multiple adds") {
    val existingCname = rsOk.copy(
      zoneId = okZone.id,
      name = "name-conflict",
      typ = RecordType.CNAME,
      records = List(CNAMEData("existing.cname.")))

    val deleteUpdateCname = DeleteChangeForValidation(
      okZone,
      "name-conflict",
      DeleteChangeInput("existing.ok.", RecordType.CNAME))
    val addUpdateCname = AddChangeForValidation(
      okZone,
      "name-conflict",
      AddChangeInput("add.ok.", RecordType.CNAME, ttl, CNAMEData("updated.cname.")))
    val addCname = AddChangeForValidation(
      okZone,
      "name-conflict",
      AddChangeInput("add.ok.", RecordType.CNAME, ttl, CNAMEData("new.add.cname.")))

    val result = validateChangesWithContext(
      List(deleteUpdateCname.validNel, addUpdateCname.validNel, addCname.validNel),
      ExistingRecordSets(List(existingCname)),
      okAuth,
      None)

    result(0) shouldBe valid
    result(1) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch("add.ok.", RecordType.CNAME))
    result(2) should haveInvalid[DomainValidationError](
      RecordNameNotUniqueInBatch("add.ok.", RecordType.CNAME))
  }

  property("validateChangesWithContext: should succeed with add CNAME, delete PTR of the same name") {
    val existingPtr = rsOk.copy(
      zoneId = validIp4ReverseZone.id,
      name = "193",
      typ = RecordType.PTR,
      records = List(PTRData("hey.there.")))

    val deletePtr = DeleteChangeForValidation(
      validIp4ReverseZone,
      "193",
      DeleteChangeInput("192.0.2.193", RecordType.PTR))
    val addCname = AddChangeForValidation(
      validIp4ReverseZone,
      "193",
      AddChangeInput("test.ok.", RecordType.CNAME, ttl, CNAMEData("hey2.there.")))

    val result = validateChangesWithContext(
      List(deletePtr.validNel, addCname.validNel),
      ExistingRecordSets(List(existingPtr)),
      okAuth,
      None)
    result.map(_ shouldBe valid)
  }

  property(
    "validateChangesWithContext: should succeed with delete and add (update) of same PTR input name") {
    val existingPtr = rsOk.copy(
      zoneId = validIp4ReverseZone.id,
      name = "193",
      typ = RecordType.PTR,
      records = List(PTRData("hey.ok.")))

    val deletePtr = DeleteChangeForValidation(
      validIp4ReverseZone,
      "193",
      DeleteChangeInput("192.0.2.193", RecordType.PTR))
    val addPtr = AddChangeForValidation(
      validIp4ReverseZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData("updateData.com")))
    val result = validateChangesWithContext(
      List(deletePtr.validNel, addPtr.validNel),
      ExistingRecordSets(List(existingPtr)),
      okAuth,
      None)
    result.map(_ shouldBe valid)
  }

  property("validateChangesWithContext: should succeed on PTR update including multiple adds") {
    val existingPtr = rsOk.copy(
      zoneId = validIp4ReverseZone.id,
      name = "193",
      typ = RecordType.PTR,
      records = List(PTRData("existing.ptr.")))

    val deleteUpdatePtr = DeleteChangeForValidation(
      validIp4ReverseZone,
      "193",
      DeleteChangeInput("192.0.2.193", RecordType.PTR))
    val addUpdatePtr = AddChangeForValidation(
      validIp4ReverseZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData("updated.ptr.")))
    val addPtr = AddChangeForValidation(
      validIp4ReverseZone,
      "193",
      AddChangeInput("192.0.2.193", RecordType.PTR, ttl, PTRData("new.add.ptr.")))

    val result = validateChangesWithContext(
      List(deleteUpdatePtr.validNel, addUpdatePtr.validNel, addPtr.validNel),
      ExistingRecordSets(List(existingPtr)),
      okAuth,
      None)

    result.map(_ shouldBe valid)
  }

  property("validateAddChangeInput: should succeed for a valid TXT addChangeInput") {
    val input = AddChangeInput("txt.ok.", RecordType.TXT, ttl, TXTData("test"))
    val result = validateAddChangeInput(input)
    result shouldBe valid
  }

  property("validateAddChangeInput: should fail for a TXT addChangeInput with empty TXTData") {
    val input = AddChangeInput("txt.ok.", RecordType.TXT, ttl, TXTData(""))
    val result = validateAddChangeInput(input)
    result should haveInvalid[DomainValidationError](InvalidLength("", 1, 64764))
  }

  property(
    "validateAddChangeInput: should fail for a TXT addChangeInput with TXTData that is too many characters") {
    val txtData = "x" * 64765
    val input = AddChangeInput("txt.ok.", RecordType.TXT, ttl, TXTData(txtData))
    val result = validateAddChangeInput(input)
    result should haveInvalid[DomainValidationError](InvalidLength(txtData, 1, 64764))
  }

  property("validateAddChangeInput: should succeed for a valid MX addChangeInput") {
    val input = AddChangeInput("mx.ok.", RecordType.MX, ttl, MXData(1, "foo.bar."))
    val result = validateAddChangeInput(input)
    result shouldBe valid
  }

  property("validateAddChangeInput: should fail for a MX addChangeInput with invalid preference") {
    val inputSmall = AddChangeInput("mx.ok.", RecordType.MX, ttl, MXData(-1, "foo.bar."))
    val inputLarge = AddChangeInput("mx.ok.", RecordType.MX, ttl, MXData(1000000, "foo.bar."))
    val resultSmall = validateAddChangeInput(inputSmall)
    val resultLarge = validateAddChangeInput(inputLarge)

    resultSmall should haveInvalid[DomainValidationError](InvalidMxPreference(-1))
    resultLarge should haveInvalid[DomainValidationError](InvalidMxPreference(1000000))
  }

  property("validateAddChangeInput: should fail for a MX addChangeInput with invalid exchange") {
    val input = AddChangeInput("mx.ok.", RecordType.MX, ttl, MXData(1, "foo$.bar."))
    val result = validateAddChangeInput(input)
    result should haveInvalid[DomainValidationError](InvalidDomainName("foo$.bar."))
  }

  property(
    "validateAddChangeInput: should fail for a MX addChangeInput with invalid preference and exchange") {
    val input = AddChangeInput("mx.ok.", RecordType.MX, ttl, MXData(-1, "foo$.bar."))
    val result = validateAddChangeInput(input)
    result should haveInvalid[DomainValidationError](InvalidMxPreference(-1))
    result should haveInvalid[DomainValidationError](InvalidDomainName("foo$.bar."))
  }

  property("validateChangesWithContext: should fail if MX record in batch already exists") {
    val existingMX = rsOk.copy(
      zoneId = okZone.id,
      name = "name-conflict",
      typ = RecordType.MX,
      records = List(MXData(1, "foo.bar.")))
    val addMX = AddChangeForValidation(
      okZone,
      "name-conflict",
      AddChangeInput("name-conflict", RecordType.MX, ttl, MXData(1, "foo.bar.")))

    val result =
      validateChangesWithContext(
        List(addMX.validNel),
        ExistingRecordSets(List(existingMX)),
        okAuth,
        None)
    result(0) should haveInvalid[DomainValidationError](RecordAlreadyExists("name-conflict."))
  }

  property("validateChangesWithContext: should succeed if duplicate MX records in batch") {
    val addMx = AddChangeForValidation(
      okZone,
      "name-conflict",
      AddChangeInput("name-conflict", RecordType.MX, ttl, MXData(1, "foo.bar.")))
    val addMx2 = AddChangeForValidation(
      okZone,
      "name-conflict",
      AddChangeInput("name-conflict", RecordType.MX, ttl, MXData(2, "foo.bar.")))

    val result = validateChangesWithContext(
      List(addMx.validNel, addMx2.validNel),
      ExistingRecordSets(List()),
      okAuth,
      None)
    result(0) shouldBe valid
  }

  property("validateChangesWithContext: should succeed if MX already exists and is deleted first") {
    val existingMx = rsOk.copy(
      zoneId = okZone.id,
      name = "name-conflict",
      typ = RecordType.MX,
      records = List(MXData(1, "foo.bar.")))
    val deleteMx = DeleteChangeForValidation(
      okZone,
      "name-conflict",
      DeleteChangeInput("name-conflict", RecordType.MX))
    val addMx = AddChangeForValidation(
      okZone,
      "name-conflict",
      AddChangeInput("name-conflict", RecordType.MX, ttl, MXData(1, "foo.bar.")))

    val result = validateChangesWithContext(
      List(deleteMx.validNel, addMx.validNel),
      ExistingRecordSets(List(existingMx)),
      okAuth,
      None)
    result(0) shouldBe valid
  }

  property("validateChangesWithContext: should properly validate changes with owner group ID") {
    val result = validateChangesWithContext(
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
        )),
      AuthPrincipal(okUser, Seq(abcGroup.id, okGroup.id)),
      Some("some-owner-group-id")
    )

    result.foreach(_ shouldBe valid)
  }

  property("validateChangesWithContext: should properly validate changes without owner group ID") {
    val result = validateChangesWithContext(
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
        )),
      AuthPrincipal(okUser, Seq(abcGroup.id, okGroup.id)),
      None
    )

    result(0) shouldBe valid
    result(1) should
      haveInvalid[DomainValidationError](
        MissingOwnerGroupId(createSharedAddChange.recordName, createSharedAddChange.zone.name))
    result(2) shouldBe valid
    result(3) shouldBe valid
    result(4) should
      haveInvalid[DomainValidationError](
        MissingOwnerGroupId(updateSharedAddChange.recordName, updateSharedAddChange.zone.name))
    result(5) shouldBe valid
    result(6) shouldBe valid
    result(7) shouldBe valid
  }

  property(
    "validateChangesWithContext: should fail deleting record for normal user not in owner group in shared zone") {
    val result = validateChangesWithContext(
      List(deleteSharedChange.validNel),
      ExistingRecordSets(List(sharedZoneRecord.copy(name = "shared-delete"))),
      dummyAuth,
      None)

    result(0) should
      haveInvalid[DomainValidationError](UserIsNotAuthorized(dummyAuth.signedInUser.userName))
  }

  property(
    "validateChangesWithContext: should delete record without owner group for normal user in shared zone") {
    val result = validateChangesWithContext(
      List(deleteSharedChange.validNel),
      ExistingRecordSets(List(sharedZoneRecord.copy(name = "shared-delete"))),
      okAuth,
      None)

    result(0) shouldBe valid
  }

  property("validateChangesWithContext: should delete record for zone admin in shared zone") {
    val result = validateChangesWithContext(
      List(deleteSharedChange.validNel),
      ExistingRecordSets(List(sharedZoneRecord.copy(name = "shared-delete"))),
      sharedAuth,
      None)

    result(0) shouldBe valid
  }

  property(
    "validateChangesWithContext: succeed update/delete to a multi record existing RecordSet if multi enabled") {
    val existing = List(
      sharedZoneRecord.copy(
        name = updateSharedAddChange.recordName,
        records = List(AAAAData("1::1"), AAAAData("2::2"))),
      sharedZoneRecord.copy(
        name = deleteSharedChange.recordName,
        records = List(AAAAData("1::1"), AAAAData("2::2"))),
      rsOk.copy(name = updatePrivateAddChange.recordName),
      rsOk.copy(name = deletePrivateChange.recordName)
    )

    val result = underTest.validateChangesWithContext(
      List(
        updateSharedAddChange.validNel,
        updateSharedDeleteChange.validNel,
        deleteSharedChange.validNel,
        updatePrivateAddChange.validNel,
        updatePrivateDeleteChange.validNel,
        deletePrivateChange.validNel
      ),
      ExistingRecordSets(existing),
      okAuth,
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

  property(
    "validateChangesWithContext: fail on update/delete to a multi record existing RecordSet if multi disabled") {
    val existing = List(
      sharedZoneRecord.copy(
        name = updateSharedAddChange.recordName,
        records = List(AAAAData("1::1"), AAAAData("2::2"))),
      sharedZoneRecord.copy(
        name = deleteSharedChange.recordName,
        records = List(AAAAData("1::1"), AAAAData("2::2"))),
      rsOk.copy(name = updatePrivateAddChange.recordName),
      rsOk.copy(name = deletePrivateChange.recordName)
    )

    val result = underTestMultiDisabled.validateChangesWithContext(
      List(
        updateSharedAddChange.validNel,
        updateSharedDeleteChange.validNel,
        deleteSharedChange.validNel,
        updatePrivateAddChange.validNel,
        updatePrivateDeleteChange.validNel,
        deletePrivateChange.validNel
      ),
      ExistingRecordSets(existing),
      okAuth,
      Some(okGroup.id)
    )

    result(0) should haveInvalid[DomainValidationError](
      ExistingMultiRecordError(updateSharedAddChange.inputChange.inputName, existing(0)))
    result(1) should haveInvalid[DomainValidationError](
      ExistingMultiRecordError(updateSharedDeleteChange.inputChange.inputName, existing(0)))
    result(2) should haveInvalid[DomainValidationError](
      ExistingMultiRecordError(deleteSharedChange.inputChange.inputName, existing(1)))
    // non duplicate
    result(3) shouldBe valid
    result(4) shouldBe valid
    result(5) shouldBe valid
  }

  property("validateChangesWithContext: succeed on add/update to a multi record if multi enabled") {
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
      List(
        updateSharedDeleteChange.validNel,
        update1.validNel,
        update2.validNel,
        add1.validNel,
        add2.validNel,
        updatePrivateAddChange.validNel
      ),
      ExistingRecordSets(existing),
      okAuth,
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

  property("validateChangesWithContext: fail on add/update to a multi record if multi disabled") {
    val existing = List(
      sharedZoneRecord.copy(
        name = updateSharedAddChange.recordName,
        records = List(AAAAData("1::1"))
      )
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

    val result = underTestMultiDisabled.validateChangesWithContext(
      List(
        updateSharedDeleteChange.validNel,
        update1.validNel,
        update2.validNel,
        add1.validNel,
        add2.validNel,
        updatePrivateAddChange.validNel
      ),
      ExistingRecordSets(existing),
      okAuth,
      Some(okGroup.id)
    )

    result(0) shouldBe valid
    result(1) should haveInvalid[DomainValidationError](NewMultiRecordError(update1))
    result(2) should haveInvalid[DomainValidationError](NewMultiRecordError(update2))
    result(3) should haveInvalid[DomainValidationError](NewMultiRecordError(add1))
    result(4) should haveInvalid[DomainValidationError](NewMultiRecordError(add2))
    // non duplicate
    result(5) shouldBe valid
  }
}
