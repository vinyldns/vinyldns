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

package vinyldns.api.domain.record

import cats.syntax.either._
import vinyldns.api.Interfaces._
import vinyldns.api.VinylDNSConfig
import vinyldns.api.domain._
import vinyldns.api.domain.dns.DnsConversions
import vinyldns.core.domain.DomainHelpers.omitTrailingDot
import vinyldns.core.domain.record.RecordType._
import vinyldns.api.domain.zone._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.Group
import vinyldns.core.domain.record.{RecordSet, RecordType}
import vinyldns.core.domain.zone.Zone

object RecordSetValidations {

  def validRecordTypes(recordSet: RecordSet, zone: Zone): Either[Throwable, Unit] =
    recordSet.typ match {
      case CNAME | SOA | TXT | NS | DS => ().asRight
      case PTR =>
        ensuring(InvalidRequest("PTR is not valid in forward lookup zone"))(zone.isReverse)
      case _ =>
        ensuring(InvalidRequest(s"${recordSet.typ} is not valid in reverse lookup zone."))(
          !zone.isReverse
        )
    }

  def validRecordNameLength(recordSet: RecordSet, zone: Zone): Either[Throwable, Unit] = {
    val absoluteName = recordSet.name + "." + zone.name
    ensuring(InvalidRequest(s"record set name ${recordSet.name} is too long")) {
      absoluteName.length < 256 || isOriginRecord(recordSet.name, zone.name)
    }
  }

  def notPending(recordSet: RecordSet): Either[Throwable, Unit] =
    ensuring(
      PendingUpdateError(
        s"RecordSet with id ${recordSet.id}, name ${recordSet.name} and type ${recordSet.typ} " +
          s"currently has a pending change"
      )
    )(
      !recordSet.isPending
    )

  def noCnameWithNewName(
      newRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone
  ): Either[Throwable, Unit] =
    ensuring(
      RecordSetAlreadyExists(
        s"RecordSet with name ${newRecordSet.name} and type CNAME already " +
          s"exists in zone ${zone.name}"
      )
    )(
      !existingRecordsWithName.exists(rs => rs.id != newRecordSet.id && rs.typ == CNAME)
    )

  def isUniqueUpdate(
      newRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone
  ): Either[Throwable, Unit] =
    ensuring(
      RecordSetAlreadyExists(
        s"RecordSet with name ${newRecordSet.name} and type ${newRecordSet.typ} already " +
          s"exists in zone ${zone.name}"
      )
    )(
      !existingRecordsWithName.exists(rs => rs.id != newRecordSet.id && rs.typ == newRecordSet.typ)
    )

  def isNotDotted(
      newRecordSet: RecordSet,
      zone: Zone,
      existingRecordSet: Option[RecordSet] = None
  ): Either[Throwable, Unit] =
    ensuring(
      InvalidRequest(
        s"Record with name ${newRecordSet.name} and type ${newRecordSet.typ} is a dotted host which" +
          s" is not allowed in zone ${zone.name}"
      )
    )(
      newRecordSet.name == zone.name || !newRecordSet.name.contains(".") ||
        existingRecordSet.exists(_.name == newRecordSet.name)
    )

  def typeSpecificValidations(
      newRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone,
      existingRecordSet: Option[RecordSet] = None
  ): Either[Throwable, Unit] =
    newRecordSet.typ match {
      case CNAME => cnameValidations(newRecordSet, existingRecordsWithName, zone, existingRecordSet)
      case NS => nsValidations(newRecordSet, zone, existingRecordSet)
      case SOA => soaValidations(newRecordSet, zone)
      case PTR => ptrValidations(newRecordSet, zone)
      case SRV | TXT | NAPTR => ().asRight // SRV, TXT and NAPTR do not go through dotted host check
      case DS => dsValidations(newRecordSet, existingRecordsWithName, zone)
      case _ => isNotDotted(newRecordSet, zone, existingRecordSet)
    }

  def typeSpecificDeleteValidations(recordSet: RecordSet, zone: Zone): Either[Throwable, Unit] =
    // for delete, the only validation is that you cant remove an NS at origin
    recordSet.typ match {
      case NS =>
        isNotOrigin(
          recordSet,
          zone,
          s"Record with name ${recordSet.name} is an NS record at apex and cannot be edited"
        )
      case SOA => InvalidRequest("SOA records cannot be deleted").asLeft
      case _ => ().asRight
    }

  /* Add/update validations by record type */
  def cnameValidations(
      newRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone,
      existingRecordSet: Option[RecordSet] = None
  ): Either[Throwable, Unit] = {
    // cannot create a cname record if a record with the same exists
    val noRecordWithName = {
      ensuring(
        RecordSetAlreadyExists(
          s"RecordSet with name ${newRecordSet.name} already " +
            s"exists in zone ${zone.name}, CNAME record cannot use duplicate name"
        )
      )(
        existingRecordsWithName.forall(_.id == newRecordSet.id)
      )
    }

    for {
      _ <- isNotOrigin(
        newRecordSet,
        zone,
        "CNAME RecordSet cannot have name '@' because it points to zone origin"
      )
      _ <- noRecordWithName
      _ <- isNotDotted(newRecordSet, zone, existingRecordSet)
    } yield ()

  }

