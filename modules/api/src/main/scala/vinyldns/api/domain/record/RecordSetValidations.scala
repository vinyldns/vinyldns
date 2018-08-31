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
import vinyldns.api.domain.DomainHelpers.omitTrailingDot
import vinyldns.api.domain.record.RecordType._
import vinyldns.api.domain.zone._

object RecordSetValidations {

  def validRecordTypes(recordSet: RecordSet, zone: Zone): Either[Throwable, Unit] =
    recordSet.typ match {
      case CNAME | SOA | TXT | NS => ().asRight
      case PTR =>
        ensuring(InvalidRequest("PTR is not valid in forward lookup zone"))(zone.isReverse)
      case _ =>
        ensuring(InvalidRequest(s"${recordSet.typ} is not valid in reverse lookup zone."))(
          !zone.isReverse)
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
          s"currently has a pending change"))(
      !recordSet.isPending
    )

  def noCnameWithNewName(
      newRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone): Either[Throwable, Unit] =
    ensuring(
      RecordSetAlreadyExists(s"RecordSet with name ${newRecordSet.name} and type CNAME already " +
        s"exists in zone ${zone.name}"))(
      !existingRecordsWithName.exists(rs => rs.id != newRecordSet.id && rs.typ == CNAME)
    )

  def isUniqueUpdate(
      newRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone): Either[Throwable, Unit] =
    ensuring(
      RecordSetAlreadyExists(
        s"RecordSet with name ${newRecordSet.name} and type ${newRecordSet.typ} already " +
          s"exists in zone ${zone.name}"))(
      !existingRecordsWithName.exists(rs => rs.id != newRecordSet.id && rs.typ == newRecordSet.typ)
    )

  def isNotDotted(recordSet: RecordSet, zone: Zone): Either[Throwable, Unit] =
    ensuring(InvalidRequest(
      s"Record with name ${recordSet.name} is a dotted host which is illegal in this zone ${zone.name}"))(
      recordSet.name == zone.name || !recordSet.name.contains(".")
    )

  def typeSpecificAddValidations(
      newRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone): Either[Throwable, Unit] =
    newRecordSet.typ match {
      case CNAME => cnameValidations(newRecordSet, existingRecordsWithName, zone)
      case NS => nsValidations(newRecordSet, zone)
      case SOA => soaValidations(newRecordSet, zone)
      case PTR => ptrValidations(newRecordSet, zone)
      case SRV => ().asRight // SRV does not go through dotted host check
      case _ => isNotDotted(newRecordSet, zone)
    }

  def typeSpecificEditValidations(
      newRecordSet: RecordSet,
      oldRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone): Either[Throwable, Unit] =
    newRecordSet.typ match {
      case CNAME => cnameValidations(newRecordSet, existingRecordsWithName, zone)
      case NS => nsValidations(newRecordSet, zone, Some(oldRecordSet))
      case SOA => soaValidations(newRecordSet, zone)
      case PTR => ptrValidations(newRecordSet, zone)
      case SRV => ().asRight // SRV does not go through dotted host check
      case _ => isNotDotted(newRecordSet, zone)
    }

  def typeSpecificDeleteValidations(recordSet: RecordSet, zone: Zone): Either[Throwable, Unit] =
    // for delete, the only validation is that you cant remove an NS at origin
    recordSet.typ match {
      case NS =>
        isNotOrigin(
          recordSet,
          zone,
          s"Record with name ${recordSet.name} is an NS record at apex and cannot be edited")
      case SOA => InvalidRequest("SOA records cannot be deleted").asLeft
      case _ => ().asRight
    }

  /* Add/update validations by record type */
  def cnameValidations(
      newRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone): Either[Throwable, Unit] = {
    // cannot create a cname record if a record with the same exists
    val noRecordWithName = {
      ensuring(
        RecordSetAlreadyExists(s"RecordSet with name ${newRecordSet.name} already " +
          s"exists in zone ${zone.name}, CNAME record cannot use duplicate name"))(
        !existingRecordsWithName.exists(_.id != newRecordSet.id)
      )
    }

    for {
      _ <- isNotOrigin(
        newRecordSet,
        zone,
        "CNAME RecordSet cannot have name '@' because it points to zone origin")
      _ <- noRecordWithName
      _ <- isNotDotted(newRecordSet, zone)
    } yield ()

  }

  def nsValidations(
      newRecordSet: RecordSet,
      zone: Zone,
      oldRecordSet: Option[RecordSet] = None): Either[Throwable, Unit] = {
    // TODO kept consistency with old validation. Not sure why NS could be dotted in reverse specifically
    val isNotDottedHost = if (!zone.isReverse) isNotDotted(newRecordSet, zone) else ().asRight

    for {
      _ <- isNotDottedHost
      _ <- isNotOrigin(
        newRecordSet,
        zone,
        s"Record with name ${newRecordSet.name} is an NS record at apex and cannot be added")
      _ <- containsApprovedNameServers(newRecordSet)
      _ <- oldRecordSet
        .map { rs =>
          isNotOrigin(
            rs,
            zone,
            s"Record with name ${newRecordSet.name} is an NS record at apex and cannot be edited")
        }
        .getOrElse(().asRight)
    } yield ()
  }

  def soaValidations(newRecordSet: RecordSet, zone: Zone): Either[Throwable, Unit] =
    // TODO kept consistency with old validation. in theory if SOA always == zone name, no special case is needed here
    if (!zone.isReverse) isNotDotted(newRecordSet, zone) else ().asRight

  def ptrValidations(newRecordSet: RecordSet, zone: Zone): Either[Throwable, Unit] =
    // TODO we don't check for PTR as dotted...not sure why
    ReverseZoneHelpers.ptrIsInZone(zone, newRecordSet.name, newRecordSet.typ).map(_ => ())

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

}
