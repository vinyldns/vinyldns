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

package vinyldns.api.repository

import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.{ChangeSet, ListRecordSetResults, RecordSet, RecordSetRepository}
import vinyldns.core.domain.zone.{ListZonesResults, Zone, ZoneRepository}
import cats.effect._
import vinyldns.core.domain.membership.{Group, GroupRepository}
import vinyldns.core.domain.zone.ZoneRepository.DuplicateZoneError

// Empty implementations let our other test classes just edit with the methods they need

trait EmptyRecordSetRepo extends RecordSetRepository {

  def getRecordSetsByName(zoneId: String, name: String): IO[List[RecordSet]] =
    IO.pure(List())

  def apply(changeSet: ChangeSet): IO[ChangeSet] = IO.pure(changeSet)

  def listRecordSets(
      zoneId: String,
      startFrom: Option[String],
      maxItems: Option[Int],
      recordNameFilter: Option[String]): IO[ListRecordSetResults] =
    IO.pure(ListRecordSetResults())

  def getRecordSets(zoneId: String, name: String, typ: RecordType): IO[List[RecordSet]] =
    IO.pure(List())

  def getRecordSet(zoneId: String, recordSetId: String): IO[Option[RecordSet]] =
    IO.pure(None)

  def getRecordSetCount(zoneId: String): IO[Int] = IO.pure(0)

  def getRecordSetsByFQDNs(names: Set[String]): IO[List[RecordSet]] = IO.pure(List())

  def getFirstOwnedRecordByGroup(ownerGroupId: String): IO[Option[String]] = IO.pure(None)

  def deleteRecordSetsInZone(zoneId: String, zoneName: String): IO[Unit] = IO(())
}

trait EmptyZoneRepo extends ZoneRepository {

  def save(zone: Zone): IO[Either[DuplicateZoneError, Zone]] = IO.pure(Right(zone))

  def getZone(zoneId: String): IO[Option[Zone]] = IO.pure(None)

  def getZoneByName(zoneName: String): IO[Option[Zone]] = IO.pure(None)

  def listZones(
      authPrincipal: AuthPrincipal,
      zoneNameFilter: Option[String] = None,
      startFrom: Option[String] = None,
      maxItems: Int = 100,
      ignoreAccess: Boolean = false): IO[ListZonesResults] = IO.pure(ListZonesResults())

  def getZonesByAdminGroupId(adminGroupId: String): IO[List[Zone]] = IO.pure(List())

  def getZonesByNames(zoneNames: Set[String]): IO[Set[Zone]] = IO.pure(Set())

  def getZonesByFilters(zoneNames: Set[String]): IO[Set[Zone]] = IO.pure(Set())

  def getFirstOwnedZoneAclGroupId(groupId: String): IO[Option[String]] = IO.pure(None)
}

trait EmptyGroupRepo extends GroupRepository {

  def save(group: Group): IO[Group] = IO.pure(group)

  def delete(group: Group): IO[Group] = IO.pure(group)

  def getGroup(groupId: String): IO[Option[Group]] = IO.pure(None)

  def getGroups(groupIds: Set[String]): IO[Set[Group]] = IO.pure(Set())

  def getGroupByName(groupName: String): IO[Option[Group]] = IO.pure(None)

  def getAllGroups(): IO[Set[Group]] = IO.pure(Set())
}
