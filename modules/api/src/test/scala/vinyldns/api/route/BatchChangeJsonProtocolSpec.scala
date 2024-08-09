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

import cats.data.NonEmptyList
import cats.scalatest.{ValidatedMatchers, ValidatedValues}
import cats.syntax.all._
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json4s.Extraction._
import org.json4s.JsonDSL._
import org.json4s._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.domain._
import vinyldns.api.domain.batch.BatchTransformations.{AddChangeForValidation, ChangeForValidation}
import vinyldns.api.domain.batch.ChangeInputType._
import vinyldns.api.domain.batch._
import vinyldns.core.TestZoneData.okZone
import vinyldns.core.domain.{
  DomainValidationErrorType,
  Fqdn,
  InvalidIpv4Address,
  InvalidTTL,
  SingleChangeError,
  ZoneDiscoveryError
}
import vinyldns.core.domain.batch.SingleChangeStatus._
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record._

class BatchChangeJsonProtocolSpec
    extends AnyWordSpec
    with Matchers
    with BatchChangeJsonProtocol
    with ValidatedValues
    with ValidatedMatchers {

  val serializers: Seq[Serializer[_]] = batchChangeSerializers

  def buildAddChangeInputJson(
      inputName: Option[String] = None,
      typ: Option[RecordType] = None,
      ttl: Option[Int] = None,
      record: Option[RecordData] = None
  ): JObject =
    JObject(
      List(
        Some("changeType" -> decompose(Add)),
        inputName.map("inputName" -> JString(_)),
        typ.map("type" -> decompose(_)),
        ttl.map("ttl" -> JInt(_)),
        record.map("record" -> decompose(_))
      ).flatten
    )

  def buildDeleteRRSetInputJson(
      inputName: Option[String] = None,
      typ: Option[RecordType] = None,
      record: Option[RecordData] = None
  ): JObject =
    JObject(
      List(
        Some("changeType" -> decompose(DeleteRecordSet)),
        inputName.map("inputName" -> JString(_)),
        typ.map("type" -> decompose(_)),
        record.map("record" -> decompose(_))
      ).flatten
    )

  val addAChangeInputJson: JObject =
    buildAddChangeInputJson(Some("foo."), Some(A), Some(3600), Some(AData("1.1.1.1")))

  val addAAAAChangeInputJson: JObject =
    buildAddChangeInputJson(Some("bar."), Some(AAAA), Some(1200), Some(AAAAData("1:2:3:4:5:6:7:8")))

  val addCNAMEChangeInputJson: JObject =
    buildAddChangeInputJson(
      Some("bizz.baz."),
      Some(CNAME),
      Some(200),
      Some(CNAMEData(Fqdn("buzz.")))
    )

  val addPTRChangeInputJson: JObject =
    buildAddChangeInputJson(Some("4.5.6.7"), Some(PTR), Some(200), Some(PTRData(Fqdn("test.com."))))

  val deleteAChangeInputJson: JObject = buildDeleteRRSetInputJson(Some("foo."), Some(A))

  val addChangeList: JObject = "changes" -> List(
    addAChangeInputJson,
    addAAAAChangeInputJson,
    addCNAMEChangeInputJson,
    addPTRChangeInputJson
  )

  val addDeleteChangeList: JObject = "changes" -> List(
    deleteAChangeInputJson,
    addAAAAChangeInputJson,
    addCNAMEChangeInputJson
  )

  val addBatchChangeInputWithComment: JObject = ("comments" -> Some("some comment")) ~~
    addChangeList

  val addBatchChangeInputWithOwnerGroupId: JObject = ("ownerGroupId" -> Some("owner-group-id")) ~~
    addBatchChangeInputWithComment

  val changeInputWithManualReviewDisabled: JObject = "changes" -> List(
    deleteAChangeInputJson,
    addAAAAChangeInputJson,
    addCNAMEChangeInputJson
  )

  val addAChangeInput = AddChangeInput("foo.", A, None, Some(3600), AData("1.1.1.1"))

  val deleteAChangeInput = DeleteRRSetChangeInput("foo.", A, None)

  val addAAAAChangeInput = AddChangeInput("bar.", AAAA, None, Some(1200), AAAAData("1:2:3:4:5:6:7:8"))

  val addCNAMEChangeInput = AddChangeInput("bizz.baz.", CNAME, None, Some(200), CNAMEData(Fqdn("buzz.")))

  val addPTRChangeInput = AddChangeInput("4.5.6.7", PTR, None, Some(200), PTRData(Fqdn("test.com.")))

  val fooDiscoveryError = ZoneDiscoveryError("foo.")

  val barDiscoveryError = ZoneDiscoveryError("bar.")

  val validChangeString = "Valid change."

  "De-serializing ChangeInputSerializer from JSON" should {
    "successfully serialize valid add change data" in {
      val resultA = ChangeInputSerializer.fromJson(addAChangeInputJson).value

      resultA shouldBe addAChangeInput

      val resultAAAA = ChangeInputSerializer.fromJson(addAAAAChangeInputJson).value

      resultAAAA shouldBe addAAAAChangeInput

      val resultCNAME = ChangeInputSerializer.fromJson(addCNAMEChangeInputJson).value

      resultCNAME shouldBe addCNAMEChangeInput

      val resultPTR = ChangeInputSerializer.fromJson(addPTRChangeInputJson).value

      resultPTR shouldBe addPTRChangeInput
    }

    "successfully serialize valid data for delete" in {
      val json = deleteAChangeInputJson
      val result = ChangeInputSerializer.fromJson(json).value

      result shouldBe deleteAChangeInput
    }

    "return an error if changeType is not specified" in {
      val result = ChangeInputSerializer.fromJson("test" -> "test")

      result should haveInvalid("Missing BatchChangeInput.changes.changeType")
    }

    "return an error if an unsupported record type is specified" in {
      val json = buildAddChangeInputJson(Some("bizz.baz."), Some(UNKNOWN), Some(2000))
      val result = ChangeInputSerializer.fromJson(json)

      result should haveInvalid(
        s"Unsupported type $UNKNOWN, valid types include: A, AAAA, CNAME, PTR, TXT, MX, NS, SRV and NAPTR"
      )
    }

    "return an error if the FQDN is not specified" in {
      val json =
        buildAddChangeInputJson(typ = Some(A), ttl = Some(3600), record = Some(AData("1.1.1.1")))
      val result = ChangeInputSerializer.fromJson(json)

      result should haveInvalid("Missing BatchChangeInput.changes.inputName")
    }

    "return an error if the type is not specified" in {
      val json = buildAddChangeInputJson(
        inputName = Some("foo."),
        ttl = Some(3600),
        record = Some(AData("1.1.1.1"))
      )
      val result = ChangeInputSerializer.fromJson(json)

      result should haveInvalid("Missing BatchChangeInput.changes.type")
    }

    "succeed if the ttl is not specified for an add change" in {
      val json = buildAddChangeInputJson(
        inputName = Some("foo."),
        typ = Some(A),
        record = Some(AData("1.1.1.1"))
      )
      val result = ChangeInputSerializer.fromJson(json).value

      result shouldBe AddChangeInput("foo.", A, None, None, AData("1.1.1.1"))
    }

    "return an error if the record is not specified for add" in {
      val jsonA = buildAddChangeInputJson(Some("foo."), Some(A), Some(3600))
      val resultA = ChangeInputSerializer.fromJson(jsonA)

      resultA should haveInvalid("Missing BatchChangeInput.changes.record.address")

      val jsonAAAA = buildAddChangeInputJson(Some("foo."), Some(AAAA), Some(3600))
      val resultAAAA = ChangeInputSerializer.fromJson(jsonAAAA)

      resultAAAA should haveInvalid("Missing BatchChangeInput.changes.record.address")
    }

    "return an error if the record data is not specified for NS" in {
      val jsonNS = buildAddChangeInputJson(Some("foo."), Some(NS), Some(3600))
      val resultNS = ChangeInputSerializer.fromJson(jsonNS)

      resultNS should haveInvalid("Missing BatchChangeInput.changes.record.nsdname")
    }

    "return an error if the record data is not specified for SRV" in {
      val jsonSRV = buildAddChangeInputJson(Some("foo."), Some(SRV), Some(3600))
      val resultSRV = ChangeInputSerializer.fromJson(jsonSRV)

      resultSRV should haveInvalid("Missing BatchChangeInput.changes.record.priority and Missing BatchChangeInput.changes.record.weight and Missing BatchChangeInput.changes.record.port and Missing BatchChangeInput.changes.record.target")
    }

    "return an error if the record data is not specified for NAPTR" in {
      val jsonNAPTR = buildAddChangeInputJson(Some("foo."), Some(NAPTR), Some(3600))
      val resultNAPTR = ChangeInputSerializer.fromJson(jsonNAPTR)

      resultNAPTR should haveInvalid("Missing BatchChangeInput.changes.record.order and Missing BatchChangeInput.changes.record.preference and Missing BatchChangeInput.changes.record.flags and Missing BatchChangeInput.changes.record.service and Missing BatchChangeInput.changes.record.regexp and Missing BatchChangeInput.changes.record.replacement")
    }
  }

  "serializing ChangeInputSerializer to JSON" should {
    "successfully serialize valid data for delete" in {
      val deleteChangeInput = DeleteRRSetChangeInput("foo.", A, None, Some(AData("1.1.1.1")))
      val json: JObject = buildDeleteRRSetInputJson(Some("foo."), Some(A), Some(AData("1.1.1.1")))
      val result = ChangeInputSerializer.toJson(deleteChangeInput)

      result shouldBe json
    }
  }

  "De-serializing BatchChangeInput from JSON" should {
    "successfully serialize valid add change data with comment and without owner group ID" in {
      val result = BatchChangeInputSerializer.fromJson(addBatchChangeInputWithComment).value

      result shouldBe BatchChangeInput(
        Some("some comment"),
        List(addAChangeInput, addAAAAChangeInput, addCNAMEChangeInput, addPTRChangeInput),
        None
      )
    }

    "successfully serialize valid add change data without comment and owner group ID" in {
      val result = BatchChangeInputSerializer.fromJson(addChangeList).value

      result shouldBe BatchChangeInput(
        None,
        List(addAChangeInput, addAAAAChangeInput, addCNAMEChangeInput, addPTRChangeInput)
      )
    }

    "successfully serialize valid add and delete change data without comment and owner group ID" in {
      val result = BatchChangeInputSerializer.fromJson(addDeleteChangeList).value

      result shouldBe BatchChangeInput(
        None,
        List(deleteAChangeInput, addAAAAChangeInput, addCNAMEChangeInput)
      )
    }

    "successfully serialize valid add and delete change with comment and owner group ID" in {
      val batchChange = BatchChangeInput(
        Some("some comment"),
        List(
          deleteAChangeInput,
          addAChangeInput,
          addAAAAChangeInput,
          addCNAMEChangeInput,
          addPTRChangeInput
        ),
        Some("owner-group-id")
      )

      BatchChangeInputSerializer
        .fromJson(BatchChangeInputSerializer.toJson(batchChange))
        .value shouldBe batchChange
    }

    "successfully serialize valid add and delete change without comment and with owner group ID" in {
      val batchChange = BatchChangeInput(
        None,
        List(
          deleteAChangeInput,
          addAChangeInput,
          addAAAAChangeInput,
          addCNAMEChangeInput,
          addPTRChangeInput
        ),
        Some("owner-group-id")
      )

      BatchChangeInputSerializer
        .fromJson(BatchChangeInputSerializer.toJson(batchChange))
        .value shouldBe batchChange
    }

    "return an error if the changes are not specified" in {
      val json = "comments" -> "some comments"
      val result = BatchChangeInputSerializer.fromJson(json)

      result should haveInvalid("Missing BatchChangeInput.changes")

      BatchChangeInputSerializer.fromJson("") should haveInvalid("Missing BatchChangeInput.changes")
    }

    "return an error if the data is not specified" in {
      val json = "changeType" -> decompose(Add)
      val result = AddChangeInputSerializer.fromJson(json)

      result should haveInvalid("Missing BatchChangeInput.changes.inputName")
      result should haveInvalid("Missing BatchChangeInput.changes.type")
    }
  }

  "Serializing SingleAddChange to JSON" should {
    "successfully serialize" in {
      val toJson = SingleAddChange(
        Some("zoneId"),
        Some("zoneName"),
        Some("recordName"),
        "fqdn",
        A,
        30,
        AData("1.1.1.1"),
        Pending,
        Some("systemMessage"),
        None,
        None,
        List(SingleChangeError(barDiscoveryError)),
        id = "id"
      )
      val result = SingleAddChangeSerializer.toJson(toJson)

      result shouldBe ("zoneId" -> "zoneId") ~
        ("zoneName" -> "zoneName") ~
        ("recordName" -> "recordName") ~
        ("inputName" -> "fqdn") ~
        ("type" -> decompose(A)) ~
        ("ttl" -> 30) ~
        ("record" -> decompose(AData("1.1.1.1"))) ~
        ("status" -> decompose(Pending)) ~
        ("systemMessage" -> "systemMessage") ~
        ("recordChangeId" -> decompose(None)) ~
        ("recordSetId" -> decompose(None)) ~
        ("validationErrors" -> decompose(List(SingleChangeError(barDiscoveryError)))) ~
        ("id" -> "id") ~
        ("changeType" -> "Add")
    }
  }

  "Serializing SingleDeleteRRSetChange to JSON" should {
    "successfully serialize when record data is not provided" in {
      val toJson = SingleDeleteRRSetChange(
        Some("zoneId"),
        Some("zoneName"),
        Some("recordName"),
        "fqdn",
        A,
        None,
        Pending,
        Some("systemMessage"),
        None,
        None,
        List(SingleChangeError(barDiscoveryError)),
        id = "id"
      )
      val result = SingleDeleteRRSetChangeSerializer.toJson(toJson)

      result shouldBe ("zoneId" -> "zoneId") ~
        ("zoneName" -> "zoneName") ~
        ("recordName" -> "recordName") ~
        ("inputName" -> "fqdn") ~
        ("type" -> decompose(A)) ~
        ("record" -> decompose(None)) ~
        ("status" -> decompose(Pending)) ~
        ("systemMessage" -> "systemMessage") ~
        ("recordChangeId" -> decompose(None)) ~
        ("recordSetId" -> decompose(None)) ~
        ("validationErrors" -> decompose(List(SingleChangeError(barDiscoveryError)))) ~
        ("id" -> "id") ~
        ("changeType" -> "DeleteRecordSet")
    }

    "successfully serialize when record data is provided" in {
      val toJson = SingleDeleteRRSetChange(
        Some("zoneId"),
        Some("zoneName"),
        Some("recordName"),
        "fqdn",
        A,
        Some(AData("1.2.3.4")),
        Pending,
        Some("systemMessage"),
        None,
        None,
        List(SingleChangeError(barDiscoveryError)),
        id = "id"
      )
      val result = SingleDeleteRRSetChangeSerializer.toJson(toJson)

      result shouldBe ("zoneId" -> "zoneId") ~
        ("zoneName" -> "zoneName") ~
        ("recordName" -> "recordName") ~
        ("inputName" -> "fqdn") ~
        ("type" -> decompose(A)) ~
        ("record" -> decompose(Some(AData("1.2.3.4")))) ~
        ("status" -> decompose(Pending)) ~
        ("systemMessage" -> "systemMessage") ~
        ("recordChangeId" -> decompose(None)) ~
        ("recordSetId" -> decompose(None)) ~
        ("validationErrors" -> decompose(List(SingleChangeError(barDiscoveryError)))) ~
        ("id" -> "id") ~
        ("changeType" -> "DeleteRecordSet")
    }
  }

  "Serializing BatchChange to JSON" should {
    "successfully serialize" in {
      val delete = SingleDeleteRRSetChange(
        Some("zoneId"),
        Some("zoneName"),
        Some("recordName"),
        "fqdn",
        A,
        None,
        Pending,
        Some("systemMessage"),
        None,
        None,
        id = "id"
      )
      val add = SingleAddChange(
        Some("zoneId"),
        Some("zoneName"),
        Some("recordName"),
        "fqdn",
        A,
        30,
        AData("1.1.1.1"),
        Pending,
        Some("systemMessage"),
        None,
        None,
        id = "id"
      )

      val time = Instant.now.truncatedTo(ChronoUnit.MILLIS)
      val batchChange = BatchChange(
        "someUserId",
        "someUserName",
        Some("these be comments!"),
        time,
        List(add, delete),
        None,
        BatchChangeApprovalStatus.PendingReview,
        None,
        None,
        None,
        "someId"
      )
      val result = BatchChangeSerializer.toJson(batchChange)

      result shouldBe ("userId" -> "someUserId") ~
        ("userName" -> "someUserName") ~
        ("comments" -> "these be comments!") ~
        ("createdTimestamp" -> decompose(time)) ~
        ("changes" -> decompose(List(add, delete))) ~
        ("status" -> decompose(BatchChangeStatus.PendingReview)) ~
        ("id" -> "someId") ~
        ("ownerGroupId" -> JNothing) ~
        ("approvalStatus" -> decompose(BatchChangeApprovalStatus.PendingReview)) ~
        ("reviewerId" -> JNothing) ~
        ("reviewComment" -> JNothing) ~
        ("reviewTimestamp" -> JNothing) ~
        ("scheduledTime" -> JNothing) ~
        ("cancelledTimestamp" -> JNothing)
    }
  }

  "Serializing BatchChangeErrorList" should {
    "serialize changes for valid inputs" in {
      val onlyValid = List(
        AddChangeForValidation(okZone, "foo", addAChangeInput, 7200).validNel,
        AddChangeForValidation(okZone, "bar", addAAAAChangeInput, 7200).validNel
      )
      val result = BatchChangeErrorListSerializer.toJson(
        InvalidBatchChangeResponses(List(addAChangeInput, addAAAAChangeInput), onlyValid)
      )

      result shouldBe decompose(List(addAChangeInput, addAAAAChangeInput))
    }

    "serialize BatchChangeErrors to their corresponding messages" in {
      val onlyErrors = List(fooDiscoveryError.invalidNel, barDiscoveryError.invalidNel)
      val result = BatchChangeErrorListSerializer.toJson(
        InvalidBatchChangeResponses(List(addAChangeInput, addAAAAChangeInput), onlyErrors)
      )

      result shouldBe decompose(
        List(
          decompose(addAChangeInput)
            .asInstanceOf[JObject] ~ ("errors" -> List(fooDiscoveryError.message)),
          decompose(addAAAAChangeInput)
            .asInstanceOf[JObject] ~ ("errors" -> List(barDiscoveryError.message))
        )
      )
    }

    "serializing a mix of valid inputs and BatchChangeErrors should return the appropriate success or error" in {
      val errorList =
        NonEmptyList.fromListUnsafe(
          List(
            InvalidIpv4Address("bad address"),
            InvalidTTL(5, DomainValidations.TTL_MIN_LENGTH, DomainValidations.TTL_MAX_LENGTH)
          )
        )

      val validAddA = AddChangeForValidation(okZone, "foo", addAChangeInput, 7200).validNel
      val invalidAddA = errorList.invalid[ChangeForValidation]
      val invalidAddAAAA = ZoneDiscoveryError("bar.").invalidNel
      val validAddAAAA = AddChangeForValidation(okZone, "bar", addAAAAChangeInput, 7200).validNel

      val batchChangeErrorList = InvalidBatchChangeResponses(
        List(addAChangeInput, addAChangeInput, addAAAAChangeInput, addAAAAChangeInput),
        List(validAddA, invalidAddA, invalidAddAAAA, validAddAAAA)
      )

      val expected = decompose(
        List(
          addAChangeInput,
          decompose(addAChangeInput).asInstanceOf[JObject] ~
            ("errors" -> List(
              InvalidIpv4Address("bad address").message,
              InvalidTTL(5, DomainValidations.TTL_MIN_LENGTH, DomainValidations.TTL_MAX_LENGTH).message
            )),
          decompose(addAAAAChangeInput).asInstanceOf[JObject] ~
            ("errors" -> List(barDiscoveryError.message)),
          addAAAAChangeInput
        )
      )

      val result = BatchChangeErrorListSerializer.toJson(batchChangeErrorList)

      result shouldBe expected
    }
  }

  "Serializing BatchChangeRevalidationErrorList" should {
    "serialize a mix of valid and invalid inputs" in {
      val errorMessage = "Zone Discovery Failed: zone for \"foo.\" does not exist in VinylDNS. " +
        "If zone exists, then it must be connected to in VinylDNS."
      val delete = SingleDeleteRRSetChange(
        Some("zoneId"),
        Some("zoneName"),
        Some("recordName"),
        "foo",
        A,
        None,
        Pending,
        None,
        None,
        None,
        id = "id"
      )
      val add = SingleAddChange(
        Some("zoneId"),
        Some("zoneName"),
        Some("recordName"),
        "foo",
        A,
        3600,
        AData("1.1.1.1"),
        Pending,
        None,
        None,
        None,
        List(SingleChangeError(DomainValidationErrorType.ZoneDiscoveryError, errorMessage)),
        id = "id"
      )

      val time = Instant.now.truncatedTo(ChronoUnit.MILLIS)
      val batchChange = BatchChange(
        "someUserId",
        "someUserName",
        Some("these be comments!"),
        time,
        List(add, delete),
        None,
        BatchChangeApprovalStatus.PendingReview,
        None,
        None,
        None,
        "someId"
      )
      val result =
        BatchChangeRevalidationErrorListSerializer.toJson(BatchChangeFailedApproval(batchChange))

      val expected = decompose(
        List(
          decompose(addAChangeInput)
            .asInstanceOf[JObject] ~ ("errors" -> List(fooDiscoveryError.message)),
          decompose(deleteAChangeInput)
        )
      )

      result shouldBe expected
    }
  }

  "Serializing BatchChangeError" should {
    "return the message of the BatchChange" in {
      val error = ZoneDiscoveryError("name")
      BatchChangeErrorSerializer.toJson(error).values shouldBe error.message
    }
  }

  "Round-trip serialization/deserialization of a RejectBatchChangeInput" should {
    "succeed if no comments are provided" in {
      val rejectBatchChangeInput = RejectBatchChangeInput(None)
      RejectBatchChangeInputSerializer
        .fromJson(
          RejectBatchChangeInputSerializer
            .toJson(rejectBatchChangeInput)
        ) shouldBe rejectBatchChangeInput.validNel
    }

    "succeed if comments are provided" in {
      val rejectBatchChangeInput = RejectBatchChangeInput(Some("some comments"))
      RejectBatchChangeInputSerializer
        .fromJson(
          RejectBatchChangeInputSerializer
            .toJson(rejectBatchChangeInput)
        ) shouldBe rejectBatchChangeInput.validNel
    }

    "fail if comments exceed MAX_COMMENT_LENGTH characters" in {
      val rejectBatchChangeInput = RejectBatchChangeInput(Some("a" * 1025))
      RejectBatchChangeInputSerializer
        .fromJson(
          RejectBatchChangeInputSerializer
            .toJson(rejectBatchChangeInput)
        ) shouldBe
        s"Comment length must not exceed $MAX_COMMENT_LENGTH characters.".invalidNel
    }
  }

  "Round-trip serialization/deserialization of a ApproveBatchChangeInput" should {
    "succeed if no comments are provided" in {
      val approveBatchChangeInput = ApproveBatchChangeInput(None)
      ApproveBatchChangeInputSerializer
        .fromJson(
          ApproveBatchChangeInputSerializer
            .toJson(approveBatchChangeInput)
        ) shouldBe approveBatchChangeInput.validNel
    }

    "succeed if comments are provided" in {
      val approveBatchChangeInput = ApproveBatchChangeInput(Some("some comments"))
      ApproveBatchChangeInputSerializer
        .fromJson(
          ApproveBatchChangeInputSerializer
            .toJson(approveBatchChangeInput)
        ) shouldBe approveBatchChangeInput.validNel
    }

    "fail if comments exceed MAX_COMMENT_LENGTH characters" in {
      val approveBatchChangeInput = ApproveBatchChangeInput(Some("a" * 1025))
      ApproveBatchChangeInputSerializer
        .fromJson(
          ApproveBatchChangeInputSerializer
            .toJson(approveBatchChangeInput)
        ) shouldBe
        s"Comment length must not exceed $MAX_COMMENT_LENGTH characters.".invalidNel
    }
  }
}
