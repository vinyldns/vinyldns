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

trait ZoneChangeRepository extends Repository {

  def save(zoneChange: ZoneChange): IO[ZoneChange]

  def listZoneChanges(
      zoneId: String,
      startFrom: Option[String] = None,
      maxItems: Int = 100
  ): IO[ListZoneChangesResults]

  def listDeletedZones(
                        authPrincipal: AuthPrincipal,
                        zoneNameFilter: Option[String] = None,
                        startFrom: Option[String] = None,
                        maxItems: Int = 100,
                        ignoreAccess: Boolean = false,
                        accessFilter: Option[Int] = None
                      ): IO[ListDeletedZonesChangeResults]

  def listFailedZoneChanges(
                             maxItems: Int = 100,
                             startFrom: Int= 0
                           ): IO[ListFailedZoneChangesResults]

  /* Returns (abandoned, abandonedPtr, abandonedShared) in 1 SQL round-trip. */
  def countAllAbandonedStats(): IO[(Int, Int, Int)]

  /** Returns (myAbandoned, myAbandonedPtr, myAbandonedShared) in 1 SQL round-trip.
    * For super/support callers ZoneService substitutes the global abandoned stats instead.
    */
  def countAllAbandonedStatsForUser(authPrincipal: AuthPrincipal): IO[(Int, Int, Int)]
}
