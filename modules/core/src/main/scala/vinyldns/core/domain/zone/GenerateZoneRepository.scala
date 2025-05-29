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
import vinyldns.core.repository.Repository

trait GenerateZoneRepository extends Repository {

  def save(generateZone: GenerateZone): IO[GenerateZone]

  def getGenerateZoneByName(zoneName: String): IO[Option[GenerateZone]]

  def deleteTx(generateZone: GenerateZone): IO[Unit]

  def listGenerateZones(
                         authPrincipal: AuthPrincipal,
                         zoneNameFilter: Option[String] = None,
                         startFrom: Option[String] = None,
                         maxItems: Int = 100,
                         ignoreAccess: Boolean = false
                       ): IO[ListGeneratedZonesResults]

  def listGeneratedZonesByAdminGroupIds(
                                         authPrincipal: AuthPrincipal,
                                         startFrom: Option[String] = None,
                                         maxItems: Int = 100,
                                         adminGroupIds: Set[String],
                                         ignoreAccess: Boolean = false
                                       ): IO[ListGeneratedZonesResults]
}
