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

package vinyldns.core.domain.zone

import cats.effect._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.zone.ZoneRepository.DuplicateZoneError
import vinyldns.core.repository.Repository

trait ZoneRepository extends Repository {

  def save(zone: Zone): IO[Either[DuplicateZoneError, Zone]]

  def getZone(zoneId: String): IO[Option[Zone]]

  def getZones(zoneId: Set[String]): IO[Set[Zone]]

  def getAllZonesWithSyncSchedule: IO[Set[Zone]]

  def getZoneByName(zoneName: String): IO[Option[Zone]]

  def getZonesByNames(zoneNames: Set[String]): IO[Set[Zone]]

  def getZonesByFilters(zoneNames: Set[String]): IO[Set[Zone]]

  def listZonesByAdminGroupIds(
       authPrincipal: AuthPrincipal,
       startFrom: Option[String] = None,
       maxItems: Int = 100,
       adminGroupIds: Set[String],
       ignoreAccess: Boolean = false
     ): IO[ListZonesResults]

  def listZones(
      authPrincipal: AuthPrincipal,
      zoneNameFilter: Option[String] = None,
      startFrom: Option[String] = None,
      maxItems: Int = 100,
      ignoreAccess: Boolean = false,
      includeReverse: Boolean = true
  ): IO[ListZonesResults]

  def getZonesByAdminGroupId(adminGroupId: String): IO[List[Zone]]

  def getFirstOwnedZoneAclGroupId(groupId: String): IO[Option[String]]
}

object ZoneRepository {
  final case class DuplicateZoneError(zoneName: String) {
    def message: String = s"""Zone with name "$zoneName" already exists."""
  }
}
