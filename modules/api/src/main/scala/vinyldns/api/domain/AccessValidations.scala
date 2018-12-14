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

import vinyldns.api.Interfaces.ensuring
import vinyldns.api.domain.zone.{ACLRuleOrdering, NotAuthorizedError, PTRACLRuleOrdering, RecordSetInfo}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.record.{RecordSet, RecordType}
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.zone.AccessLevel.AccessLevel
import vinyldns.core.domain.zone._

object AccessValidations extends AccessValidationAlgebra {

  def canSeeZone(auth: AuthPrincipal, zone: Zone): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} cannot access zone '${zone.name}'"))(
      auth.canReadAll || auth.isGroupMember(zone.adminGroupId) || userHasAclRules(auth, zone))

  def canChangeZone(auth: AuthPrincipal, zone: Zone): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} cannot modify zone '${zone.name}'"))(
      auth.canEditAll || auth.isGroupMember(zone.adminGroupId))

  def canAddRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone): Either[Throwable, Unit] = {
    val accessLevel = getAccessLevel(auth, recordName, recordType, zone)
    ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} does not have access to create " +
        s"$recordName.${zone.name}"))(
      accessLevel == AccessLevel.Delete || accessLevel == AccessLevel.Write)
  }

  def canUpdateRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone): Either[Throwable, Unit] = {
    val accessLevel = getAccessLevel(auth, recordName, recordType, zone)
    ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} does not have access to update " +
        s"$recordName.${zone.name}"))(
      accessLevel == AccessLevel.Delete || accessLevel == AccessLevel.Write)
  }

  def canDeleteRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} does not have access to delete " +
        s"$recordName.${zone.name}"))(
      getAccessLevel(auth, recordName, recordType, zone) == AccessLevel.Delete)

  def canViewRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} does not have access to view " +
        s"$recordName.${zone.name}"))(
      getAccessLevel(auth, recordName, recordType, zone) != AccessLevel.NoAccess)

  def getListAccessLevels(
      auth: AuthPrincipal,
      recordSets: List[RecordSet],
      zone: Zone): List[RecordSetInfo] =
    if (auth.canEditAll || auth.isGroupMember(zone.adminGroupId))
      recordSets.map(RecordSetInfo(_, AccessLevel.Delete))
    else {
      val rulesForUser = zone.acl.rules.filter(ruleAppliesToUser(auth, _))

      def getAccessFromUserAcls(recordName: String, recordType: RecordType): AccessLevel = {
        // user filter has already been applied
        val validRules = rulesForUser.filter { rule =>
          ruleAppliesToRecordType(recordType, rule) && ruleAppliesToRecordName(
            recordName,
            recordType,
            zone,
            rule)
        }
        getPrioritizedAccessLevel(recordType, validRules)
      }

      recordSets.map { rs =>
        val aclAccessLevel = getAccessFromUserAcls(rs.name, rs.typ)
        val accessLevel = {
          if ((aclAccessLevel == AccessLevel.NoAccess) && auth.canReadAll)
            AccessLevel.Read
          else
            aclAccessLevel
        }
        RecordSetInfo(rs, accessLevel)
      }
    }

  /* Non-algebra methods */
  def getAccessFromAcl(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone): AccessLevel = {
    val validRules = zone.acl.rules.filter { rule =>
      ruleAppliesToUser(auth, rule) && ruleAppliesToRecordType(recordType, rule) && ruleAppliesToRecordName(
        recordName,
        recordType,
        zone,
        rule)
    }
    getPrioritizedAccessLevel(recordType, validRules)
  }

  def userHasAclRules(auth: AuthPrincipal, zone: Zone): Boolean =
    zone.acl.rules.exists(ruleAppliesToUser(auth, _))

  // Pull ACL rules that are relevant for the user based on userId, groups
  def ruleAppliesToUser(auth: AuthPrincipal, rule: ACLRule): Boolean =
    (rule.userId, rule.groupId) match {
      case (None, None) => true
      case (Some(userId), _) if userId == auth.userId => true
      case (_, Some(groupId)) if auth.memberGroupIds.contains(groupId) => true
      case _ => false
    }

  // Pull ACL rules that are relevant for the user based on record mask
  def ruleAppliesToRecordName(
      recordName: String,
      recordType: RecordType,
      zone: Zone,
      rule: ACLRule): Boolean =
    rule.recordMask match {
      case Some(mask) if recordType == RecordType.PTR =>
        ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, zone, recordName)
      case Some(mask) => recordName.matches(mask)
      case None => true
    }

  // Pull ACL rules that are relevant for the record based on type
  def ruleAppliesToRecordType(recordType: RecordType, rule: ACLRule): Boolean =
    rule.recordTypes.isEmpty || rule.recordTypes.contains(recordType)

  def getPrioritizedAccessLevel(recordType: RecordType, rules: Set[ACLRule]): AccessLevel =
    if (rules.isEmpty) {
      AccessLevel.NoAccess
    } else {
      implicit val ruleOrder: ACLRuleOrdering =
        if (recordType == RecordType.PTR) PTRACLRuleOrdering else ACLRuleOrdering
      val topRule = rules.toSeq.min
      topRule.accessLevel
    }

  def getAccessLevel(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone): AccessLevel = auth match {
    case superUser if superUser.canEditAll => AccessLevel.Delete
    case groupMember if groupMember.isGroupMember(zone.adminGroupId) => AccessLevel.Delete
    case supportUser if supportUser.canReadAll => {
      val aclAccess = getAccessFromAcl(auth, recordName, recordType, zone)
      if (aclAccess == AccessLevel.NoAccess) AccessLevel.Read else aclAccess
    }
    case _ => getAccessFromAcl(auth, recordName, recordType, zone)
  }
}
