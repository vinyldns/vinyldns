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

package vinyldns.core

import vinyldns.core.config.MessagesConfig._
import vinyldns.core.config.Message
import org.slf4j.LoggerFactory

object Messages {

  private val logger = LoggerFactory.getLogger(this.getClass)

  // Regex to extract format specifiers from message strings
  private val FormatSpecifier =
    """%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])""".r

  // Extract conversion types from a message string to validate override compatibility
  private def conversions(s: String): List[String] =
    FormatSpecifier.findAllMatchIn(s).map(_.group(1)).filterNot(_ == "%").toList

  // Getting the messages present in the config file
  val configMessages: Map[String, Message] = messages.fold(
    failures => {
      logger.error(s"Failed to load messages config, overrides will not be applied: $failures")
      Map("error" -> Message("config-not-found", None))
    },
    value => value.messages.map(value => value.text -> value).toMap
  )

  // Extract the override logic for testability
  private[core] def applyOverride(
      messagesMap: Map[String, Message],
      existingMessage: String
  ): String =
    messagesMap
      .get(existingMessage)
      .flatMap(_.overrideText)
      .filter(_.nonEmpty)
      .filter { candidate =>
        val matches = conversions(candidate) == conversions(existingMessage)
        if (!matches)
          logger.warn(
            s"Ignoring message override, placeholders differ: [$existingMessage] -> [$candidate]"
          )
        matches
      }
      .getOrElse(existingMessage)

  // Checking if a message is present in config file and overriding the existing message
  implicit class MessagesStringExtension(existingMessage: String) {
    def orConfig: String = applyOverride(configMessages, existingMessage)
  }

  // Messages displayed to the user and the files in which they are present
  /* MembershipService.scala */
  // Placeholders: %s = group name, %s = email address, %s = contact
  val GroupEmailExistsErrorMsg: String = "Cannot create group. A group, %s, is already associated with the email address %s. Please contact %s to be added to the group.".orConfig

  // Placeholders: %s = group name, %s = email address
  val GroupEmailExistsUpdateErrorMsg: String = "Cannot update group. A group, %s, is already associated with the email address %s. Visit FAQ for more information.".orConfig

  // Placeholders: %s = user id
  val UserNotFoundErrorMsg: String = "User with ID %s was not found".orConfig

  // Placeholders: %s = group id
  val GroupNotFoundErrorMsg: String = "Group with ID %s was not found".orConfig

  // Placeholders: %s = user ids
  val UsersNotFoundErrorMsg: String = "Users [ %s ] were not found".orConfig

  // Placeholders: %s = user name
  val UserIsNotFoundErrorMsg: String = "User %s was not found".orConfig

  // Placeholders: %s = group name, %s = zone/record name
  val ACLRuleErrorMsg: String = "%s has an ACL rule for a zone including %s. Cannot delete. Please transfer the ownership to another group before deleting.".orConfig

  // Placeholders: %s = group name, %s = record set name
  val RecordSetOwnerErrorMsg: String = "%s is the owner for a record set including %s. Cannot delete. Please transfer the ownership to another group before deleting.".orConfig

  // Placeholders: %s = group name, %s = contact
  val GroupAlreadyExistsErrorMsg: String = "Group with name %s already exists. Please try a different name or contact %s to be added to the group.".orConfig

  // Placeholders: %s = group name
  val ZoneAdminErrorMsg: String = "%s is the admin of a zone. Cannot delete. Please transfer the ownership to another group before deleting.".orConfig

  val GroupValidationErrorMsg: String = "Group name and email cannot be empty.".orConfig

  val EmailValidationErrorMsg: String = "Please enter a valid Email. Valid domains should end with".orConfig

  val InvalidEmailValidationErrorMsg: String = "Please enter a valid Email.".orConfig

  val DotsValidationErrorMsg: String = "Please enter a valid Email. Number of dots allowed after @ is".orConfig

  /* DomainValidationErrors.scala */
  // Placeholders: %d = limit
  val ChangeLimitExceededErrorMsg: String = "Cannot request more than %d changes in a single batch change request".orConfig

  // Placeholders: %s = group id
  val GroupIdNotFoundErrorMsg: String = "Group with ID \"%s\" was not found".orConfig

  // Placeholders: %d = max count
  val BatchChangeIsEmptyErrorMsg: String = "Batch change contained no changes. Batch change must have at least one change, up to a maximum of %d changes.".orConfig

  // Placeholders: %s = user name, %s = group name
  val NotMemberOfGroupErrorMsg: String = "User \"%s\" must be a member of group \"%s\" to apply this group to batch changes.".orConfig

  // Placeholders: %s = domain name
  val InvalidDomainNameErrorMsg: String = "Invalid domain name: \"%s\", valid domain names must be letters, numbers, underscores, and hyphens, joined by dots, and terminated with a dot.".orConfig

