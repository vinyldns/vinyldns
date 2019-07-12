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

package vinyldns.core.protobuf

import cats.syntax.all._
import vinyldns.core.domain.{DomainValidationError, ZoneDiscoveryError}
import vinyldns.core.domain.batch.{
  SingleAddChange,
  SingleChange,
  SingleChangeStatus,
  SingleDeleteChange
}
import vinyldns.core.domain.record.RecordType
import vinyldns.core.protobuf.SingleChangeType.{SingleAddType, SingleChangeType, SingleDeleteType}
import vinyldns.proto.VinylDNSProto
import scala.collection.JavaConverters._

object SingleChangeType extends Enumeration {
  type SingleChangeType = Value
  val SingleAddType, SingleDeleteType = Value

  def from(singleChange: SingleChange): SingleChangeType = singleChange match {
    case _: SingleAddChange => SingleChangeType.SingleAddType
    case _: SingleDeleteChange => SingleChangeType.SingleDeleteType
  }
}

object DomainValidationErrorCode extends Enumeration {
  type DomainValidationErrorType = Value
  // NOTE: once defined, an error code cannot be changed!
  val ZDE, Unknown = Value

  def from(error: DomainValidationError): DomainValidationErrorType = error match {
    case _: ZoneDiscoveryError => ZDE
    case _ => Unknown
  }
}

trait BatchChangeProtobufConversions extends ProtobufConversions {
  /* Currently, we only support the add change type.  When we support additional change we will add them here */
  def fromPB(
      changeType: SingleChangeType,
      errors: List[DomainValidationError],
      change: VinylDNSProto.SingleChange): Either[Throwable, SingleChange] =
    Either.catchNonFatal {
      changeType match {
        case SingleAddType =>
          val changeData = VinylDNSProto.SingleAddChange.parseFrom(change.getChangeData.getData)
          val recordType = RecordType.withName(change.getRecordType)
          val recordData = fromPB(changeData.getRecordData, recordType)
          SingleAddChange(
            if (change.hasZoneId) Some(change.getZoneId) else None,
            if (change.hasZoneName) Some(change.getZoneName) else None,
            if (change.hasRecordName) Some(change.getRecordName) else None,
            change.getInputName,
            RecordType.withName(change.getRecordType),
            changeData.getTtl,
            recordData,
            SingleChangeStatus.withName(change.getStatus),
            if (change.hasSystemMessage) Some(change.getSystemMessage) else None,
            if (change.hasRecordChangeId) Some(change.getRecordChangeId) else None,
            if (change.hasRecordSetId) Some(change.getRecordSetId) else None,
            errors,
            change.getId
          )
        case SingleDeleteType =>
          SingleDeleteChange(
            if (change.hasZoneId) Some(change.getZoneId) else None,
            if (change.hasZoneName) Some(change.getZoneName) else None,
            if (change.hasRecordName) Some(change.getRecordName) else None,
            change.getInputName,
            RecordType.withName(change.getRecordType),
            SingleChangeStatus.withName(change.getStatus),
            if (change.hasSystemMessage) Some(change.getSystemMessage) else None,
            if (change.hasRecordChangeId) Some(change.getRecordChangeId) else None,
            if (change.hasRecordSetId) Some(change.getRecordSetId) else None,
            errors,
            change.getId
          )
      }
    }

  def fromPB(change: VinylDNSProto.SingleChange): Either[Throwable, SingleChange] = {
    import cats.implicits._
    for {
      typ <- Either.catchNonFatal(SingleChangeType.withName(change.getChangeType))
      errorList <- Either.catchNonFatal(change.getValidationErrorsList.asScala.toList)
      convertedErrors <- errorList.map(fromPB).sequence
      sc <- fromPB(typ, convertedErrors, change)
    } yield sc
  }

  def fromPB(
      error: VinylDNSProto.DomainValidationError): Either[Throwable, DomainValidationError] = {
    import DomainValidationErrorCode._

    Either
      .catchNonFatal(DomainValidationErrorCode.withName(error.getErrorType))
      .flatMap { errorCode =>
        errorCode match {
          case ZDE =>
            Either.catchNonFatal {
              val errorInput =
                VinylDNSProto.DomainValidationErrorOneString.parseFrom(error.getData).getStringOne
              ZoneDiscoveryError(errorInput)
            }
          case _ =>
            // TODO
            new RuntimeException("TODO").asLeft[DomainValidationError]
        }
      }
  }

  def toPB(change: SingleAddChange): Either[Throwable, VinylDNSProto.SingleChange] =
    Either.catchNonFatal {
      val rd = toRecordData(change.recordData)
      val sad =
        VinylDNSProto.SingleAddChange.newBuilder().setTtl(change.ttl).setRecordData(rd).build()
      val scd = VinylDNSProto.SingleChangeData.newBuilder().setData(sad.toByteString)

      val sc = VinylDNSProto.SingleChange
        .newBuilder()
        .setChangeData(scd)
        .setChangeType(SingleAddType.toString)
        .setId(change.id)
        .setInputName(change.inputName)
        .setRecordType(change.typ.toString)
        .setStatus(change.status.toString)

      change.zoneId.foreach(x => sc.setZoneId(x))
      change.zoneName.foreach(x => sc.setZoneName(x))
      change.recordName.foreach(x => sc.setRecordName(x))
      change.systemMessage.foreach(x => sc.setSystemMessage(x))
      change.recordChangeId.foreach(x => sc.setRecordChangeId(x))
      change.recordSetId.foreach(x => sc.setRecordSetId(x))
      change.validationErrors.map(toDomainErrorProto).foreach(e => sc.addValidationErrors(e))

      sc.build()
    }

  def toPB(change: SingleDeleteChange): Either[Throwable, VinylDNSProto.SingleChange] =
    Either.catchNonFatal {
      val sc = VinylDNSProto.SingleChange
        .newBuilder()
        .setChangeType(SingleDeleteType.toString)
        .setId(change.id)
        .setInputName(change.inputName)
        .setRecordType(change.typ.toString)
        .setStatus(change.status.toString)

      change.zoneId.foreach(x => sc.setZoneId(x))
      change.zoneName.foreach(x => sc.setZoneName(x))
      change.recordName.foreach(x => sc.setRecordName(x))
      change.systemMessage.foreach(x => sc.setSystemMessage(x))
      change.recordChangeId.foreach(x => sc.setRecordChangeId(x))
      change.recordSetId.foreach(x => sc.setRecordSetId(x))
      change.validationErrors.map(toDomainErrorProto).foreach(e => sc.addValidationErrors(e))

      sc.build()
    }

  def toPB(change: SingleChange): Either[Throwable, VinylDNSProto.SingleChange] =
    change match {
      case sac: SingleAddChange => toPB(sac)
      case sdc: SingleDeleteChange => toPB(sdc)
    }

  def toDomainErrorProto(error: DomainValidationError): VinylDNSProto.DomainValidationError = {
    import DomainValidationErrorCode._

    def toSingleErrorPb(s: String): VinylDNSProto.DomainValidationErrorOneString =
      VinylDNSProto.DomainValidationErrorOneString.newBuilder().setStringOne(s).build()

    val builder = VinylDNSProto.DomainValidationError.newBuilder()
    error match {
      case ZoneDiscoveryError(name) =>
        builder
          .setErrorType(ZDE.toString)
          .setData(toSingleErrorPb(name).toByteString)
      case _ =>
        // TODO
        throw new RuntimeException("TODO")
    }
    builder.build()

  }
}
