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

package vinyldns.api.domain.access

import vinyldns.api.Interfaces.ensuring
import vinyldns.api.domain.ReverseZoneHelpers
import vinyldns.api.domain.zone._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.record.{RecordData, RecordType}
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.zone.AccessLevel.AccessLevel
import vinyldns.core.domain.zone.{ACLRule, AccessLevel, Zone}

import scala.collection.mutable
import scala.concurrent.duration._

class AccessValidations(
    globalAcls: GlobalAcls = GlobalAcls(List.empty),
    sharedApprovedTypes: List[RecordType.Value] = Nil
) extends AccessValidationsAlgebra {

  private final val RegexBudgetNanos = 1.second.toNanos

  private final class RegexBudget {
    val deadlineNanos: Long = System.nanoTime() + RegexBudgetNanos
    val timedOutRules: mutable.Set[ACLRule] = mutable.Set.empty
  }

  def canSeeZone(auth: AuthPrincipal, zone: Zone): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} cannot access zone '${zone.name}'")
    )(
      auth.isSystemAdmin || zone.shared || auth
        .isGroupMember(zone.adminGroupId) || userHasAclRules(auth, zone)
    )

  def canSeeZoneChange(auth: AuthPrincipal, zone: Zone): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} cannot access zone '${zone.name}' changes")
    )(
      auth.isSystemAdmin || zone.shared || auth.isGroupMember(zone.adminGroupId)
    )

  def canChangeZone(
      auth: AuthPrincipal,
      zoneName: String,
      zoneAdminGroupId: String
  ): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(
        s"""User '${auth.signedInUser.userName}' cannot create or modify zone '$zoneName' because
           |they are not in the Zone Admin Group '$zoneAdminGroupId'""".stripMargin
          .replace("\n", " ")
      )
    )(auth.isSuper || auth.isGroupMember(zoneAdminGroupId))

  def canAddRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone,
      recordData: List[RecordData] = List.empty
  ): Either[Throwable, Unit] = {
    val accessLevel = getAccessLevel(auth, recordName, recordType, zone, None, recordData)
    ensuring(
      NotAuthorizedError(
        s"User ${auth.signedInUser.userName} does not have access to create " +
          s"$recordName.${zone.name}"
      )
    )(accessLevel == AccessLevel.Delete || accessLevel == AccessLevel.Write)
  }

  def canUpdateRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone,
      recordOwnerGroupId: Option[String],
      superUserCanUpdateOwnerGroup: Boolean = false,
      newRecordData: List[RecordData] = List.empty
  ): Either[Throwable, Unit] = {
    val accessLevel = {
      getAccessLevel(auth, recordName, recordType, zone, recordOwnerGroupId, newRecordData)
    }
    ensuring(
      NotAuthorizedError(
        s"User ${auth.signedInUser.userName} does not have access to update " +
          s"$recordName.${zone.name}"
      )
    )(accessLevel == AccessLevel.Delete || accessLevel == AccessLevel.Write || superUserCanUpdateOwnerGroup)
  }

  def canDeleteRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone,
      recordOwnerGroupId: Option[String],
      existingRecordData: List[RecordData] = List.empty
  ): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(
        s"User ${auth.signedInUser.userName} does not have access to delete " +
          s"$recordName.${zone.name}"
      )
    )(
      getAccessLevel(auth, recordName, recordType, zone, recordOwnerGroupId, existingRecordData) == AccessLevel.Delete
    )

  def canViewRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone,
      recordOwnerGroupId: Option[String],
      recordData: List[RecordData] = List.empty
  ): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(
        s"User ${auth.signedInUser.userName} does not have access to view " +
          s"$recordName.${zone.name}"
      )
    )(
      getAccessLevel(auth, recordName, recordType, zone, recordOwnerGroupId, recordData) != AccessLevel.NoAccess
    )

  def getListAccessLevels(
      auth: AuthPrincipal,
      recordSets: List[RecordSetInfo],
      zone: Zone
  ): List[RecordSetListInfo] =
    if (auth.isSuper || auth.isGroupMember(zone.adminGroupId))
      recordSets.map(RecordSetListInfo(_, AccessLevel.Delete))
    else {
      val budget = new RegexBudget
      
      recordSets.map { rs =>
        val aclAccessLevel = getAccessFromAcl(auth, rs.name, rs.typ, zone, budget)
        val accessLevel = {
          if ((aclAccessLevel == AccessLevel.NoAccess) && auth.isSystemAdmin)
            AccessLevel.Read
          else if (zone.shared && rs.ownerGroupId.forall(auth.isGroupMember))
            AccessLevel.Delete
          else
            aclAccessLevel
        }
        RecordSetListInfo(rs, accessLevel)
      }
    }

  def getZoneAccess(auth: AuthPrincipal, zone: Zone): AccessLevel =
    if (canChangeZone(auth, zone.name, zone.adminGroupId).isRight)
      AccessLevel.Delete
    else if (canSeeZone(auth, zone).isRight)
      AccessLevel.Read
    else AccessLevel.NoAccess

  /* Non-algebra methods */
  def getAccessFromAcl(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone
  ): AccessLevel =
    getAccessFromAcl(auth, recordName, recordType, zone, new RegexBudget)

  private def getAccessFromAcl(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone,
      budget: RegexBudget
  ): AccessLevel = {
    val validRules = zone.acl.rules.toSeq
      .filter(rule => ruleAppliesToUser(auth, rule) && ruleAppliesToRecordType(recordType, rule))
      .flatMap { rule =>
        matchRecordName(recordName, recordType, zone, rule, budget) match {
          case RegexMatchResult.Matched(true) => Some(rule)
          case RegexMatchResult.Matched(false) => None
          case RegexMatchResult.TimedOut => Some(rule.copy(accessLevel = AccessLevel.NoAccess))
        }
      }
      .toSet
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
      rule: ACLRule
  ): Boolean =
    matchRecordName(recordName, recordType, zone, rule, new RegexBudget) match {
      case RegexMatchResult.Matched(false) => false
      case _ => true
    }

  private def matchRecordName(
      recordName: String,
      recordType: RecordType,
      zone: Zone,
      rule: ACLRule,
      budget: RegexBudget
  ): RegexMatchResult =
    rule.recordMask match {
      case Some(mask) if recordType == RecordType.PTR =>
        RegexMatchResult.Matched(ReverseZoneHelpers.recordsetIsWithinCidrMask(mask, zone, recordName))
      case Some(_) if budget.timedOutRules.contains(rule) =>
        RegexMatchResult.TimedOut
      case Some(mask) =>
        val result = RegexValidator.matchDetectingTimeout(recordName, mask, budget.deadlineNanos)
        if (result == RegexMatchResult.TimedOut) budget.timedOutRules += rule
        result
      case None => RegexMatchResult.Matched(true)
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
      zone: Zone,
      recordOwnerGroupId: Option[String] = None,
      recordData: List[RecordData] = List.empty
  ): AccessLevel = auth match {
    case testUser if testUser.isTestUser && !zone.isTest => AccessLevel.NoAccess
    case admin if admin.isGroupMember(zone.adminGroupId) || admin.isSuper=>
      AccessLevel.Delete
    case recordOwner
        if zone.shared && sharedRecordAccess(recordOwner, recordType, recordOwnerGroupId) =>
      AccessLevel.Delete
    case support if support.isSystemAdmin =>
      val aclAccess = getAccessFromAcl(auth, recordName, recordType, zone)
      if (aclAccess == AccessLevel.NoAccess)
        AccessLevel.Read
      else aclAccess
    case globalAclUser
        if globalAcls.isAuthorized(globalAclUser, recordName, recordType, zone, recordData) =>
      AccessLevel.Delete
    case _ => getAccessFromAcl(auth, recordName, recordType, zone)
  }

  def sharedRecordAccess(
      auth: AuthPrincipal,
      recordType: RecordType,
      recordOwnerGroupId: Option[String]
  ): Boolean =
    recordOwnerGroupId.exists(auth.isGroupMember) ||
      (recordOwnerGroupId.isEmpty && sharedApprovedTypes.contains(recordType))
}