  // Placeholders: %s = value, %d = min length, %d = max length
  val InvalidLengthErrorMsg: String = "Invalid length: \"%s\", length needs to be between %d and %d characters.".orConfig

  // Placeholders: %s = email address
  val InvalidEmailErrorMsg: String = "Invalid email address: \"%s\".".orConfig

  // Placeholders: %s = record type, %s = valid types list
  val InvalidRecordTypeErrorMsg: String = "Invalid record type: \"%s\", valid record types include %s.".orConfig

  // Placeholders: %s = port number, %d = min port, %d = max port
  val InvalidPortNumberErrorMsg: String = "Invalid port number: \"%s\", port must be a number between %d and %d.".orConfig

  // Placeholders: %s = IPv4 address
  val InvalidIpv4AddressErrorMsg: String = "Invalid IPv4 address: \"%s\".".orConfig

  // Placeholders: %s = IPv6 address
  val InvalidIpv6AddressErrorMsg: String = "Invalid IPv6 address: \"%s\".".orConfig

  // Placeholders: %s = IP address
  val InvalidIPAddressErrorMsg: String = "Invalid IP address: \"%s\".".orConfig

  // Placeholders: %s = TTL value, %d = min TTL, %d = max TTL
  val InvalidTTLErrorMsg: String = "Invalid TTL: \"%s\", must be a number between %d and %d.".orConfig

  // Placeholders: %s = MX preference, %d = min preference, %d = max preference
  val InvalidMxPreferenceErrorMsg: String = "Invalid MX Preference: \"%s\", must be a number between %d and %d.".orConfig

  // Placeholders: %s = record type, %s = field name, %s = value, %d = min, %d = max
  val InvalidMX_NAPTR_SRVDataErrorMsg: String = "Invalid %s %s: \"%s\", must be a number between %d and %d.".orConfig

  // Placeholders: %s = NAPTR flag value
  val InvalidNaptrFlagErrorMsg: String = "Invalid NAPTR flag value: '%s'. Valid NAPTR flag value must be U, S, A or P.".orConfig

  // Placeholders: %s = NAPTR regexp value
  val InvalidNaptrRegexpErrorMsg: String = "Invalid NAPTR regexp value: '%s'. Valid NAPTR regexp value must start and end with '!'.".orConfig

  // Placeholders: %s = error message
  val InvalidBatchRequestErrorMsg: String = "%s".orConfig

  // Placeholders: %s = name server
  val NotApprovedNSErrorMsg: String = "Name Server %s is not an approved name server.".orConfig

  // Placeholders: %s = record type, %s = valid types list
  val InvalidBatchRecordTypeErrorMsg: String = "Invalid Batch Record Type: \"%s\", valid record types for batch changes include %s.".orConfig

  // Placeholders: %s = zone name
  val ZoneDiscoveryErrorMsg: String = "Zone Discovery Failed: zone for \"%s\" does not exist in VinylDNS. If zone exists, then it must be connected to in VinylDNS.".orConfig

  // Placeholders: %s = record name
  val RecordAlreadyExistsErrorMsg: String = "RecordName \"%s\" already exists. If you intended to update this record, submit a DeleteRecordSet entry followed by an Add.".orConfig

  // Placeholders: %s = record name
  val RecordDoesNotExistErrorMsg: String = "Record \"%s\" Does Not Exist: cannot delete a record that does not exist.".orConfig

  // Placeholders: %s = record name, %s = record type
  val CnameIsNotUniqueErrorMsg: String = "CNAME Conflict: CNAME record names must be unique. Existing record with name \"%s\" and type \"%s\" conflicts with this record.".orConfig

  // Placeholders: %s = record name, %s = record type
  val RecordNameNotUniqueInBatchErrorMsg: String = "Record Name \"%s\" Not Unique In Batch Change: cannot have multiple \"%s\" records with the same name.".orConfig

  // Placeholders: %s = record name, %s = record type
  val RecordInReverseZoneErrorMsg: String = "Invalid Record Type In Reverse Zone: record with name \"%s\" and type \"%s\" is not allowed in a reverse zone.".orConfig

  // Placeholders: %s = record name
  val HighValueDomainErrorMsg: String = "Record name \"%s\" is configured as a High Value Domain, so it cannot be modified.".orConfig

  // Placeholders: %s = zone name, %s = record name
  val MissingOwnerGroupIdErrorMsg: String = "Zone \"%s\" is a shared zone, so owner group ID must be specified for record \"%s\".".orConfig

  // Placeholders: %s = zone name
  val CnameAtZoneApexErrorMsg: String = "CNAME cannot be the same name as zone \"%s\".".orConfig

  // Placeholders: %s = record set name
  val RecordManualReviewErrorMsg: String = "Record set with name \"%s\" requires manual review.".orConfig

