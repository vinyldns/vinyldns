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
import vinyldns.api.backend.dns.DnsConversions
import vinyldns.api.config.HighValueDomainConfig
import vinyldns.api.domain._
import vinyldns.core.domain.DomainHelpers._
import vinyldns.core.domain.record.RecordType._
import vinyldns.api.domain.zone._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.Group
import vinyldns.core.domain.record.{RecordSet, RecordType}
import vinyldns.core.domain.zone.Zone
import vinyldns.core.Messages._

import scala.util.matching.Regex

object RecordSetValidations {

  def validRecordTypes(recordSet: RecordSet, zone: Zone): Either[Throwable, Unit] =
    recordSet.typ match {
      case CNAME | SOA | TXT | NS | DS => ().asRight
      case PTR =>
        ensuring(InvalidRequest(InvalidPtrErrorMsg))(zone.isReverse)
      case _ =>
        ensuring(InvalidRequest(ReverseLookupErrorMsg.format(recordSet.typ)))(
          !zone.isReverse
        )
    }

  def validRecordNameLength(recordSet: RecordSet, zone: Zone): Either[Throwable, Unit] = {
    val absoluteName = recordSet.name + "." + zone.name
    ensuring(InvalidRequest(RecordNameLengthErrorMsg.format(recordSet.name))) {
      absoluteName.length < 256 || isOriginRecord(recordSet.name, zone.name)
    }
  }

  def notPending(recordSet: RecordSet): Either[Throwable, Unit] =
    ensuring(
      PendingUpdateError(PendingUpdateErrorMsg.format(recordSet.id, recordSet.name, recordSet.typ))
    )(
      !recordSet.isPending
    )

  def noCnameWithNewName(
      newRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone
  ): Either[Throwable, Unit] =
    ensuring(
      RecordSetAlreadyExists(RecordSetCnameExistsErrorMsg.format(newRecordSet.name, zone.name))
    )(
      !existingRecordsWithName.exists(rs => rs.id != newRecordSet.id && rs.typ == CNAME)
    )

