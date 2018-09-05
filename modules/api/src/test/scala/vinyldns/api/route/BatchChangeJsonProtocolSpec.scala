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
import org.joda.time.DateTime
import org.json4s.Extraction._
import org.json4s.JsonDSL._
import org.json4s._
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.VinylDNSTestData
import vinyldns.api.domain._
import vinyldns.api.domain.batch.BatchTransformations.{AddChangeForValidation, ChangeForValidation}
import vinyldns.api.domain.batch.ChangeInputType._
import vinyldns.api.domain.batch.{
  AddChangeInput,
  BatchChangeInput,
  DeleteChangeInput,
  InvalidBatchChangeResponses
}
import vinyldns.core.domain.batch.SingleChangeStatus._
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record._

class BatchChangeJsonProtocolSpec
    extends WordSpec
    with Matchers
    with BatchChangeJsonProtocol
    with ValidatedValues
    with ValidatedMatchers
    with VinylDNSTestData {

  val serializers: Seq[Serializer[_]] = batchChangeSerializers

  def buildAddChangeInputJson(
      inputName: Option[String] = None,
      typ: Option[RecordType] = None,
      ttl: Option[Int] = None,
      record: Option[RecordData] = None): JObject =
    JObject(
      List(
        Some("changeType" -> decompose(Add)),
        inputName.map("inputName" -> JString(_)),
        typ.map("type" -> decompose(_)),
        ttl.map("ttl" -> JInt(_)),
        record.map("record" -> decompose(_))
      ).flatten)

  def buildDeleteChangeInputJson(
      inputName: Option[String] = None,
      typ: Option[RecordType] = None): JObject =
    JObject(
      List(
        Some("changeType" -> decompose(DeleteRecordSet)),
        inputName.map("inputName" -> JString(_)),
        typ.map("type" -> decompose(_))).flatten)

  val addAChangeInputJson: JObject =
    buildAddChangeInputJson(Some("foo."), Some(A), Some(3600), Some(AData("1.1.1.1")))

  val addAAAAChangeInputJson: JObject =
    buildAddChangeInputJson(Some("bar."), Some(AAAA), Some(1200), Some(AAAAData("1:2:3:4:5:6:7:8")))

  val addCNAMEChangeInputJson: JObject =
    buildAddChangeInputJson(Some("bizz.baz."), Some(CNAME), Some(200), Some(CNAMEData("buzz.")))

  val addPTRChangeInputJson: JObject =
    buildAddChangeInputJson(Some("4.5.6.7"), Some(PTR), Some(200), Some(PTRData("test.com.")))

  val deleteAChangeInputJson: JObject = buildDeleteChangeInputJson(Some("foo."), Some(A))

  val addChangeList: JObject = "changes" -> List(
    addAChangeInputJson,
    addAAAAChangeInputJson,
    addCNAMEChangeInputJson,
    addPTRChangeInputJson)

  val addDeleteChangeList: JObject = "changes" -> List(
    deleteAChangeInputJson,
    addAAAAChangeInputJson,
    addCNAMEChangeInputJson)

  val addBatchChangeInputWithComment: JObject = ("comments" -> Some("some comment")) ~~
    addChangeList

  val addAChangeInput = AddChangeInput("foo.", A, 3600, AData("1.1.1.1"))

  val deleteAChangeInput = DeleteChangeInput("foo.", A)

  val addAAAAChangeInput = AddChangeInput("bar.", AAAA, 1200, AAAAData("1:2:3:4:5:6:7:8"))

  val addCNAMEChangeInput = AddChangeInput("bizz.baz.", CNAME, 200, CNAMEData("buzz."))

  val addPTRChangeInput = AddChangeInput("4.5.6.7", PTR, 200, PTRData("test.com."))

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
        s"Unsupported type $UNKNOWN, valid types include: A, AAAA, CNAME, PTR, TXT, and MX")
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
        record = Some(AData("1.1.1.1")))
      val result = ChangeInputSerializer.fromJson(json)

      result should haveInvalid("Missing BatchChangeInput.changes.type")
    }

    "return an error if the ttl is not specified" in {
      val json = buildAddChangeInputJson(
        inputName = Some("foo."),
        typ = Some(A),
        record = Some(AData("1.1.1.1")))
      val result = ChangeInputSerializer.fromJson(json)

      result should haveInvalid("Missing BatchChangeInput.changes.ttl")
    }

    "return an error if the record is not specified for add" in {
      val jsonA = buildAddChangeInputJson(Some("foo."), Some(A), Some(3600))
      val resultA = ChangeInputSerializer.fromJson(jsonA)

      resultA should haveInvalid("Missing BatchChangeInput.changes.record.address")

      val jsonAAAA = buildAddChangeInputJson(Some("foo."), Some(AAAA), Some(3600))
      val resultAAAA = ChangeInputSerializer.fromJson(jsonAAAA)

      resultAAAA should haveInvalid("Missing BatchChangeInput.changes.record.address")
    }
  }

  "De-serializing BatchChangeInput from JSON" should {
    "successfully serialize valid add change data when comment is provided" in {
      val result = BatchChangeInputSerializer.fromJson(addBatchChangeInputWithComment).value

      result shouldBe BatchChangeInput(
        Some("some comment"),
        List(addAChangeInput, addAAAAChangeInput, addCNAMEChangeInput, addPTRChangeInput))
    }

    "successfully serialize valid add change data when no comment is provided" in {
      val result = BatchChangeInputSerializer.fromJson(addChangeList).value

      result shouldBe BatchChangeInput(
        None,
        List(addAChangeInput, addAAAAChangeInput, addCNAMEChangeInput, addPTRChangeInput))
    }

    "successfully serialize valid add and delete change data when no comment is provided" in {
      val result = BatchChangeInputSerializer.fromJson(addDeleteChangeList).value

      result shouldBe BatchChangeInput(
        None,
        List(deleteAChangeInput, addAAAAChangeInput, addCNAMEChangeInput))
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
      result should haveInvalid("Missing BatchChangeInput.changes.ttl")
    }
  }

  "Serializing SingleAddChange to JSON" should {
    "successfully serialize" in {
      val toJson = SingleAddChange(
        "zoneId",
        "zoneName",
        "recordName",
        "fqdn",
        A,
        30,
        AData("1.1.1.1"),
        Pending,
        Some("systemMessage"),
        None,
        None,
        "id")
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
        ("id" -> "id") ~
        ("changeType" -> "Add")
    }
  }

  "Serializing SingleDeleteChange to JSON" should {
    "successfully serialize" in {
      val toJson = SingleDeleteChange(
        "zoneId",
        "zoneName",
        "recordName",
        "fqdn",
        A,
        Pending,
        Some("systemMessage"),
        None,
        None,
        "id")
      val result = SingleDeleteChangeSerializer.toJson(toJson)

      result shouldBe ("zoneId" -> "zoneId") ~
        ("zoneName" -> "zoneName") ~
        ("recordName" -> "recordName") ~
        ("inputName" -> "fqdn") ~
        ("type" -> decompose(A)) ~
        ("status" -> decompose(Pending)) ~
        ("systemMessage" -> "systemMessage") ~
        ("recordChangeId" -> decompose(None)) ~
        ("recordSetId" -> decompose(None)) ~
        ("id" -> "id") ~
        ("changeType" -> "DeleteRecordSet")
    }
  }

  "Serializing BatchChange to JSON" should {
    "successfully serialize" in {
      val delete = SingleDeleteChange(
        "zoneId",
        "zoneName",
        "recordName",
        "fqdn",
        A,
        Pending,
        Some("systemMessage"),
        None,
        None,
        "id")
      val add = SingleAddChange(
        "zoneId",
        "zoneName",
        "recordName",
        "fqdn",
        A,
        30,
        AData("1.1.1.1"),
        Pending,
        Some("systemMessage"),
        None,
        None,
        "id")

      val time = DateTime.now
      val batchChange = BatchChange(
        "someUserId",
        "someUserName",
        Some("these be comments!"),
        time,
        List(add, delete),
        "someId")
      val result = BatchChangeSerializer.toJson(batchChange)

      result shouldBe ("userId" -> "someUserId") ~
        ("userName" -> "someUserName") ~
        ("comments" -> "these be comments!") ~
        ("createdTimestamp" -> decompose(time)) ~
        ("changes" -> decompose(List(add, delete))) ~
        ("status" -> decompose(BatchChangeStatus.Pending)) ~
        ("id" -> "someId")
    }
  }

  "Serializing BatchChangeErrorList" should {
    "serialize changes for valid inputs" in {
      val onlyValid = List(
        AddChangeForValidation(okZone, "foo", addAChangeInput).validNel,
        AddChangeForValidation(okZone, "bar", addAAAAChangeInput).validNel)
      val result = BatchChangeErrorListSerializer.toJson(
        InvalidBatchChangeResponses(List(addAChangeInput, addAAAAChangeInput), onlyValid))

      result shouldBe decompose(List(addAChangeInput, addAAAAChangeInput))
    }

    "serialize BatchChangeErrors to their corresponding messages" in {
      val onlyErrors = List(fooDiscoveryError.invalidNel, barDiscoveryError.invalidNel)
      val result = BatchChangeErrorListSerializer.toJson(
        InvalidBatchChangeResponses(List(addAChangeInput, addAAAAChangeInput), onlyErrors))

      result shouldBe decompose(
        List(
          decompose(addAChangeInput)
            .asInstanceOf[JObject] ~ ("errors" -> List(fooDiscoveryError.message)),
          decompose(addAAAAChangeInput)
            .asInstanceOf[JObject] ~ ("errors" -> List(barDiscoveryError.message))
        ))
    }

    "serializing a mix of valid inputs and BatchChangeErrors should return the appropriate success or error" in {
      val errorList =
        NonEmptyList.fromListUnsafe(List(InvalidIpv4Address("bad address"), InvalidTTL(5)))

      val validAddA = AddChangeForValidation(okZone, "foo", addAChangeInput).validNel
      val invalidAddA = errorList.invalid[ChangeForValidation]
      val invalidAddAAAA = ZoneDiscoveryError("bar.").invalidNel
      val validAddAAAA = AddChangeForValidation(okZone, "bar", addAAAAChangeInput).validNel

      val batchChangeErrorList = InvalidBatchChangeResponses(
        List(addAChangeInput, addAChangeInput, addAAAAChangeInput, addAAAAChangeInput),
        List(validAddA, invalidAddA, invalidAddAAAA, validAddAAAA)
      )

      val expected = decompose(
        List(
          addAChangeInput,
          decompose(addAChangeInput).asInstanceOf[JObject] ~
            ("errors" -> List(InvalidIpv4Address("bad address").message, InvalidTTL(5).message)),
          decompose(addAAAAChangeInput).asInstanceOf[JObject] ~
            ("errors" -> List(barDiscoveryError.message)),
          addAAAAChangeInput
        )
      )

      val result = BatchChangeErrorListSerializer.toJson(batchChangeErrorList)

      result shouldBe expected
    }
  }

  "Serializing BatchChangeError" should {
    "return the message of the BatchChange" in {
      val error = ZoneDiscoveryError("name")
      BatchChangeErrorSerializer.toJson(error).values shouldBe error.message
    }
  }
}