  // Placeholders: %s = feature name
  val UnsupportedOperationErrorMsg: String = "%s is not yet implemented/supported in VinylDNS.".orConfig

  // Placeholders: %s = record data, %s = record name
  val DeleteRecordDataDoesNotExistErrorMsg: String = "Record data %s does not exist for \"%s\".".orConfig

  // Placeholders: %s = record name, %s = group name, %s = group id, %s = guidance message
  val NotAuthorizedErrorMsg: String = "The record \"%s\" is owned by the %s group (id: %s). %s".orConfig

  // Placeholders: %s = contact email
  val NotAuthorizedAddContactMsg: String = "Only members of this group may add records to this zone. Please contact them for assistance: %s.".orConfig

  val NotAuthorizedAddDefaultMsg: String = "Only members of this group may add records to this zone.".orConfig

  // Placeholders: %s = contact email
  val NotAuthorizedUpdateContactMsg: String = "Only members of this group may update this record. Please contact them for assistance: %s.".orConfig

  val NotAuthorizedUpdateDefaultMsg: String = "Only members of this group may update this record.".orConfig

  // Placeholders: %s = contact email
  val NotAuthorizedDeleteContactMsg: String = "Only members of this group may delete this record. Please contact them for assistance: %s.".orConfig

  val NotAuthorizedDeleteDefaultMsg: String = "Only members of this group may delete this record.".orConfig

  // Placeholders: %s = CNAME value
  val InvalidIPv4CNameErrorMsg: String = "Invalid Cname: \"%s\", Valid CNAME record data should not be an IP address".orConfig

  // Placeholders: %s = CNAME value
  val InvalidForwardCnameErrorMsg: String = "Invalid Cname: \"%s\", valid cnames must be letters, numbers, slashes, underscores, and hyphens, joined by dots, and terminated with a dot.".orConfig

  // Placeholders: %s = CNAME value
  val InvalidReverseCnameErrorMsg: String = "Invalid Cname: \"%s\", valid cnames must be letters, numbers, underscores, and hyphens, joined by dots, and terminated with a dot.".orConfig

  // Placeholders: %s = record name
  val InvalidUpdateRequestErrorMsg: String = "Cannot perform request for the record \"%s\". Add and Delete for the record with same record data exists in the batch.".orConfig

  /* Deprecated errors */
  // Placeholders: %s = record name, %s = record type, %s = number of records
  val ExistingMultiRecordErrorMsg: String = "RecordSet with name %s and type %s cannot be updated in a single Batch Change because it contains multiple DNS records (%s).".orConfig

  // Placeholders: %s = record name, %s = record type
  val NewMultiRecordErrorMsg: String = "Multi-record recordsets are not enabled for this instance of VinylDNS. Cannot create a new record set with multiple records for inputName %s and type %s.".orConfig

  // Placeholders: %s = user name
  val UserIsNotAuthorizedMsg: String = "User \"%s\" is not authorized.".orConfig

  /* RecordSetValidations.scala */
  val RecordNameFilterErrorMsg: String = "Record Name Filter field must contain at least two letters or numbers and cannot have wildcard at both the start and end.".orConfig

  val UnchangedZoneIdErrorMsg: String = "Cannot update RecordSet's zone ID.".orConfig

  val UnchangedRecordTypeErrorMsg: String = "Cannot update RecordSet's record type.".orConfig

  val UnchangedRecordNameErrorMsg: String = "Cannot update RecordSet's name.".orConfig

  // Placeholders: %s = group id
  val RecordOwnerGroupNotFoundErrorMsg: String = "Record owner group with id \"%s\" not found".orConfig

  // Placeholders: %s = group id
  val UserNotInOwnerGroupErrorMsg: String = "User not in record owner group with id \"%s\"".orConfig

  // Placeholders: %s = record name
  val NSApexErrorMsg: String = "Record with name %s is an NS record at apex and cannot be added".orConfig

  // Placeholders: %s = record name
  val NSApexEditErrorMsg: String = "Record with name %s is an NS record at apex and cannot be edited".orConfig

  // Placeholders: %s = record name, %d = NS TTL
  val DSTTLErrorMsg: String = "DS record [%s] must have TTL matching its linked NS (%d)".orConfig

  // Placeholders: %s = record name, %s = zone name
  val DSInvalidErrorMsg: String = "DS record [%s] is invalid because there is no NS record with that name in the zone [%s]".orConfig

  // Placeholders: %s = record name
  val DSApexErrorMsg: String = "Record with name [%s] is an DS record at apex and cannot be added".orConfig

  // Placeholders: %s = record name, %s = zone name
  val CnameDuplicateErrorMsg: String = "RecordSet with name %s already exists in zone %s, CNAME record cannot use duplicate name".orConfig

  val InvalidCnameErrorMsg: String = "CNAME RecordSet cannot have name '@' because it points to zone origin".orConfig

