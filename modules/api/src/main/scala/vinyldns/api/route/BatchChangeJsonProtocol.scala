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

import cats.data._
import cats.data.Validated._
import org.json4s.JsonDSL._
import org.json4s._
import cats.implicits._
import vinyldns.core.domain.DomainValidationError
import vinyldns.api.domain.batch.ChangeInputType._
import vinyldns.api.domain.batch._
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record._

trait BatchChangeJsonProtocol extends JsonValidation {

  val batchChangeSerializers = Seq(
    JsonEnumV(ChangeInputType),
    JsonEnumV(SingleChangeStatus),
    JsonEnumV(BatchChangeApprovalStatus),
    BatchChangeInputSerializer,
    ChangeInputSerializer,
    AddChangeInputSerializer,
    DeleteChangeInputSerializer,
    SingleAddChangeSerializer,
    SingleDeleteChangeSerializer,
    BatchChangeSerializer,
    BatchChangeErrorListSerializer,
    BatchChangeErrorSerializer,
    RejectBatchChangeInputSerializer,
    ApproveBatchChangeInputSerializer
  )

  final val MAX_COMMENT_LENGTH: Int = 1024

  case object BatchChangeInputSerializer extends ValidationSerializer[BatchChangeInput] {
    override def fromJson(js: JValue): ValidatedNel[String, BatchChangeInput] = {
      val changeList =
        (js \ "changes").required[List[ChangeInput]]("Missing BatchChangeInput.changes")

      (
        (js \ "comments").optional[String],
        changeList,
        (js \ "ownerGroupId").optional[String]
      ).mapN(BatchChangeInput(_, _, _))
    }
  }

  case object ChangeInputSerializer extends ValidationSerializer[ChangeInput] {
    override def fromJson(js: JValue): ValidatedNel[String, ChangeInput] = {
      val changeType =
        (js \ "changeType").required(ChangeInputType, "Missing BatchChangeInput.changes.changeType")

      changeType.andThen {
        case Add => js.required[AddChangeInput]("Invalid AddChangeInput json")
        case DeleteRecordSet => js.required[DeleteChangeInput]("Invalid DeleteChangeInput json")
      }
    }
  }

  case object AddChangeInputSerializer extends ValidationSerializer[AddChangeInput] {
    override def fromJson(js: JValue): ValidatedNel[String, AddChangeInput] = {
      val recordType = (js \ "type").required(RecordType, "Missing BatchChangeInput.changes.type")

      (
        (js \ "inputName").required[String]("Missing BatchChangeInput.changes.inputName"),
        recordType,
        (js \ "ttl").optional[Long],
        recordType.andThen(extractRecord(_, js \ "record"))).mapN(AddChangeInput.apply)
    }

    override def toJson(aci: AddChangeInput): JValue =
      ("changeType" -> Extraction.decompose(Add)) ~
        ("inputName" -> aci.inputName) ~
        ("type" -> Extraction.decompose(aci.typ)) ~
        ("ttl" -> aci.ttl) ~
        ("record" -> Extraction.decompose(aci.record))
  }

  case object DeleteChangeInputSerializer extends ValidationSerializer[DeleteChangeInput] {
    override def fromJson(js: JValue): ValidatedNel[String, DeleteChangeInput] = {
      val recordType = (js \ "type").required(RecordType, "Missing BatchChangeInput.changes.type")

      (
        (js \ "inputName").required[String]("Missing BatchChangeInput.changes.inputName"),
        recordType).mapN(DeleteChangeInput.apply)
    }

    override def toJson(aci: DeleteChangeInput): JValue =
      ("changeType" -> Extraction.decompose(DeleteRecordSet)) ~
        ("inputName" -> aci.inputName) ~
        ("type" -> Extraction.decompose(aci.typ))
  }

  // recordName, zoneName, zoneId used to be required; getOrElse to maintain backwards compatability with clients
  case object SingleAddChangeSerializer extends ValidationSerializer[SingleAddChange] {
    override def toJson(sac: SingleAddChange): JValue =
      ("changeType" -> "Add") ~
        ("inputName" -> sac.inputName) ~
        ("type" -> Extraction.decompose(sac.typ)) ~
        ("ttl" -> sac.ttl) ~
        ("record" -> Extraction.decompose(sac.recordData)) ~
        ("status" -> sac.status.toString) ~
        ("recordName" -> sac.recordName.getOrElse("")) ~
        ("zoneName" -> sac.zoneName.getOrElse("")) ~
        ("zoneId" -> sac.zoneId.getOrElse("")) ~
        ("systemMessage" -> sac.systemMessage) ~
        ("recordChangeId" -> sac.recordChangeId) ~
        ("recordSetId" -> sac.recordSetId) ~
        ("id" -> sac.id)
  }

