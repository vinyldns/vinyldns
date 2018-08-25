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

import vinyldns.api.domain.auth.AuthPrincipal
import vinyldns.api.domain.record.RecordType.RecordType
import vinyldns.api.domain.record.{ChangeSet, ListRecordSetResults, RecordSet, RecordSetRepository}
import vinyldns.api.domain.zone.{Zone, ZoneRepository}

import cats.effect._

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
}

trait EmptyZoneRepo extends ZoneRepository {

  def save(zone: Zone): IO[Zone] = IO.pure(zone)

  def getZone(zoneId: String): IO[Option[Zone]] = IO.pure(None)

  def getZoneByName(zoneName: String): IO[Option[Zone]] = IO.pure(None)

  def listZones(
      authPrincipal: AuthPrincipal,
      zoneNameFilter: Option[String] = None,
      offset: Option[Int] = None,
      pageSize: Int = 100): IO[List[Zone]] = IO.pure(List())

  def getZonesByAdminGroupId(adminGroupId: String): IO[List[Zone]] = IO.pure(List())

  def getZonesByNames(zoneNames: Set[String]): IO[Set[Zone]] = IO.pure(Set())

  def getZonesByFilters(zoneNames: Set[String]): IO[Set[Zone]] = IO.pure(Set())

}
