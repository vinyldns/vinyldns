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

object Messages {

  // When less than two letters or numbers is filled in Record Name Filter field in RecordSetSearch page
  val RecordNameFilterError = "Record Name Filter field must contain at least two letters or numbers to perform a RecordSet Search."

  // When creating group with name that already exists
  // s"Group with name $name already exists. Please try a different name or contact ${existingGroup.email} to be added to the group."
  val GroupAlreadyExistsError = s"Group with name {TestGroup} already exists. Please try a different name or contact {test@test.com} to be added to the group."

  // When deleting a group being the admin of a zone
  // s"${group.name} is the admin of a zone. Cannot delete. Please transfer the ownership to another group before deleting."
  val ZoneAdminError = s"{TestGroup} is the admin of a zone. Cannot delete. Please transfer the ownership to another group before deleting."

  // When deleting a group being the owner for a record set
  // s"${group.name} is the owner for a record set including $rsId. Cannot delete. Please transfer the ownership to another group before deleting.
  val RecordSetOwnerError = s"{TestGroup} is the owner for a record set including {RS_ID}. Cannot delete. Please transfer the ownership to another group before deleting."

  // When deleting a group which has an ACL rule for a zone
  // s"${group.name} has an ACL rule for a zone including $zId. Cannot delete. Please transfer the ownership to another group before deleting."
  val ACLRuleError = s"{TestGroup} has an ACL rule for a zone including {Z_ID}. Cannot delete. Please transfer the ownership to another group before deleting."

  // When NSData field is not a positive integer
  val NSDataError = "NS data must be a positive integer"

  // When importing files other than .csv
  val ImportError = "Import failed. Not a valid file. File should be of ‘.csv’ type."

  // When user is not authorized to make changes to the record
  // s"""User "$userName" is not authorized. Contact ${ownerType.toString.toLowerCase} owner group:
  //       |${ownerGroupName.getOrElse(ownerGroupId)} at ${contactEmail.getOrElse("")} to make DNS changes.
  //       |You must be a part of the owner group to make DNS changes.""".stripMargin .replaceAll("\n", " ")
  val NotAuthorizedError = s"""User {"dummy"} is not authorized. Contact {zone} owner group: {ok-group} at 
  {test@test.com} to make DNS changes. You must be a part of the owner group to make DNS changes."""

}
