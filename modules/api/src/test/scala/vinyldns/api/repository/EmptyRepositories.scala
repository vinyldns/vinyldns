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

import scala.concurrent.Future

// Empty implementations let our other test classes just edit with the methods they need

trait EmptyRecordSetRepo extends RecordSetRepository {

  def getRecordSetsByName(zoneId: String, name: String): Future[List[RecordSet]] =
    Future.successful(List())

  def apply(changeSet: ChangeSet): Future[ChangeSet] = Future.successful(changeSet)

  def listRecordSets(
      zoneId: String,
      startFrom: Option[String],
      maxItems: Option[Int],
      recordNameFilter: Option[String]): Future[ListRecordSetResults] =
    Future.successful(ListRecordSetResults())

  def getRecordSets(zoneId: String, name: String, typ: RecordType): Future[List[RecordSet]] =
    Future.successful(List())

  def getRecordSet(zoneId: String, recordSetId: String): Future[Option[RecordSet]] =
    Future.successful(None)

  def getRecordSetCount(zoneId: String): Future[Int] = Future.successful(0)
}

trait EmptyZoneRepo extends ZoneRepository {

  def save(zone: Zone): Future[Zone] = Future.successful(zone)

  def getZone(zoneId: String): Future[Option[Zone]] = Future.successful(None)

  def getZoneByName(zoneName: String): Future[Option[Zone]] = Future.successful(None)

  def listZones(
      authPrincipal: AuthPrincipal,
      zoneNameFilter: Option[String] = None,
      offset: Option[Int] = None,
      pageSize: Int = 100): Future[List[Zone]] = Future.successful(List())

  def getZonesByAdminGroupId(adminGroupId: String): Future[List[Zone]] = Future.successful(List())

  def getZonesByNames(zoneNames: Set[String]): Future[Set[Zone]] = Future.successful(Set())

  def getZonesByFilters(zoneNames: Set[String]): Future[Set[Zone]] = Future.successful(Set())

}