  def dsValidations(
      newRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone
  ): Either[Throwable, Unit] = {
    // see https://tools.ietf.org/html/rfc4035#section-2.4
    val nsChecks = existingRecordsWithName.find(_.typ == NS) match {
      case Some(ns) if ns.ttl == newRecordSet.ttl => ().asRight
      case Some(ns) =>
        InvalidRequest(
          s"DS record [${newRecordSet.name}] must have TTL matching its linked NS (${ns.ttl})"
        ).asLeft
      case None =>
        InvalidRequest(
          s"DS record [${newRecordSet.name}] is invalid because there is no NS record with that " +
            s"name in the zone [${zone.name}]"
        ).asLeft
    }

    for {
      _ <- isNotDotted(newRecordSet, zone)
      _ <- isNotOrigin(
        newRecordSet,
        zone,
        s"Record with name [${newRecordSet.name}] is an DS record at apex and cannot be added"
      )
      _ <- nsChecks
    } yield ()
  }

  def nsValidations(
      newRecordSet: RecordSet,
      zone: Zone,
      oldRecordSet: Option[RecordSet] = None
  ): Either[Throwable, Unit] = {
    // TODO kept consistency with old validation. Not sure why NS could be dotted in reverse specifically
    val isNotDottedHost = if (!zone.isReverse) isNotDotted(newRecordSet, zone) else ().asRight

    for {
      _ <- isNotDottedHost
      _ <- isNotOrigin(
        newRecordSet,
        zone,
        s"Record with name ${newRecordSet.name} is an NS record at apex and cannot be added"
      )
      _ <- containsApprovedNameServers(newRecordSet)
      _ <- oldRecordSet
        .map { rs =>
          isNotOrigin(
            rs,
            zone,
            s"Record with name ${newRecordSet.name} is an NS record at apex and cannot be edited"
          )
        }
        .getOrElse(().asRight)
    } yield ()
  }

  def soaValidations(newRecordSet: RecordSet, zone: Zone): Either[Throwable, Unit] =
    // TODO kept consistency with old validation. in theory if SOA always == zone name, no special case is needed here
    if (!zone.isReverse) isNotDotted(newRecordSet, zone) else ().asRight

  def ptrValidations(newRecordSet: RecordSet, zone: Zone): Either[Throwable, Unit] =
    // TODO we don't check for PTR as dotted...not sure why
    ReverseZoneHelpers.ptrIsInClasslessDelegatedZone(zone, newRecordSet.name).map(_ => ())

  private def isNotOrigin(recordSet: RecordSet, zone: Zone, err: String): Either[Throwable, Unit] =
    ensuring(InvalidRequest(err))(
      !isOriginRecord(recordSet.name, omitTrailingDot(zone.name))
    )

  private def containsApprovedNameServers(nsRecordSet: RecordSet): Either[Throwable, Unit] =
    ZoneRecordValidations
      .containsApprovedNameServers(VinylDNSConfig.approvedNameServers, nsRecordSet)
      .toEither
      .map(_ => ())
      .leftMap(errors => InvalidRequest(errors.toList.mkString(", ")))

  private def isOriginRecord(recordSetName: String, zoneName: String): Boolean =
    recordSetName == "@" || omitTrailingDot(recordSetName) == omitTrailingDot(zoneName)

  def isNotHighValueDomain(recordSet: RecordSet, zone: Zone): Either[Throwable, Unit] = {
    val result = recordSet.typ match {
      case RecordType.PTR =>
        val ip = ReverseZoneHelpers.reverseNameToIp(recordSet.name, zone)
        ZoneRecordValidations.isNotHighValueIp(VinylDNSConfig.highValueIpList, ip)
      case _ =>
        val fqdn = DnsConversions.recordDnsName(recordSet.name, zone.name).toString()
        ZoneRecordValidations.isNotHighValueFqdn(VinylDNSConfig.highValueRegexList, fqdn)
    }

    result.toEither
      .map(_ => ())
      .leftMap(errors => InvalidRequest(errors.toList.map(_.message).mkString(", ")))
  }

  def canUseOwnerGroup(
      ownerGroupId: Option[String],
      group: Option[Group],
      authPrincipal: AuthPrincipal
  ): Either[Throwable, Unit] =
    (ownerGroupId, group) match {
      case (None, _) => ().asRight
      case (Some(groupId), None) =>
        InvalidGroupError(s"""Record owner group with id "$groupId" not found""").asLeft
      case (Some(groupId), Some(_)) =>
        if (authPrincipal.isSuper || authPrincipal.isGroupMember(groupId)) ().asRight
        else InvalidRequest(s"""User not in record owner group with id "$groupId"""").asLeft
    }

  def recordSetIsInZone(recordSet: RecordSet, zone: Zone): Either[Throwable, Unit] =
    Either.cond(
      recordSet.zoneId == zone.id,
      (),
      InvalidRequest(s"""Cannot update RecordSet's zoneId attribute""")
    )
}
