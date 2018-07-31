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

import cats.syntax.either._
import vinyldns.api.Interfaces.ensuring
import vinyldns.api.domain.auth.AuthPrincipal
import vinyldns.api.domain.record.{RecordSet, RecordType}
import vinyldns.api.domain.record.RecordType.RecordType
import vinyldns.api.domain.zone.AccessLevel.AccessLevel
import vinyldns.api.domain.zone._

class AccessValidations(approvedNsGroups: Set[String] = Set()) extends AccessValidationAlgebra {

  def canSeeZone(auth: AuthPrincipal, zone: Zone): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} cannot access zone '${zone.name}'"))(
      (hasZoneAdminAccess(auth, zone) || zone.shared) || userHasAclRules(auth, zone))

  def canChangeZone(auth: AuthPrincipal, zone: Zone): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} cannot modify zone '${zone.name}'"))(
      hasZoneAdminAccess(auth, zone))

  def canAddRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone): Either[Throwable, Unit] = {
    val accessLevel = getAccessLevel(auth, recordName, recordType, zone)
    val access = ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} does not have access to create " +
        s"$recordName.${zone.name}"))(
      accessLevel == AccessLevel.Delete || accessLevel == AccessLevel.Write)

    for {
      _ <- access
      _ <- doNSCheck(auth, recordType, zone)
    } yield ().asRight
  }

  def canUpdateRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone): Either[Throwable, Unit] = {
    val accessLevel = getAccessLevel(auth, recordName, recordType, zone)
    val access = ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} does not have access to update " +
        s"$recordName.${zone.name}"))(
      accessLevel == AccessLevel.Delete || accessLevel == AccessLevel.Write)

    for {
      _ <- access
      _ <- doNSCheck(auth, recordType, zone)
    } yield ().asRight
  }

  def canDeleteRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone): Either[Throwable, Unit] = {
    val access = ensuring(
      NotAuthorizedError(s"User ${auth.signedInUser.userName} does not have access to delete " +
        s"$recordName.${zone.name}"))(
      getAccessLevel(auth, recordName, recordType, zone) == AccessLevel.Delete)

    for {
      _ <- access
      _ <- doNSCheck(auth, recordType, zone)
    } yield ().asRight
  }

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
    if (hasZoneAdminAccess(auth, zone)) recordSets.map(RecordSetInfo(_, AccessLevel.Delete))
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
        val accessLevel = getAccessFromUserAcls(rs.name, rs.typ)
        RecordSetInfo(rs, accessLevel)
      }
    }

  /* Non-algebra methods */
  def doNSCheck(
      authPrincipal: AuthPrincipal,
      recordType: RecordType,
      zone: Zone): Either[Throwable, Unit] = {
    def nsAuthorized: Boolean =
      authPrincipal.signedInUser.isSuper ||
        (authPrincipal.isAuthorized(zone.adminGroupId) && approvedNsGroups.contains(
          zone.adminGroupId))

    ensuring(
      NotAuthorizedError(
        "Do not have permissions to manage NS recordsets, please contact vinyldns-support"))(
      recordType != RecordType.NS || (recordType == RecordType.NS && nsAuthorized))
  }

  def hasZoneAdminAccess(auth: AuthPrincipal, zone: Zone): Boolean =
    auth.isAuthorized(zone.adminGroupId)

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
      zone: Zone): AccessLevel =
    if (hasZoneAdminAccess(auth, zone)) AccessLevel.Delete
    else getAccessFromAcl(auth, recordName, recordType, zone)
}
