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
import java.time.temporal.ChronoUnit
import java.time.Instant
import vinyldns.core.crypto.CryptoAlgebra
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
        .copy(id = UUID.randomUUID().toString, created = Instant.now.truncatedTo(ChronoUnit.MILLIS), status = ZoneStatus.Syncing),
      authPrincipal.userId,
      ZoneChangeType.Create,
      status
    )

  def forUpdate(
      newZone: Zone,
      oldZone: Zone,
      authPrincipal: AuthPrincipal,
      crypto: CryptoAlgebra
  ): ZoneChange =
    ZoneChange(
      newZone.copy(
        updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)),
        connection = fixConn(oldZone.connection, newZone.connection, crypto),
        transferConnection =
          fixConn(oldZone.transferConnection, newZone.transferConnection, crypto)
      ),
      authPrincipal.userId,
      ZoneChangeType.Update,
      ZoneChangeStatus.Pending
    )

  def forSync(zone: Zone, authPrincipal: AuthPrincipal): ZoneChange =
    ZoneChange(
      zone.copy(updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)), status = ZoneStatus.Syncing),
      authPrincipal.userId,
      ZoneChangeType.Sync,
      ZoneChangeStatus.Pending
    )

  def forSyncs(zone: Zone): ZoneChange =
    ZoneChange(
      zone.copy(updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)), status = ZoneStatus.Syncing),
      zone.scheduleRequestor.get,
      ZoneChangeType.AutomatedSync,
      ZoneChangeStatus.Pending
    )

  def forDelete(zone: Zone, authPrincipal: AuthPrincipal): ZoneChange =
    ZoneChange(
      zone.copy(updated = Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)), status = ZoneStatus.Deleted),
      authPrincipal.userId,
      ZoneChangeType.Delete,
      ZoneChangeStatus.Pending
    )

  // If a client echoes back the stored ciphertext, the route re-encrypts it; keep the stored key.
  // Skip the decrypt when keys already match (e.g. the redacted key was resolved in Zone.apply).
  private def fixConn(
      oldConn: Option[ZoneConnection],
      newConn: Option[ZoneConnection],
      crypto: CryptoAlgebra
  ): Option[ZoneConnection] =
    newConn.map { nc =>
      val oc = oldConn.getOrElse(nc)
      if (oc.key != nc.key && oc.key == nc.decrypted(crypto).key) nc.copy(key = oc.key) else nc
    }
}
