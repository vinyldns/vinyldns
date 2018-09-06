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

import cats.syntax.either._
import com.aaronbedra.orchard.CIDR
import org.joda.time.DateTime
import vinyldns.api.Interfaces.ensuring
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.zone.{ACLRule, Zone, ZoneACL}

import scala.util.{Failure, Success, Try}

class ZoneValidations(syncDelayMillis: Int) {

  def userIsMemberOfGroup(groupId: String, authPrincipal: AuthPrincipal): Either[Throwable, Unit] =
    ensuring(InvalidZoneAdminError(s"User is not a member of $groupId."))(
      authPrincipal.isAuthorized(groupId))

  def outsideSyncDelay(zone: Zone): Either[Throwable, Unit] =
    zone.latestSync match {
      case Some(time) if DateTime.now.getMillis - time.getMillis < syncDelayMillis => {
        RecentSyncError(s"Zone ${zone.name} was recently synced. Cannot complete sync").asLeft
      }
      case _ => Right(())
    }

  // TODO - zone ACL validations should happen up front as input validation longer term
  def isValidZoneAcl(acl: ZoneACL): Either[Throwable, Unit] =
    acl.rules.foldLeft(().asRight[Throwable]) {
      case (acc, rule) => acc.flatMap(_ => isValidAclRule(rule))
    }

  def isValidAclRule(rule: ACLRule): Either[Throwable, Unit] =
    for {
      _ <- isUserOrGroupRule(rule)
      _ <- aclRuleMaskIsValid(rule)
    } yield ()

  def isUserOrGroupRule(rule: ACLRule): Either[Throwable, Unit] =
    ensuring(InvalidRequest("Invalid ACL rule: ACL rules must have a group or user id")) {
      (rule.groupId ++ rule.userId).size == 1
    }

  def aclRuleMaskIsValid(rule: ACLRule): Either[Throwable, Unit] =
    rule.recordMask match {
      case Some(mask) if rule.recordTypes == Set(RecordType.PTR) =>
        Try(CIDR.valueOf(mask)) match {
          case Success(_) => Right(())
          case Failure(e) =>
            InvalidRequest(s"PTR types must have no mask or a valid CIDR mask: ${e.getMessage}").asLeft
        }
      case Some(_) if rule.recordTypes.contains(RecordType.PTR) =>
        InvalidRequest("Multiple record types including PTR must have no mask").asLeft
      case Some(mask) =>
        Try("string".matches(mask)) match {
          case Success(_) => ().asRight
          case Failure(_) => InvalidRequest(s"record mask $mask is an invalid regex").asLeft
        }
      case None => ().asRight
    }
}
