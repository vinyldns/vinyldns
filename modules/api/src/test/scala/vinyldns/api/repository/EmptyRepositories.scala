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
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{Zone, ZoneRepository, ListZonesResults}
import cats.effect._
import scalikejdbc._
import vinyldns.core.domain.membership._
import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.zone.ZoneRepository.DuplicateZoneError

// Empty implementations let our other test classes just edit with the methods they need

trait EmptyRecordSetRepo extends RecordSetRepository {

  def getRecordSetsByName(zoneId: String, name: String): IO[List[RecordSet]] =
    IO.pure(List())

  def apply(db: DB, changeSet: ChangeSet): IO[ChangeSet] = IO.pure(changeSet)

  def listRecordSets(
                      zoneId: Option[String],
                      startFrom: Option[String],
                      maxItems: Option[Int],
                      recordNameFilter: Option[String],
                      recordTypeFilter: Option[Set[RecordType]],
                      recordOwnerGroupFilter: Option[String],
                      nameSort: NameSort
                    ): IO[ListRecordSetResults] =
    IO.pure(ListRecordSetResults(nameSort = nameSort))


  def getRecordSets(zoneId: String, name: String, typ: RecordType): IO[List[RecordSet]] =
    IO.pure(List())

  def getRecordSet(recordSetId: String): IO[Option[RecordSet]] =
    IO.pure(None)

  def getRecordSetCount(zoneId: String): IO[Int] = IO.pure(0)

  def getRecordSetsByFQDNs(names: Set[String]): IO[List[RecordSet]] = IO.pure(List())

  def getFirstOwnedRecordByGroup(ownerGroupId: String): IO[Option[String]] = IO.pure(None)

  def deleteRecordSetsInZone(DB: DB, zoneId: String, zoneName: String): IO[Unit] = IO(())
}

trait EmptyRecordSetCacheRepo extends RecordSetCacheRepository {

  def listRecordSetData(
                         zoneId: Option[String],
                         startFrom: Option[String],
                         maxItems: Option[Int],
                         recordNameFilter: Option[String],
                         recordTypeFilter: Option[Set[RecordType]],
                         recordOwnerGroupFilter: Option[String],
                         nameSort: NameSort
                       ): IO[ListRecordSetResults] =
    IO.pure(ListRecordSetResults(nameSort = nameSort))
}

trait EmptyZoneRepo extends ZoneRepository {

  def save(zone: Zone): IO[Either[DuplicateZoneError, Zone]] = IO.pure(Right(zone))

  def getZone(zoneId: String): IO[Option[Zone]] = IO.pure(None)

  def getZoneByName(zoneName: String): IO[Option[Zone]] = IO.pure(None)

  def listZonesByAdminGroupIds(
     authPrincipal: AuthPrincipal,
     startFrom: Option[String] = None,
     maxItems: Int = 100,
     adminGroupIds: Set[String],
     ignoreAccess: Boolean = false
   ): IO[ListZonesResults] = IO.pure(ListZonesResults())

  def listZones(
                 authPrincipal: AuthPrincipal,
                 zoneNameFilter: Option[String] = None,
                 startFrom: Option[String] = None,
                 maxItems: Int = 100,
                 ignoreAccess: Boolean = false
               ): IO[ListZonesResults] = IO.pure(ListZonesResults())

  def getZonesByAdminGroupId(adminGroupId: String): IO[List[Zone]] = IO.pure(List())

  def getZonesByNames(zoneNames: Set[String]): IO[Set[Zone]] = IO.pure(Set())

  def getZonesByFilters(zoneNames: Set[String]): IO[Set[Zone]] = IO.pure(Set())

  def getFirstOwnedZoneAclGroupId(groupId: String): IO[Option[String]] = IO.pure(None)
}

trait EmptyGroupRepo extends GroupRepository {

  def save(db: DB, group: Group): IO[Group] = IO.pure(group)

  def delete(group: Group): IO[Group] = IO.pure(group)

  def getGroup(groupId: String): IO[Option[Group]] = IO.pure(None)

  def getGroups(groupIds: Set[String]): IO[Set[Group]] = IO.pure(Set())

  def getGroupsByName(groupNames: Set[String]): IO[Set[Group]] = IO.pure(Set())

  def getGroupByName(groupName: String): IO[Option[Group]] = IO.pure(None)

  def getGroupsByName(groupName: String): IO[Set[Group]] = IO.pure(Set())

  def getAllGroups(): IO[Set[Group]] = IO.pure(Set())
}

trait EmptyUserRepo extends UserRepository {
  def getUser(userId: String): IO[Option[User]] = IO.pure(None)

  def getUsers(
                userIds: Set[String],
                startFrom: Option[String],
                maxItems: Option[Int]
              ): IO[ListUsersResults] = IO.pure(ListUsersResults(List(), None))

  def getAllUsers: IO[List[User]] = IO.pure(List())

  def getUserByAccessKey(accessKey: String): IO[Option[User]] = IO.pure(None)

  def getUserByName(userName: String): IO[Option[User]] = IO.pure(None)

  def save(user: User): IO[User] = IO.pure(user)

  def save(users: List[User]): IO[List[User]] = IO.pure(List())
}
