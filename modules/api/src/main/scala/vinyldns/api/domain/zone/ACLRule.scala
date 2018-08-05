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

package vinyldns.api.domain.zone

import cats.data._
import cats.implicits._
import AccessLevel.AccessLevel
import vinyldns.api.domain.record.RecordType.RecordType
import vinyldns.api.domain.DomainValidations._
import vinyldns.api.domain.{DomainValidationError, zone}

object AccessLevel extends Enumeration {
  type AccessLevel = Value
  val NoAccess, Read, Write, Delete = Value
}

case class ACLRule(
    accessLevel: AccessLevel,
    description: Option[String] = None,
    userId: Option[String] = None,
    groupId: Option[String] = None,
    recordMask: Option[String] = None, // regular expression for record names
    recordTypes: Set[RecordType] = Set.empty
)

object ACLRule {
  final val DESCRIPTION_MAX = 255
  def apply(aclRuleInfo: ACLRuleInfo): ACLRule =
    zone.ACLRule(
      aclRuleInfo.accessLevel,
      aclRuleInfo.description,
      aclRuleInfo.userId,
      aclRuleInfo.groupId,
      aclRuleInfo.recordMask,
      aclRuleInfo.recordTypes)

  def build(
      accessLevel: AccessLevel,
      description: Option[String],
      userId: Option[String],
      groupId: Option[String],
      recordMask: Option[String],
      recordTypes: Set[RecordType]): ValidatedNel[DomainValidationError, ACLRule] =
    (
      accessLevel.validNel[DomainValidationError],
      validateStringLength(description, None, DESCRIPTION_MAX),
      userId.validNel[DomainValidationError],
      groupId.validNel[DomainValidationError],
      recordMask.validNel[DomainValidationError],
      validateKnownRecordTypes(recordTypes)).mapN(ACLRule.apply)
}
