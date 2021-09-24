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

import vinyldns.api.config.MessagesConfig._
import vinyldns.api.config.Messages
import vinyldns.core.Messages._

object MessagesService {

  // Converting messages obtained from configuration to Map[String, Messages] to replace the old messages using id
  val configMessage: Map[String, Messages] = messages.right.toOption match {
    case Some(x) => x.messages.map(x => x.id -> x).toMap
    case None => Map("error" -> Messages("error", "error", "error"))
  }

  // Creating default messages using Messages object in Messages.scala
  val messagesList = List(
    Messages("create-group-email-exists", "error", GroupEmailExistsErrorMsg),
    Messages("update-group-email-exists", "error", GroupEmailExistsUpdateErrorMsg),
    Messages("membership-user-not-found", "error", UserNotFoundErrorMsg),
    Messages("membership-group-not-found", "error", GroupNotFoundErrorMsg),
    Messages("membership-users-not-found", "error", UsersNotFoundErrorMsg),
    Messages("acl-rule-error", "error", ACLRuleErrorMsg),
    Messages("record-set-owner-error", "error", RecordSetOwnerErrorMsg),
    Messages("group-exists", "error", GroupAlreadyExistsErrorMsg),
    Messages("zone-admin-error", "error", ZoneAdminErrorMsg),
    Messages("record-name-filter-error", "error", RecordNameFilterErrorMsg),
    Messages("unchanged-zone-id", "error", UnchangedZoneIdErrorMsg),
    Messages("unchanged-record-type", "error", UnchangedRecordTypeErrorMsg),
    Messages("unchanged-record-name", "error", UnchangedRecordNameErrorMsg),
    Messages("owner-group-not-found", "error", RecordOwnerGroupNotFoundErrorMsg),
    Messages("user-not-in-owner-group", "error", UserNotInOwnerGroupErrorMsg),
    Messages("ns-apex-error", "error", NSApexErrorMsg),
    Messages("ns-apex-edit-error", "error", NSApexEditErrorMsg),
    Messages("ds-ttl-error", "error", DSTTLErrorMsg),
    Messages("invalid-ds", "error", DSInvalidErrorMsg),
    Messages("ds-apex-error", "error", DSApexErrorMsg),
    Messages("duplicate-cname-error", "error", CnameDuplicateErrorMsg),
    Messages("invalid-cname", "error", InvalidCnameErrorMsg),
    Messages("soa-delete-error", "error", SOADeleteErrorMsg),
    Messages("dotted-host-error", "error", DottedHostErrorMsg),
    Messages("recordset-already-exists", "error", RecordSetAlreadyExistsErrorMsg),
    Messages("recordset-cname-exists", "error", RecordSetCnameExistsErrorMsg),
    Messages("pending-update", "error", PendingUpdateErrorMsg),
    Messages("record-name-length-error", "error", RecordNameLengthErrorMsg),
    Messages("reverse-lookup-error", "error", ReverseLookupErrorMsg),
    Messages("invalid-ptr", "error", InvalidPtrErrorMsg),
    Messages("batch-change-not-found", "error", BatchChangeNotFoundErrorMsg),
    Messages("user-not-authorized", "error", UserNotAuthorizedErrorMsg),
    Messages("batch-conversion-error", "error", BatchConversionErrorMsg),
    Messages("batch-change-not-pending", "error", BatchChangeNotPendingReviewErrorMsg),
    Messages("batch-requester-not-found", "error", BatchRequesterNotFoundErrorMsg),
    Messages("scheduled-changes-disabled", "error", ScheduledChangesDisabledErrorMsg),
    Messages("time-must-be-in-future", "error", ScheduledTimeMustBeInFutureErrorMsg),
    Messages("change-not-due", "error", ScheduledChangeNotDueErrorMsg),
    Messages("require-owner-group", "error", ManualReviewRequiresOwnerGroupErrorMsg),
    Messages("missing-group-name", "validation", MissingGroupNameMsg),
    Messages("missing-group-email", "validation", MissingGroupEmailMsg),
    Messages("missing-group-members", "validation", MissingGroupMembersMsg),
    Messages("missing-group-admins", "validation", MissingGroupAdminsMsg),
    Messages("missing-group-id", "validation", MissingGroupIdMsg),
    Messages("missing-new-group", "validation", MissingNewGroupMsg),
    Messages("missing-change-type", "validation", MissingChangeTypeMsg),
    Messages("missing-user-id", "validation", MissingUserIdMsg)
  )

  // Converting default messages to Map[String, Messages], to replace if messages are changed in configuration
  val constantMessages: Map[String, Messages] = messagesList.map(m => m.id -> m).toMap

  // Replaces old default message if a new message is configured in reference.conf
  // Messages are replaced by their respective id
  val finalMessages: Map[String, Product] = constantMessages ++ configMessage

}
