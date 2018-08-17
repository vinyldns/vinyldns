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

import vinyldns.api.domain.auth.AuthPrincipal

import scala.concurrent.Future

trait ZoneRepository {

  def save(zone: Zone): Future[Zone]

  def getZone(zoneId: String): Future[Option[Zone]]

  def getZoneByName(zoneName: String): Future[Option[Zone]]

  def getZonesByNames(zoneNames: Set[String]): Future[Set[Zone]]

  def getZonesByFilters(zoneNames: Set[String]): Future[Set[Zone]]

  def listZones(
      authPrincipal: AuthPrincipal,
      zoneNameFilter: Option[String] = None,
      offset: Option[Int] = None,
      pageSize: Int = 100): Future[List[Zone]]

  def getZonesByAdminGroupId(adminGroupId: String): Future[List[Zone]]

}
