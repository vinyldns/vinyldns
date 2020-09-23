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

package vinyldns.route53.backend

import vinyldns.core.domain.backend.{Backend, BackendProvider}
import vinyldns.core.domain.zone.Zone

class Route53BackendProvider(connections: List[Route53Backend]) extends BackendProvider {

  private val connMap: Map[String, Route53Backend] = connections.map(c => c.id -> c).toMap

  /**
    * Given a zone, returns a connection to the zone, returns None if cannot connect
    *
    * @param zone The zone to attempt to connect to
    * @return A backend that is usable, or None if it could not connect
    */
  def connect(zone: Zone): Option[Backend] =
    // only way to connect is via backend id right now
    zone.backendId.flatMap(connectById)

  /**
    * Given a backend id, looks up the backend for this provider if it exists
    *
    * @return A backend that is usable, or None if could not connect
    */
  def connectById(backendId: String): Option[Backend] =
    connMap.get(backendId)

  /**
    * @return The backend ids loaded with this provider
    */
  def ids: List[String] = connMap.keys.toList
}