  val SOADeleteErrorMsg: String = "SOA records cannot be deleted".orConfig

  // Placeholders: %s = record name, %s = record type, %s = zone name
  val DottedHostErrorMsg: String = "Record with name %s and type %s is a dotted host which is not allowed in zone %s".orConfig

  // Placeholders: %s = record name, %s = record type, %s = zone name
  val RecordSetAlreadyExistsErrorMsg: String = "RecordSet with name %s and type %s already exists in zone %s".orConfig

  // Placeholders: %s = record name, %s = zone name
  val RecordSetCnameExistsErrorMsg: String = "RecordSet with name %s and type CNAME already exists in zone %s".orConfig

  // Placeholders: %s = record set id, %s = record name, %s = record type
  val PendingUpdateErrorMsg: String = "RecordSet with id %s, name %s and type %s currently has a pending change".orConfig

  // Placeholders: %s = record name
  val RecordNameLengthErrorMsg: String = "record set name %s is too long".orConfig

  // Placeholders: %s = hostname
  val ReverseLookupErrorMsg: String = "%s is not valid in reverse lookup zone.".orConfig

  val InvalidPtrErrorMsg: String = "PTR is not valid in forward lookup zone".orConfig

  // Placeholders: %s = FQDN, %s = zone name
  val InvalidRequestErrorMsg: String = ("Record with fqdn '%s.%s' cannot be created. " +
    "Please check if a record with the same FQDN and type already exist and make the change there.").orConfig

  // Placeholders: %s = zone name
  val RtypeOrUserNotAllowedErrorMsg: String = "Record type is not allowed or the user is not authorized to create a dotted host in the zone '%s'".orConfig

  // Placeholders: %s = record data
  val RDataWithConsecutiveDotsErrorMsg: String = "RecordSet Data cannot contain consecutive 'dot' character. RData: '%s'".orConfig

  // Placeholders: %s = CNAME value
  val IPv4inCnameErrorMsg: String = "Invalid CNAME: %s, valid CNAME record data cannot be an IP address.".orConfig

  // Placeholders: %s = record name, %s = dots limit
  val MoreDotsThanAllowedErrorMsg: String = ("RecordSet with name %s has more dots than that is allowed in config for this zone " +
    "which is, 'dots-limit = %s'.").orConfig

  val InvalidEndingErrorMsg: String = "RecordSet name cannot end with a dot, unless it's an apex record.".orConfig

  val OwnershipTransferUpdateErrorMsg: String = "Cannot update RecordSet's if user not a member of ownership group. User can only request for ownership transfer".orConfig

  val OwnershipTransferCancelledRequestErrorMsg: String = "Cannot update RecordSet Ownership Status when request is cancelled.".orConfig

  val OwnershipTransferZoneNotSharedErrorMsg: String = "Cannot update RecordSet Ownership Status when zone is not shared.".orConfig

  // Placeholders: %s = group id
  val OwnershipTransferAlreadyOwnedErrorMsg: String = "Record owner group with id %s already owns the record, new request is not needed.".orConfig

  // Placeholders: %s = group id (old request status)
  val OwnershipTransferInvalidPendingReviewErrorMsg: String = "Invalid Ownership transfer status: %s".orConfig

  // Placeholders: %s = action being performed
  val OwnershipTransferInvalidApprovalErrorMsg: String = "Unable to %s the Ownership transfer status for the record: None".orConfig

  // Placeholders: %s = current status
  val OwnershipTransferInvalidCancelErrorMsg: String = "Unable to cancel the Ownership transfer. Current status: %s".orConfig

  val OwnershipTransferUnauthorisedCancelErrorMsg: String = "Unauthorised to Cancel the ownership transfer".orConfig

  // Placeholders: %s = new status
  val OwnershipTransferUnauthorizedChangeErrorMsg: String = "Unauthorized to change ownership transfer status to '%s'".orConfig



  /* BatchChangeErrors.scala */
  // Placeholders: %s = batch change id
  val BatchChangeNotFoundErrorMsg: String = "Batch change with id %s cannot be found".orConfig

  // Placeholders: %s = item id
  val UserNotAuthorizedErrorMsg: String = "User does not have access to item %s".orConfig

  // Placeholders: %s = record name, %s = record type
  val BatchConversionErrorMsg: String = "Batch conversion for processing failed to convert change with name \"%s\" and type \"%s\"".orConfig

  // Placeholders: %s = batch change id
  val BatchChangeNotPendingReviewErrorMsg: String = "Batch change %s is not pending review, so it cannot be rejected.".orConfig

  // Placeholders: %s = user id, %s = user name
  val BatchRequesterNotFoundErrorMsg: String = "The requesting user with id %s and name %s cannot be found in VinylDNS".orConfig

