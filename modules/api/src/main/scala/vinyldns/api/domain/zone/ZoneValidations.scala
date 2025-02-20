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
import com.comcast.ip4s.Cidr
import java.time.Instant
import java.time.temporal.ChronoUnit
import vinyldns.api.Interfaces.ensuring
import vinyldns.core.domain.membership.User
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.zone.{ACLRule, Zone, ZoneACL}

import scala.util.{Failure, Success, Try}

class ZoneValidations(syncDelayMillis: Int) {

  def outsideSyncDelay(zone: Zone): Either[Throwable, Unit] =
    zone.latestSync match {
      case Some(time) if Instant.now.truncatedTo(ChronoUnit.MILLIS).toEpochMilli - time.toEpochMilli < syncDelayMillis => {
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

  def isValidGenerateZone(responseCode : Int, responseMsg : String): Either[Throwable, Unit] =
    ensuring(InvalidRequest(responseMsg)) {
      responseCode <= 400
    }

  def aclRuleMaskIsValid(rule: ACLRule): Either[Throwable, Unit] =
    rule.recordMask match {
      case Some(mask) if rule.recordTypes == Set(RecordType.PTR) =>
        Try(Cidr.fromString(mask).get) match {
          case Success(_) => Right(())
          case Failure(_) =>
            InvalidRequest(s"PTR types must have no mask or a valid CIDR mask: Invalid CIDR block").asLeft
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

  // Validates that the zone is either not shared or shared and the user is a super or support user
  def validateSharedZoneAuthorized(zoneShared: Boolean, user: User): Either[Throwable, Unit] =
    ensuring(NotAuthorizedError("Not authorized to create shared zones."))(
      !zoneShared || user.isSuper || user.isSupport
    )

  // Validates that the zone shared status has not been changed, or changed and the user is a super user
  def validateSharedZoneAuthorized(
      currentShared: Boolean,
      updateShared: Boolean,
      user: User
  ): Either[Throwable, Unit] =
    ensuring(
      NotAuthorizedError(
        s"Not authorized to update zone shared status from $currentShared to $updateShared."
      )
    )(currentShared == updateShared || user.isSuper || user.isSupport)
}
