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

import java.util.UUID

import org.joda.time.DateTime
import vinyldns.api.crypto.Crypto
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.zone._

object ZoneChangeGenerator {

  import ZoneChangeStatus._

  def forAdd(
      zone: Zone,
      authPrincipal: AuthPrincipal,
      status: ZoneChangeStatus = Pending
  ): ZoneChange =
    ZoneChange(
      zone
        .copy(id = UUID.randomUUID().toString, created = DateTime.now, status = ZoneStatus.Syncing),
      authPrincipal.userId,
      ZoneChangeType.Create,
      status
    )

  def forUpdate(newZone: Zone, oldZone: Zone, authPrincipal: AuthPrincipal): ZoneChange =
    ZoneChange(
      newZone.copy(updated = Some(DateTime.now), connection = fixConn(oldZone, newZone)),
      authPrincipal.userId,
      ZoneChangeType.Update,
      ZoneChangeStatus.Pending
    )

  def forSync(zone: Zone, authPrincipal: AuthPrincipal): ZoneChange =
    ZoneChange(
      zone.copy(updated = Some(DateTime.now), status = ZoneStatus.Syncing),
      authPrincipal.userId,
      ZoneChangeType.Sync,
      ZoneChangeStatus.Pending
    )

  def forDelete(zone: Zone, authPrincipal: AuthPrincipal): ZoneChange =
    ZoneChange(
      zone.copy(updated = Some(DateTime.now), status = ZoneStatus.Deleted),
      authPrincipal.userId,
      ZoneChangeType.Delete,
      ZoneChangeStatus.Pending
    )

  private def fixConn(oldZ: Zone, newZ: Zone): Option[ZoneConnection] =
    newZ.connection.map(newConn => {
      val oldConn = oldZ.connection.getOrElse(newConn)
      newConn.copy(
        key =
          if (oldConn.key == newConn.decrypted(Crypto.instance).key) oldConn.key else newConn.key
      )
    })
}