  val ScheduledChangesDisabledErrorMsg: String = "Cannot create a scheduled change, as it is currently disabled on this VinylDNS instance.".orConfig

  val ScheduledTimeMustBeInFutureErrorMsg: String = "Scheduled time must be in the future.".orConfig

  // Placeholders: %s = scheduled date time
  val ScheduledChangeNotDueErrorMsg: String = "Cannot process scheduled change as it is not past the scheduled date of %s".orConfig

  val ManualReviewRequiresOwnerGroupErrorMsg: String = "Batch change requires owner group for manual review.".orConfig

  /* DnsJsonProtocol.scala */
  val MissingRecordSetZoneMsg: String = "Missing RecordSetChange.zone".orConfig

  val MissingRecordSetMsg: String = "Missing RecordSetChange.recordSet".orConfig

  val MissingRecordSetUserIdMsg: String = "Missing RecordSetChange.userId".orConfig

  val MissingRecordSetChangeTypeMsg: String = "Missing RecordSetChange.changeType".orConfig

  val MissingZoneNameMsg: String = "Missing Zone.name".orConfig

  val MissingZoneEmailMsg: String = "Missing Zone.email".orConfig

  val MissingZoneGroupIdMsg: String = "Missing Zone.adminGroupId".orConfig

  val MissingZoneIdMsg: String = "Missing Zone.id".orConfig

  val MissingZoneSharedMsg: String = "Missing Zone.shared".orConfig

  val UnsupportedKeyAlgorithmMsg: String = "Unsupported type for key algorithm, must be a string".orConfig

  val MissingZoneConnectionNameMsg: String = "Missing ZoneConnection.name".orConfig

  val MissingZoneConnectionKeyNameMsg: String = "Missing ZoneConnection.keyName".orConfig

  val MissingZoneConnectionKeyMsg: String = "Missing ZoneConnection.key".orConfig

  val MissingZoneConnectionServer: String = "Missing ZoneConnection.primaryServer".orConfig

  val MissingRecordSetTypeMsg: String = "Missing RecordSet.type".orConfig

  val MissingRecordSetZoneIdMsg: String = "Missing RecordSet.zoneId".orConfig

  val MissingRecordSetNameMsg: String = "Missing RecordSet.name".orConfig

  val MissingRecordSetTTL: String = "Missing RecordSet.ttl".orConfig

  val RecordNameLengthMsg: String = "Record name must not exceed 255 characters".orConfig

  val RecordContainsSpaceMsg: String = "Record name cannot contain spaces".orConfig

  val RecordSetTTLNotPositiveMsg: String = "RecordSet.ttl must be a positive signed 32 bit number".orConfig

  val RecordSetTTLNotValidMsg: String = "RecordSet.ttl must be a positive signed 32 bit number greater than or equal to 30".orConfig

  val CnameValidationMsg: String = "CNAME record sets cannot contain multiple records".orConfig

  val MissingARecordsMsg: String = "Missing A Records".orConfig

  val MissingAAAARecordsMsg: String = "Missing AAAA Records".orConfig

  val MissingCnameRecordsMsg: String = "Missing CNAME Records".orConfig

  val MissingDSRecordsMsg: String = "Missing DS Records".orConfig

  val MissingMXRecordsMsg: String = "Missing MX Records".orConfig

  val MissingNsRecordsMsg: String = "Missing NS Records".orConfig

  val MissingPTRRecordsMsg: String = "Missing PTR Records".orConfig

  val MissingSOARecordsMsg: String = "Missing SOA Records".orConfig

  val MissingSPFRecordsMsg: String = "Missing SPF Records".orConfig

  val MissingSRVRecordsMsg: String = "Missing SRV Records".orConfig

  val MissingNAPTRRecordsMsg: String = "Missing NAPTR Records".orConfig

  val MissingSSHFPRecordsMsg: String = "Missing SSHFP Records".orConfig

  val MissingTXTRecordsMsg: String = "Missing TXT Records".orConfig

  // Placeholders: %s = record type, %s = valid types list
  val UnsupportedRecordTypeMsg: String = "Unsupported type %s, valid types include %s".orConfig

  val MissingAAddressMsg: String = "Missing A.address".orConfig

  val InvalidIPv4Msg: String = "A must be a valid IPv4 Address".orConfig

  val MissingAAAAAddressMsg: String = "Missing AAAA.address".orConfig

  val InvalidIPv6Msg: String = "AAAA must be a valid IPv6 Address".orConfig

  val MissingCnameMsg: String = "Missing CNAME.cname".orConfig

  val CnameLengthMsg: String = "CNAME domain name must not exceed 255 characters".orConfig

  val CnameAbsoluteMsg: String = "CNAME data must be absolute".orConfig

  val MissingMXPreferenceMsg: String = "Missing MX.preference".orConfig

