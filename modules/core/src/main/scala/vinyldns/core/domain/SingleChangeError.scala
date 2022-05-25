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

import vinyldns.core.domain.DomainValidationErrorType.DomainValidationErrorType

case class SingleChangeError(errorType: DomainValidationErrorType, message: String)

object SingleChangeError {
  def apply(error: DomainValidationError): SingleChangeError =
    new SingleChangeError(DomainValidationErrorType.from(error), error.message)
}

// format: off
object DomainValidationErrorType extends Enumeration {
  type DomainValidationErrorType = Value
  // NOTE: once defined, an error code type cannot be changed!
  val ChangeLimitExceeded, BatchChangeIsEmpty, GroupDoesNotExist, NotAMemberOfOwnerGroup,
  InvalidDomainName, InvalidCname, InvalidLength, InvalidEmail, InvalidRecordType, InvalidPortNumber,
  InvalidIpv4Address, InvalidIpv6Address, InvalidIPAddress, InvalidTTL, InvalidMxPreference,
  InvalidBatchRecordType, ZoneDiscoveryError, RecordAlreadyExists, RecordDoesNotExist,
  CnameIsNotUniqueError, UserIsNotAuthorized, UserIsNotAuthorizedError, RecordNameNotUniqueInBatch,
  RecordInReverseZoneError, HighValueDomainError, MissingOwnerGroupId, ExistingMultiRecordError,
  NewMultiRecordError, CnameAtZoneApexError, RecordRequiresManualReview, UnsupportedOperation,
  DeleteRecordDataDoesNotExist = Value

  // $COVERAGE-OFF$
  def from(error: DomainValidationError): DomainValidationErrorType =
    error match {
      case _: ChangeLimitExceeded => ChangeLimitExceeded
      case _: BatchChangeIsEmpty => BatchChangeIsEmpty
      case _: GroupDoesNotExist => GroupDoesNotExist
      case _: NotAMemberOfOwnerGroup => NotAMemberOfOwnerGroup
      case _: InvalidDomainName => InvalidDomainName
      case _: InvalidCname => InvalidCname
      case _: InvalidLength => InvalidLength
      case _: InvalidEmail => InvalidEmail
      case _: InvalidRecordType => InvalidRecordType
      case _: InvalidPortNumber => InvalidPortNumber
      case _: InvalidIpv4Address => InvalidIpv4Address
      case _: InvalidIpv6Address => InvalidIpv6Address
      case _: InvalidIPAddress => InvalidIPAddress
      case _: InvalidTTL => InvalidTTL
      case _: InvalidMxPreference => InvalidMxPreference
      case _: InvalidBatchRecordType => InvalidBatchRecordType
      case _: ZoneDiscoveryError => ZoneDiscoveryError
      case _: RecordAlreadyExists => RecordAlreadyExists
      case _: RecordDoesNotExist => RecordDoesNotExist
      case _: CnameIsNotUniqueError => CnameIsNotUniqueError
      case _: UserIsNotAuthorized => UserIsNotAuthorized
      case _: UserIsNotAuthorizedError => UserIsNotAuthorizedError
      case _: RecordNameNotUniqueInBatch => RecordNameNotUniqueInBatch
      case _: RecordInReverseZoneError => RecordInReverseZoneError
      case _: HighValueDomainError => HighValueDomainError
      case _: MissingOwnerGroupId => MissingOwnerGroupId
      case _: ExistingMultiRecordError => ExistingMultiRecordError
      case _: NewMultiRecordError => NewMultiRecordError
      case _: CnameAtZoneApexError => CnameAtZoneApexError
      case _: RecordRequiresManualReview => RecordRequiresManualReview
      case _: UnsupportedOperation => UnsupportedOperation
      case _: DeleteRecordDataDoesNotExist => DeleteRecordDataDoesNotExist
    }
  // $COVERAGE-ON$
}
// format: on
