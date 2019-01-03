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

package vinyldns.api.domain

import vinyldns.api.domain.batch.SupportedBatchChangeRecordTypes
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.record.RecordType.RecordType

// $COVERAGE-OFF$
sealed trait DomainValidationError {
  def message: String
}

final case class InvalidDomainName(param: String) extends DomainValidationError {
  def message: String =
    s"""Invalid domain name: "$param", valid domain names must be letters, numbers, and hyphens, """ +
      "joined by dots, and terminated with a dot."
}

final case class InvalidLength(param: String, minLengthInclusive: Int, maxLengthInclusive: Int)
    extends DomainValidationError {
  def message: String =
    s"""Invalid length: "$param", length needs to be between $minLengthInclusive and $maxLengthInclusive characters."""
}

final case class InvalidEmail(param: String) extends DomainValidationError {
  def message: String = s"""Invalid email address: "$param"."""
}

final case class InvalidRecordType(param: String) extends DomainValidationError {
  def message: String =
    s"""Invalid record type: "$param", valid record types include ${RecordType.values}."""
}

final case class InvalidPortNumber(param: String, minPort: Int, maxPort: Int)
    extends DomainValidationError {
  def message: String =
    s"""Invalid port number: "$param", port must be a number between $minPort and $maxPort."""
}

final case class InvalidIpv4Address(param: String) extends DomainValidationError {
  def message: String = s"""Invalid IPv4 address: "$param"."""
}

final case class InvalidIpv6Address(param: String) extends DomainValidationError {
  def message: String = s"""Invalid IPv6 address: "$param"."""
}

final case class InvalidIPAddress(param: String) extends DomainValidationError {
  def message: String = s"""Invalid IP address: "$param"."""
}

final case class InvalidTTL(param: Long) extends DomainValidationError {
  def message: String =
    s"""Invalid TTL: "${param.toString}", must be a number between """ +
      s"${DomainValidations.TTL_MIN_LENGTH} and ${DomainValidations.TTL_MAX_LENGTH}."
}

final case class InvalidMxPreference(param: Long) extends DomainValidationError {
  def message: String =
    s"""Invalid MX Preference: "${param.toString}", must be a number between """ +
      s"${DomainValidations.MX_PREFERENCE_MIN_VALUE} and ${DomainValidations.MX_PREFERENCE_MAX_VALUE}."
}

final case class InvalidBatchRecordType(param: String) extends DomainValidationError {
  def message: String =
    s"""Invalid Batch Record Type: "$param", valid record types for batch changes include """ +
      s"${SupportedBatchChangeRecordTypes.get}."
}

final case class ZoneDiscoveryError(name: String) extends DomainValidationError {
  def message: String =
    s"""Zone Discovery Failed: zone for "$name" does not exist in VinylDNS. """ +
      "If zone exists, then it must be created in VinylDNS."
}

final case class RecordAlreadyExists(name: String) extends DomainValidationError {
  def message: String =
    s"""Record "$name" Already Exists: cannot add an existing record; to update it, """ +
      "issue a DeleteRecordSet then an Add."
}

final case class RecordDoesNotExist(name: String) extends DomainValidationError {
  def message: String =
    s"""Record "$name" Does Not Exist: cannot delete a record that does not exist."""
}

final case class CnameIsNotUniqueError(name: String, typ: RecordType)
    extends DomainValidationError {
  def message: String =
    "CNAME Conflict: CNAME record names must be unique. " +
      s"""Existing record with name "$name" and type "$typ" conflicts with this record."""
}

final case class UserIsNotAuthorized(user: String) extends DomainValidationError {
  def message: String = s"""User "$user" is not authorized."""
}

final case class RecordNameNotUniqueInBatch(name: String, typ: RecordType)
    extends DomainValidationError {
  def message: String =
    s"""Record Name "$name" Not Unique In Batch Change: cannot have multiple "$typ" records with the same name."""
}

final case class RecordInReverseZoneError(name: String, typ: String) extends DomainValidationError {
  def message: String =
    "Invalid Record Type In Reverse Zone: record with name " +
      s""""$name" and type "$typ" is not allowed in a reverse zone."""
}

final case class HighValueDomainError(name: String) extends DomainValidationError {
  def message: String =
    s"""Record name "$name" is configured as a High Value Domain, so it cannot be modified."""
}

final case class OwnerGroupIdMissing(recordName: String, zoneName: String)
    extends DomainValidationError {
  def message: String =
    s"""Zone "$zoneName" is a shared zone, so owner group ID must be specified for record
       | "$recordName".""".stripMargin
}
// $COVERAGE-ON$