  val MXPreferenceValidationMsg: String = "MX.preference must be a 16 bit integer".orConfig

  val MissingMXExchangeMsg: String = "Missing MX.exchange".orConfig

  val MXExchangeValidationMsg: String = "MX.exchange must be less than 255 characters".orConfig

  val MissingNSNameMsg: String = "Missing NS.nsdname".orConfig

  val NSNameValidationMsg: String = "NS must be less than 255 characters".orConfig

  val MissingPTRNameMsg: String = "Missing PTR.ptrdname".orConfig

  val PTRNameValidationMsg: String = "PTR must be less than 255 characters".orConfig

  val MissingSOAMNameMsg: String = "Missing SOA.mname".orConfig

  val SOAMNameValidationMsg: String = "SOA.mname must be less than 255 characters".orConfig

  val MissingSOARNameMsg: String = "Missing SOA.rname".orConfig

  val SOARNameValidationMsg: String = "SOA.rname must be less than 255 characters".orConfig

  val MissingSOASerialMsg: String = "Missing SOA.serial".orConfig

  val SOASerialValidationMsg: String = "SOA.serial must be an unsigned 32 bit number".orConfig

  val MissingSOARefreshMsg: String = "Missing SOA.refresh".orConfig

  val SOARefreshValidationMsg: String = "SOA.refresh must be an unsigned 32 bit number".orConfig

  val MissingSOARetryMsg: String = "Missing SOA.retry".orConfig

  val SOARetryValidationMsg: String = "SOA.retry must be an unsigned 32 bit number".orConfig

  val MissingSOAExpireMsg: String = "Missing SOA.expire".orConfig

  val SOAExpireValidationMsg: String = "SOA.expire must be an unsigned 32 bit number".orConfig

  val MissingSOAMinimumMsg: String = "Missing SOA.minimum".orConfig

  val SOAMinimumValidationMsg: String = "SOA.minimum must be an unsigned 32 bit number".orConfig

  val MissingSPFTextMsg: String = "Missing SPF.text".orConfig

  val SPFValidationMsg: String = "SPF record must be less than 64764 characters".orConfig

  val MissingSRVPriorityMsg: String = "Missing SRV.priority".orConfig

  val SRVPriorityValidationMsg: String = "SRV.priority must be an unsigned 16 bit number".orConfig

  val MissingSRVWeightMsg: String = "Missing SRV.weight".orConfig

  val SRVWeightValidationMsg: String = "SRV.weight must be an unsigned 16 bit number".orConfig

  val MissingSRVPortMsg: String = "Missing SRV.port".orConfig

  val SRVPortValidationMsg: String = "SRV.port must be an unsigned 16 bit number".orConfig

  val MissingSRVTargetMsg: String = "Missing SRV.target".orConfig

  val SRVTargetValidationMsg: String = "SRV.target must be less than 255 characters".orConfig

  val MissingNAPTROrderMsg: String = "Missing NAPTR.order".orConfig

  val NAPTROrderValidationMsg: String = "NAPTR.order must be an unsigned 16 bit number".orConfig

  val MissingNAPTRPreferenceMsg: String = "Missing NAPTR.preference".orConfig

  val NAPTRPreferenceValidationMsg: String = "NAPTR.preference must be an unsigned 16 bit number".orConfig

  val MissingNAPTRFlagsMsg: String = "Missing NAPTR.flags".orConfig

  val NAPTRFlagsValidationMsg: String = "Invalid NAPTR.flag. Valid NAPTR flag value must be U, S, A or P".orConfig

  val MissingNAPTRServiceMsg: String = "Missing NAPTR.service".orConfig

  val NAPTRServiceValidationMsg: String = "NAPTR.service must be less than 255 characters".orConfig

  val MissingNAPTRRegexMsg: String = "Missing NAPTR.regexp".orConfig

  val NAPTRRegexValidationMsg: String = "Invalid NAPTR.regexp. Valid NAPTR regexp value must start and end with '!' or can be empty".orConfig

  val MissingNAPTRReplacementMsg: String = "Missing NAPTR.replacement".orConfig

  val NAPTRReplacementValidationMsg: String = "NAPTR.replacement must be less than 255 characters".orConfig

  val MissingSSHFPAlgorithmMsg: String = "Missing SSHFP.algorithm".orConfig

  val SSHFPAlgorithmValidationMsg: String = "SSHFP.algorithm must be an unsigned 8 bit number".orConfig

  val MissingSSHFPTypeMsg: String = "Missing SSHFP.type".orConfig

  val SSHFPTypeValidationMsg: String = "SSHFP.type must be an unsigned 8 bit number".orConfig

  val MissingSSHFPFingerprintMsg: String = "Missing SSHFP.fingerprint".orConfig

  val MissingDSKeytagMsg: String = "Missing DS.keytag".orConfig

