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

  // Error displayed when less than two letters or numbers is filled in Record Name Filter field in RecordSetSearch page
  val RecordNameFilterError =
    "Record Name Filter field must contain at least two letters or numbers and cannot have wildcard at both the start and end."

  /*
   *  Error displayed when attempting to create group with name that already exists
   *
   * Placeholders:
   * 1. [string] group name
   * 2. [string] group email address
   */
  val GroupAlreadyExistsErrorMsg =
    "Group with name %s already exists. Please try a different name or contact %s to be added to the group."

  /*
   *  Error displayed when deleting a group being the admin of a zone
   *
   * Placeholders:
   * 1. [string] group name
   */
  val ZoneAdminError =
    "%s is the admin of a zone. Cannot delete. Please transfer the ownership to another group before deleting."

  /*
   *  Error displayed when deleting a group being the owner for a record set
   *
   * Placeholders:
   * 1. [string] group name
   * 2. [string] record set id
   */
  val RecordSetOwnerError =
    "%s is the owner for a record set including %s. Cannot delete. Please transfer the ownership to another group before deleting."

  /*
   *  Error displayed when deleting a group which has an ACL rule for a zone
   *
   * Placeholders:
   * 1. [string] group name
   * 2. [string] zone id
   */
  val ACLRuleError =
    "%s has an ACL rule for a zone including %s. Cannot delete. Please transfer the ownership to another group before deleting."

  // Error displayed when NSData field is not a positive integer
  val NSDataError = "NS data must be a positive integer"

  /*
   *  Error displayed when user is not authorized to make changes to the record
   *
   * Placeholders:
   * 1. [string] user name
   * 2. [string] owner type
   * 3. [string] owner group name | owner group id
   * 4. [string] contact email
   */
  val NotAuthorizedErrorMsg =
    "User \"%s\" is not authorized. Contact %s owner group: %s at %s to make DNS changes."

  // Error displayed when group name or email is empty
  val GroupValidationErrorMsg = "Group name and email cannot be empty."

  val EmailValidationErrorMsg = "Please enter a valid Email. Valid domains should end with"

  val InvalidEmailValidationErrorMsg = "Please enter a valid Email."

  val DotsValidationErrorMsg = "Please enter a valid Email. Number of dots allowed after @ is"

  val nonExistentRecordDeleteMessage = "This record does not exist. No further action is required."

  val nonExistentRecordDataDeleteMessage = "Record data entered does not exist. No further action is required."
}
