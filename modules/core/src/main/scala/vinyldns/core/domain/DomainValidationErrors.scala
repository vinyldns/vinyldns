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

package vinyldns.core.domain

import vinyldns.core.domain.batch.OwnerType.OwnerType
import vinyldns.core.domain.record.{RecordData, RecordType}
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.Messages._

// $COVERAGE-OFF$
sealed abstract class DomainValidationError(val isFatal: Boolean = true) {
  def message: String
}

// The request itself is invalid in this case, so we fail fast
final case class ChangeLimitExceeded(limit: Int) extends DomainValidationError {
  def message: String = ChangeLimitExceededErrorMsg.format(limit)
}

final case class BatchChangeIsEmpty(limit: Int) extends DomainValidationError {
  def message: String = BatchChangeIsEmptyErrorMsg.format(limit)
}

final case class GroupDoesNotExist(id: String) extends DomainValidationError {
  def message: String = GroupIdNotFoundErrorMsg.format(id)
}

final case class NotAMemberOfOwnerGroup(ownerGroupId: String, userName: String)
    extends DomainValidationError {
  def message: String = NotMemberOfGroupErrorMsg.format(userName, ownerGroupId)
}

final case class InvalidDomainName(param: String) extends DomainValidationError {
  def message: String = InvalidDomainNameErrorMsg.format(param)
}

final case class InvalidIPv4CName(param: String) extends DomainValidationError {
  def message: String = InvalidIPv4CNameErrorMsg.format(param)
}

final case class InvalidCname(param: String, isReverseZone: Boolean) extends DomainValidationError {
  def message: String =
    isReverseZone match {
      case true => InvalidForwardCnameErrorMsg.format(param)
      case false => InvalidReverseCnameErrorMsg.format(param)
    }
}

final case class InvalidLength(param: String, minLengthInclusive: Int, maxLengthInclusive: Int)
    extends DomainValidationError {
  def message: String = InvalidLengthErrorMsg.format(param, minLengthInclusive, maxLengthInclusive)
}

final case class InvalidEmail(param: String) extends DomainValidationError {
  def message: String = InvalidEmailErrorMsg.format(param)
}

final case class InvalidRecordType(param: String) extends DomainValidationError {
  def message: String = InvalidRecordTypeErrorMsg.format(param, RecordType.values)
}

final case class InvalidPortNumber(param: String, minPort: Int, maxPort: Int)
    extends DomainValidationError {
  def message: String = InvalidPortNumberErrorMsg.format(param, minPort, maxPort)
}

final case class InvalidIpv4Address(param: String) extends DomainValidationError {
  def message: String = InvalidIpv4AddressErrorMsg.format(param)
}

final case class InvalidIpv6Address(param: String) extends DomainValidationError {
  def message: String = InvalidIpv6AddressErrorMsg.format(param)
}

final case class InvalidIPAddress(param: String) extends DomainValidationError {
  def message: String = InvalidIPAddressErrorMsg.format(param)
}

final case class InvalidTTL(param: Long, min: Long, max: Long) extends DomainValidationError {
  def message: String = InvalidTTLErrorMsg.format(param.toString, min, max)
}

final case class InvalidMX_NAPTR_SRVData(param: Long, min: Long, max: Long, recordDataType: String, recordType: String)
    extends DomainValidationError {
  def message: String = InvalidMX_NAPTR_SRVDataErrorMsg.format(recordType, recordDataType, param.toString, min, max)
}

final case class InvalidNaptrFlag(value: String)
  extends DomainValidationError {
  def message: String = InvalidNaptrFlagErrorMsg.format(value)
}

final case class InvalidNaptrRegexp(value: String)
  extends DomainValidationError {
  def message: String = InvalidNaptrRegexpErrorMsg.format(value)
}

final case class InvalidBatchRecordType(param: String, supported: Set[RecordType])
    extends DomainValidationError {
  def message: String = InvalidBatchRecordTypeErrorMsg.format(param, supported)
}

final case class ZoneDiscoveryError(name: String, fatal: Boolean = false)
    extends DomainValidationError(fatal) {
  def message: String = ZoneDiscoveryErrorMsg.format(name)
}

final case class RecordAlreadyExists(name: String) extends DomainValidationError {
  def message: String = RecordAlreadyExistsErrorMsg.format(name)
}

final case class RecordDoesNotExist(name: String) extends DomainValidationError {
  def message: String = RecordDoesNotExistErrorMsg.format(name)
}

final case class InvalidUpdateRequest(name: String) extends DomainValidationError {
  def message: String = InvalidUpdateRequestErrorMsg.format(name)
}

final case class CnameIsNotUniqueError(name: String, typ: RecordType)
    extends DomainValidationError {
  def message: String = CnameIsNotUniqueErrorMsg.format(name, typ)
}

final case class UserIsNotAuthorizedError(
    userName: String,
    ownerGroupId: String,
    ownerType: OwnerType,
    contactEmail: Option[String] = None,
    ownerGroupName: Option[String] = None
) extends DomainValidationError {
  def message: String =
    NotAuthorizedErrorMsg.format(
      userName,
      ownerType.toString.toLowerCase,
      ownerGroupName.getOrElse(ownerGroupId),
      contactEmail.getOrElse("")
    )
}

final case class RecordNameNotUniqueInBatch(name: String, typ: RecordType)
    extends DomainValidationError {
  def message: String = RecordNameNotUniqueInBatchErrorMsg.format(name, typ)
}

final case class RecordInReverseZoneError(name: String, typ: String) extends DomainValidationError {
  def message: String = RecordInReverseZoneErrorMsg.format(name, typ)
}

final case class HighValueDomainError(name: String) extends DomainValidationError {
  def message: String = HighValueDomainErrorMsg.format(name)
}

final case class MissingOwnerGroupId(recordName: String, zoneName: String)
    extends DomainValidationError {
  def message: String = MissingOwnerGroupIdErrorMsg.format(zoneName, recordName)
}

final case class CnameAtZoneApexError(zoneName: String) extends DomainValidationError {
  def message: String = CnameAtZoneApexErrorMsg.format(zoneName)
}

final case class RecordRequiresManualReview(fqdn: String, fatal: Boolean = false)
    extends DomainValidationError(fatal) {
  def message: String = RecordManualReviewErrorMsg.format(fqdn).replaceAll("\n", " ")
}

final case class InvalidBatchRequest(msg: String) extends DomainValidationError {
  def message: String = InvalidBatchRequestErrorMsg.format(msg.replaceAll("\n", " "))
}

final case class NotApprovedNSError(nsData: String) extends DomainValidationError {
  def message: String = NotApprovedNSErrorMsg.format(nsData)
}

final case class UnsupportedOperation(operation: String) extends DomainValidationError {
  def message: String = UnsupportedOperationErrorMsg.format(operation)
}

final case class DeleteRecordDataDoesNotExist(inputName: String, recordData: RecordData)
    extends DomainValidationError {
  def message: String = DeleteRecordDataDoesNotExistErrorMsg.format(recordData, inputName)
}
// $COVERAGE-ON$