  val DSKeytagValidationMsg: String = "DS.keytag must be an unsigned 16 bit number".orConfig

  val MissingDSAlgorithmMsg: String = "Missing DS.algorithm".orConfig

  // Placeholders: %d = algorithm number
  val UnsupportedDNSSECMsg: String = "Algorithm %d is not a supported DNSSEC algorithm".orConfig

  val MissingDSDigestTypeMsg: String = "Missing DS.digesttype".orConfig

  // Placeholders: %d = digest type number
  val UnsupportedDigestTypeMsg: String = "Digest Type %d is not a supported DS record digest type".orConfig

  val MissingDSDigestMsg: String = "Missing DS.digest".orConfig

  val DigestConvertMsg: String = "Could not convert digest to valid hex".orConfig

  val MissingTXTTextMsg: String = "Missing TXT.text".orConfig

  val TXTRecordValidationMsg: String = "TXT record must be less than 64764 characters".orConfig

  val NSDataErrorMsg: String = "NS data must be a positive integer".orConfig

  val UnsupportedEncryptedTypeErrorMsg: String = "Unsupported type for zone connection key, must be a string".orConfig

  /* AccessValidations.scala */
  // Placeholders: %s = user name, %s = zone name
  val CannotAccessZoneErrorMsg: String = "User %s cannot access zone '%s'".orConfig

  // Placeholders: %s = user name, %s = zone name
  val CannotAccessZoneChangeErrorMsg: String = "User %s cannot access zone '%s' changes".orConfig

  // Placeholders: %s = user name, %s = zone name, %s = admin group name
  val CannotChangeZoneErrorMsg: String = "User '%s' cannot create or modify zone '%s' because they are not in the Zone Admin Group '%s'".orConfig

  // Placeholders: %s = user name, %s = zone name, %s = record name
  val CannotAddRecordErrorMsg: String = "User %s does not have access to create %s.%s".orConfig

  // Placeholders: %s = user name, %s = zone name, %s = record name
  val CannotUpdateRecordErrorMsg: String = "User %s does not have access to update %s.%s".orConfig

  // Placeholders: %s = user name, %s = zone name, %s = record name
  val CannotDeleteRecordErrorMsg: String = "User %s does not have access to delete %s.%s".orConfig

  // Placeholders: %s = user name, %s = zone name, %s = record name
  val CannotViewRecordErrorMsg: String = "User %s does not have access to view %s.%s".orConfig

  /* MembershipValidations.scala */
  val hasNoMembersAndAdminsErrorMsg: String = "Group must have at least one member and one admin".orConfig

  val NoAuthorizationErrorMsg: String = "Not authorized".orConfig

  val InvalidGroupChangeIdErrorMsg: String = "Invalid Group Change ID".orConfig

  /* ACLJsonProtocol.scala */
  val ACLAccessLevelMsg: String = "Missing ACLRule.accessLevel".orConfig

  val DeserializeCheckErrorMsg: String = "Cannot specify both a userId and a groupId".orConfig

  /* MembershipJsonProtocol.scala */
  val MissingGroupNameMsg: String = "Missing Group.name".orConfig

  val MissingGroupEmailMsg: String = "Missing Group.email".orConfig

  val MissingGroupMembersMsg: String = "Missing Group.members".orConfig

  val MissingGroupAdminsMsg: String = "Missing Group.admins".orConfig

  val MissingGroupIdMsg: String = "Missing Group.id".orConfig

  val MissingNewGroupMsg: String = "Missing new group".orConfig

  val MissingChangeTypeMsg: String = "Missing change type".orConfig

  val MissingUserIdMsg: String = "Missing userId".orConfig

  val MissingUserNameMsg: String = "Missing userName".orConfig

  val MissingGroupChangeMsg: String = "Missing groupChangeMessage".orConfig

  /* VinylDNSAuthentication.scala */
  val AuthMissingErrorMsg: String = "Authorization header not found".orConfig

  val AuthRejectedErrorMsg: String = "Authorization header could not be parsed".orConfig

  val RequestSignatureErrorMsg: String = "Request signature could not be validated".orConfig

  // Placeholders: %s = username
  val AccountLockedErrorMsg: String = "Account with username %s is locked".orConfig

  // Placeholders: %s = access key
  val AccountAccessKeyErrorMsg: String = "Account with accessKey %s specified was not found".orConfig

  /* RecordSetChangeHandler.scala */
  val OutOfSyncFailureMessage: String = "This record set is out of sync with the DNS backend; sync this zone before attempting to update this record set.".orConfig

  val IncompatibleRecordFailureMessage: String = "Incompatible record in DNS.".orConfig

  val SyncZoneMessage: String = "This record set is out of sync with the DNS backend. Sync this zone before attempting to update this record set.".orConfig

