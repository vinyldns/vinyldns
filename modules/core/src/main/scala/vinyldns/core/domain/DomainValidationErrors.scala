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
import vinyldns.core.domain.record.{RecordData, RecordSet, RecordType}
import vinyldns.core.domain.record.RecordType.RecordType

// $COVERAGE-OFF$
sealed abstract class DomainValidationError(val isFatal: Boolean = true) {
  def message: String
}

// The request itself is invalid in this case, so we fail fast
final case class ChangeLimitExceeded(limit: Int) extends DomainValidationError {
  def message: String = s"Cannot request more than $limit changes in a single batch change request"
}

final case class BatchChangeIsEmpty(limit: Int) extends DomainValidationError {
  def message: String =
    s"Batch change contained no changes. Batch change must have at least one change, up to a maximum of $limit changes."
}

final case class GroupDoesNotExist(id: String) extends DomainValidationError {
  def message: String = s"""Group with ID "$id" was not found"""
}

final case class NotAMemberOfOwnerGroup(ownerGroupId: String, userName: String)
    extends DomainValidationError {
  def message: String =
    s"""User "$userName" must be a member of group "$ownerGroupId" to apply this group to batch changes."""
}

final case class InvalidDomainName(param: String) extends DomainValidationError {
  def message: String =
    s"""Invalid domain name: "$param", valid domain names must be letters, numbers, underscores, and hyphens, """ +
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

final case class InvalidTTL(param: Long, min: Long, max: Long) extends DomainValidationError {
  def message: String =
    s"""Invalid TTL: "${param.toString}", must be a number between $min and $max."""
}

final case class InvalidMxPreference(param: Long, min: Long, max: Long)
    extends DomainValidationError {
  def message: String =
    s"""Invalid MX Preference: "${param.toString}", must be a number between $min and $max."""
}

final case class InvalidBatchRecordType(param: String, supported: Set[RecordType])
    extends DomainValidationError {
  def message: String =
    s"""Invalid Batch Record Type: "$param", valid record types for batch changes include $supported."""
}

final case class ZoneDiscoveryError(name: String, fatal: Boolean = false)
    extends DomainValidationError(fatal) {
  def message: String =
    s"""Zone Discovery Failed: zone for "$name" does not exist in VinylDNS. """ +
      "If zone exists, then it must be connected to in VinylDNS."
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

final case class UserIsNotAuthorizedError(
    userName: String,
    ownerGroupId: String,
    ownerType: OwnerType,
    contactEmail: Option[String] = None,
    ownerGroupName: Option[String] = None)
    extends DomainValidationError {
  def message: String =
    s"""User "$userName" is not authorized. Contact ${ownerType.toString.toLowerCase} owner group:
       |${ownerGroupName.getOrElse(ownerGroupId)} at ${contactEmail.getOrElse("")}.""".stripMargin
      .replaceAll("\n", " ")
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

final case class MissingOwnerGroupId(recordName: String, zoneName: String)
    extends DomainValidationError {
  def message: String =
    s"""Zone "$zoneName" is a shared zone, so owner group ID must be specified for record "$recordName"."""
}

final case class CnameAtZoneApexError(zoneName: String) extends DomainValidationError {
  def message: String = s"""CNAME cannot be the same name as zone "$zoneName"."""
}

final case class RecordRequiresManualReview(fqdn: String, fatal: Boolean = false)
    extends DomainValidationError(fatal) {
  def message: String =
    s"""Record set with name "$fqdn" requires manual review."""
      .replaceAll("\n", " ")
}

final case class UnsupportedOperation(operation: String) extends DomainValidationError {
  def message: String = s"$operation is not yet implemented/supported in VinylDNS."
}

final case class DeleteRecordDataDoesNotExist(inputName: String, recordData: RecordData)
    extends DomainValidationError {
  def message: String = s"""Record data $recordData does not exist for "$inputName"."""
}

// Deprecated errors
final case class ExistingMultiRecordError(fqdn: String, record: RecordSet)
    extends DomainValidationError {
  def message: String =
    s"""RecordSet with name $fqdn and type ${record.typ.toString} cannot be updated in a single Batch Change
       |because it contains multiple DNS records (${record.records.length}).""".stripMargin
      .replaceAll("\n", " ")
}

final case class NewMultiRecordError(changeName: String, changeType: RecordType)
    extends DomainValidationError {
  def message: String =
    s"""Multi-record recordsets are not enabled for this instance of VinylDNS.
       |Cannot create a new record set with multiple records for inputName $changeName and
       |type $changeType.""".stripMargin
      .replaceAll("\n", " ")
}

// deprecated in favor of more informative error message
final case class UserIsNotAuthorized(userName: String) extends DomainValidationError {
  def message: String = s"""User "$userName" is not authorized."""
}
// $COVERAGE-ON$