  def recordSetDoesNotExist(
      newRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone
  ): Either[Throwable, Unit] =
    ensuring(
      RecordSetAlreadyExists(
        RecordSetAlreadyExistsErrorMsg.format(newRecordSet.name, newRecordSet.typ, zone.name)
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
      InvalidRequest(DottedHostErrorMsg.format(newRecordSet.name, newRecordSet.typ, zone.name))
    )(
      newRecordSet.name == zone.name || !newRecordSet.name.contains(".") ||
        existingRecordSet.exists(_.name == newRecordSet.name)
    )

  def typeSpecificValidations(
      newRecordSet: RecordSet,
      existingRecordsWithName: List[RecordSet],
      zone: Zone,
      existingRecordSet: Option[RecordSet],
      approvedNameServers: List[Regex]
  ): Either[Throwable, Unit] =
    newRecordSet.typ match {
      case CNAME => cnameValidations(newRecordSet, existingRecordsWithName, zone, existingRecordSet)
      case NS => nsValidations(newRecordSet, zone, existingRecordSet, approvedNameServers)
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
          NSApexEditErrorMsg.format(recordSet.name)
        )
      case SOA => InvalidRequest(SOADeleteErrorMsg).asLeft
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
        RecordSetAlreadyExists(CnameDuplicateErrorMsg.format(newRecordSet.name, zone.name))
      )(
        existingRecordsWithName.forall(_.id == newRecordSet.id)
      )
    }

    // cname recordset data cannot contain more than one sequential '.'
    val RDataWithConsecutiveDots = {
      ensuring(
        RecordSetValidation(
          s"RecordSet Data cannot contain consecutive 'dot' character. RData: '${newRecordSet.records.head.toString}'"
        )
      )(
        noConsecutiveDots(newRecordSet.records.head.toString)
      )
    }

    for {
      _ <- isNotOrigin(
        newRecordSet,
        zone,
        InvalidCnameErrorMsg
      )
      _ <- noRecordWithName
      _ <- RDataWithConsecutiveDots
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
        InvalidRequest(DSTTLErrorMsg.format(newRecordSet.name, ns.ttl)).asLeft
      case None =>
        InvalidRequest(DSInvalidErrorMsg.format(newRecordSet.name, zone.name)).asLeft
    }

    for {
      _ <- isNotDotted(newRecordSet, zone)
      _ <- isNotOrigin(
        newRecordSet,
        zone,
        DSApexErrorMsg.format(newRecordSet.name)
      )
      _ <- nsChecks
    } yield ()
  }

  def nsValidations(
      newRecordSet: RecordSet,
      zone: Zone,
      oldRecordSet: Option[RecordSet],
      approvedNameServers: List[Regex]
  ): Either[Throwable, Unit] = {
    // TODO kept consistency with old validation. Not sure why NS could be dotted in reverse specifically
    val isNotDottedHost = if (!zone.isReverse) isNotDotted(newRecordSet, zone) else ().asRight

    for {
      _ <- isNotDottedHost
      _ <- isNotOrigin(
        newRecordSet,
        zone,
        NSApexErrorMsg.format(newRecordSet.name)
      )
      _ <- containsApprovedNameServers(newRecordSet, approvedNameServers)
      _ <- oldRecordSet
        .map { rs =>
          isNotOrigin(
            rs,
            zone,
            NSApexEditErrorMsg.format(newRecordSet.name)
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

  private def containsApprovedNameServers(
      nsRecordSet: RecordSet,
      approvedNameServers: List[Regex]
  ): Either[Throwable, Unit] =
    ZoneRecordValidations
      .containsApprovedNameServers(approvedNameServers, nsRecordSet)
      .toEither
      .map(_ => ())
      .leftMap(errors => InvalidRequest(errors.toList.mkString(", ")))

  private def isOriginRecord(recordSetName: String, zoneName: String): Boolean =
    recordSetName == "@" || omitTrailingDot(recordSetName) == omitTrailingDot(zoneName)

  def isNotHighValueDomain(
      recordSet: RecordSet,
      zone: Zone,
      highValueDomainConfig: HighValueDomainConfig
  ): Either[Throwable, Unit] = {
    val result = recordSet.typ match {
      case RecordType.PTR =>
        val ip = ReverseZoneHelpers.reverseNameToIp(recordSet.name, zone)
        ZoneRecordValidations.isNotHighValueIp(highValueDomainConfig.ipList, ip)
      case _ =>
        val fqdn = DnsConversions.recordDnsName(recordSet.name, zone.name).toString()
        ZoneRecordValidations.isNotHighValueFqdn(highValueDomainConfig.fqdnRegexes, fqdn)
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
        InvalidGroupError(RecordOwnerGroupNotFoundErrorMsg.format(groupId)).asLeft
      case (Some(groupId), Some(_)) =>
        if (authPrincipal.isSuper || authPrincipal.isGroupMember(groupId)) ().asRight
        else InvalidRequest(UserNotInOwnerGroupErrorMsg.format(groupId)).asLeft
    }

  def unchangedRecordName(
      existing: RecordSet,
      updates: RecordSet,
      zone: Zone
  ): Either[Throwable, Unit] = Either.cond(
    updates.name.toLowerCase == existing.name.toLowerCase
      || (updates.name == "@" && existing.name.toLowerCase == zone.name.toLowerCase),
    (),
    InvalidRequest(UnchangedRecordNameErrorMsg)
  )

  def unchangedRecordType(
      existing: RecordSet,
      updates: RecordSet
  ): Either[Throwable, Unit] =
    Either.cond(
      updates.typ == existing.typ,
      (),
      InvalidRequest(UnchangedRecordTypeErrorMsg)
    )

  def unchangedZoneId(
      existing: RecordSet,
      updates: RecordSet
  ): Either[Throwable, Unit] =
    Either.cond(
      updates.zoneId == existing.zoneId,
      (),
      InvalidRequest(UnchangedZoneIdErrorMsg)
    )

  def validRecordNameFilterLength(recordNameFilter: String): Either[Throwable, Unit] =
    ensuring(
      InvalidRequest(RecordNameFilterErrorMsg)
    ) {
      val searchRegex: Regex = """[a-zA-Z0-9].*[a-zA-Z0-9]+""".r
      searchRegex.findFirstIn(recordNameFilter).isDefined
    }
}