  val RecordConflictMessage: String = "Conflict due to the record having the same name as an NS record in the same zone. Please create the record using the DNS service the NS record has been delegated to (ex. AWS r53), or use a different record name.".orConfig

  // Placeholders: %s = change name, %s = record name, %s = validation error message
  val UpdateValidateMsg: String = "Failed validating update to DNS for change \"%s\": \"%s\": %s".orConfig

  // Placeholders: %s = change id, %s = record name, %s = apply error message
  val UpdateApplyMsg: String = "Failed applying update to DNS for change %s:%s: %s".orConfig

  // Placeholders: %s = change id, %s = record name, %s = verification error message
  val UpdateVerifyMsg: String = "Failed verifying update to DNS for change %s:%s: %s".orConfig

  /* ZoneRecordValidations.scala */
  // Placeholders: %s = name server
  val ApprovedNameServerMsg: String = "Name Server %s is not an approved name server.".orConfig

  /* ZoneService.scala */
  val UnknownGroupNameMsg: String = "Unknown group name".orConfig

  // Placeholders: %s = zone name, %s = contact
  val ZoneAlreadyExistsErrorMsg: String = "Zone with name %s already exists. Please contact %s to request access to the zone.".orConfig

  // Placeholders: %s = zone id
  val ZoneIdNotFoundErrorMsg: String = "Zone with id %s does not exists".orConfig

  // Placeholders: %s = zone name
  val ZoneNameNotFoundErrorMsg: String = "Zone with name %s does not exists".orConfig

  // Placeholders: %s = admin group id
  val AdminGroupNotExistsErrorMsg: String = "Admin group with ID %s does not exist".orConfig

  val InvalidCronStringErrorMsg: String = "Invalid cron expression. Please enter a valid cron expression in 'recurrenceSchedule'.".orConfig

  // Placeholders: %s = user name
  val UnauthorizedSyncScheduleErrorMsg: String = "User '%s' is not authorized to schedule zone sync in this zone.".orConfig

  /* ZoneValidations.scala */
  // Placeholders: %s = zone name
  val RecentSyncErrorMsg: String = "Zone %s was recently synced. Cannot complete sync".orConfig

  val InvalidACLRuleErrorMsg: String = "Invalid ACL rule: ACL rules must have a group or user id".orConfig

  val CIDRErrorMsg: String = "PTR types must have no mask or a valid CIDR mask: Invalid CIDR block".orConfig

  val PTRNoMaskErrorMsg: String = "Multiple record types including PTR must have no mask".orConfig

  // Placeholders: %s = regex pattern
  val InvalidRegexErrorMsg: String = "record mask %s is an invalid regex".orConfig

  val CreateSharedZoneErrorMsg: String = "Not authorized to create shared zones.".orConfig

  // Placeholders: %b = old value, %b = new value
  val UpdateSharedZoneErrorMsg: String = "Not authorized to update zone shared status from %b to %b.".orConfig

  /* JsonValidation.scala */
  val UnexpectedExtractionErrorMsg: String = "Extraction.extract returned unexpected type".orConfig

  // Placeholders: %s = JSON element name
  val JsonParseErrorMsg: String = "Failed to parse %s".orConfig

  // Placeholders: %s = element being parsed, %s = error message
  val UnexpectedJsonErrorMsg: String = "While parsing %s, received unexpected error '%s'".orConfig

  // Placeholders: %s = entity type
  val InvalidMsg: String = "Invalid %s".orConfig

  /* Route53Backend.scala */
  // Placeholders: %s = zone name
  val HostedZoneErrorMsg: String = "Unable to find hosted zone for zone name %s".orConfig

  // Placeholders: %s = record name
  val RecordConversionErrorMsg: String = "Unable to convert record set to route 53 format for %s".orConfig

  /* SqsMessage.scala */
  // Placeholders: %s = SQS message id
  val SqsParseErrorMsg: String = "Unable to parse SQS Message with ID '%s'".orConfig

  // Placeholders: %s = SQS message id
  val SqsBodyErrorMsg: String = "No message body found for SQS message with ID '%s'".orConfig

  val SqsInvalidCommand: String = "Invalid command message type '%s'".orConfig

  /* SqsMessageQueue.scala */
  // Placeholders: %d = duration value, %d = min duration, %d = max duration
  val InvalidDurationErrorMsg: String = "Invalid duration: %d seconds. Duration must be between %d-%d seconds.".orConfig

  /* SqsMessageQueueProvider.scala */
  val InvalidQueueNameErrorMsg: String = ("Invalid queue name: %s. Must be 1-80 alphanumeric, hyphen or underscore characters. " +
    "FIFO queues (queue names ending in \".fifo\") are not supported.").orConfig

  val nonExistentRecordDeleteMessage = "This record does not exist. No further action is required."

  val nonExistentRecordDataDeleteMessage = "Record data entered does not exist. No further action is required."

}