  // recordName, zoneName, zoneId used to be required; getOrElse to maintain backwards compatability with clients
  case object SingleDeleteChangeSerializer extends ValidationSerializer[SingleDeleteChange] {
    override def toJson(sac: SingleDeleteChange): JValue =
      ("changeType" -> "DeleteRecordSet") ~
        ("inputName" -> sac.inputName) ~
        ("type" -> Extraction.decompose(sac.typ)) ~
        ("status" -> sac.status.toString) ~
        ("recordName" -> sac.recordName.getOrElse("")) ~
        ("zoneName" -> sac.zoneName.getOrElse("")) ~
        ("zoneId" -> sac.zoneId.getOrElse("")) ~
        ("systemMessage" -> sac.systemMessage) ~
        ("recordChangeId" -> sac.recordChangeId) ~
        ("recordSetId" -> sac.recordSetId) ~
        ("id" -> sac.id)
  }

  case object BatchChangeSerializer extends ValidationSerializer[BatchChange] {
    override def toJson(bc: BatchChange): JValue =
      ("userId" -> bc.userId) ~
        ("userName" -> bc.userName) ~
        ("comments" -> bc.comments) ~
        ("createdTimestamp" -> Extraction.decompose(bc.createdTimestamp)) ~
        ("changes" -> Extraction.decompose(bc.changes)) ~
        ("status" -> bc.status.toString) ~
        ("id" -> bc.id) ~
        ("ownerGroupId" -> bc.ownerGroupId)
  }

  case object BatchChangeInfoSerializer extends ValidationSerializer[BatchChangeInfo] {
    override def toJson(bc: BatchChangeInfo): JValue =
      ("userId" -> bc.userId) ~
        ("userName" -> bc.userName) ~
        ("comments" -> bc.comments) ~
        ("createdTimestamp" -> Extraction.decompose(bc.createdTimestamp)) ~
        ("changes" -> Extraction.decompose(bc.changes)) ~
        ("status" -> bc.status.toString) ~
        ("id" -> bc.id) ~
        ("ownerGroupId" -> bc.ownerGroupId) ~
        ("ownerGroupName" -> bc.ownerGroupName)
  }

  case object BatchChangeErrorListSerializer
      extends ValidationSerializer[InvalidBatchChangeResponses] {
    override def toJson(crl: InvalidBatchChangeResponses): JValue =
      crl.changeRequestResponses.zip(crl.changeRequests).map {
        case (Valid(_), changeInput) => Extraction.decompose(changeInput)
        case (Invalid(batchChangeError), changeInput) =>
          val change = Extraction.decompose(changeInput)
          val errors: JValue = "errors" -> Extraction.decompose(batchChangeError.toList)

          change.merge(errors)
      }
  }

  case object BatchChangeErrorSerializer extends ValidationSerializer[DomainValidationError] {
    override def toJson(dve: DomainValidationError): JValue = dve.message
  }

  def checkCommentLength(comments: Option[String]): Boolean =
    comments.forall(_.length <= MAX_COMMENT_LENGTH)

  case object RejectBatchChangeInputSerializer
      extends ValidationSerializer[RejectBatchChangeInput] {
    override def fromJson(js: JValue): ValidatedNel[String, RejectBatchChangeInput] =
      (js \ "reviewComment")
        .optional[String]
        .check(
          s"Comment length must not exceed $MAX_COMMENT_LENGTH characters." -> checkCommentLength)
        .map(RejectBatchChangeInput)
  }

  case object ApproveBatchChangeInputSerializer
      extends ValidationSerializer[ApproveBatchChangeInput] {
    override def fromJson(js: JValue): ValidatedNel[String, ApproveBatchChangeInput] =
      (js \ "reviewComment")
        .optional[String]
        .check(
          s"Comment length must not exceed $MAX_COMMENT_LENGTH characters." -> checkCommentLength)
        .map(ApproveBatchChangeInput)
  }

  def extractRecord(typ: RecordType, js: JValue): ValidatedNel[String, RecordData] = {
    import RecordType._
    typ match {
      case A => js.required[AData]("Missing BatchChangeInput.changes.record.address")
      case AAAA => js.required[AAAAData]("Missing BatchChangeInput.changes.record.address")
      case CNAME => js.required[CNAMEData]("Missing BatchChangeInput.changes.record.cname")
      case PTR => js.required[PTRData]("Missing BatchChangeInput.changes.record.ptrdname")
      case TXT => js.required[TXTData]("Missing BatchChangeInput.changes.record.text")
      case MX =>
        js.required[MXData](
          "Missing BatchChangeInput.changes.record.preference and BatchChangeInput.changes.record.exchange")
      case _ =>
        s"Unsupported type $typ, valid types include: A, AAAA, CNAME, PTR, TXT, and MX".invalidNel
    }
  }
}
