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

import cats.effect._
import vinyldns.api.domain.auth.AuthPrincipal
import vinyldns.api.repository.Repository

trait ZoneRepository extends Repository {

  def save(zone: Zone): IO[Zone]

  def getZone(zoneId: String): IO[Option[Zone]]

  def getZoneByName(zoneName: String): IO[Option[Zone]]

  def getZonesByNames(zoneNames: Set[String]): IO[Set[Zone]]

  def getZonesByFilters(zoneNames: Set[String]): IO[Set[Zone]]

  def listZones(
      authPrincipal: AuthPrincipal,
      zoneNameFilter: Option[String] = None,
      offset: Option[Int] = None,
      pageSize: Int = 100): IO[List[Zone]]

  def getZonesByAdminGroupId(adminGroupId: String): IO[List[Zone]]

}
