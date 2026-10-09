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

package vinyldns.api.engine

import cats.effect.IO
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc.DB
import vinyldns.api.engine.ZoneSyncHandler.executeWithinTransaction
import vinyldns.core.domain.record.{RecordSetCacheRepository, RecordSetRepository}
import vinyldns.core.domain.zone._

object ZoneChangeHandler {

  private implicit val logger: Logger = LoggerFactory.getLogger("vinyldns.engine.ZoneChangeHandler")

  def apply(
             zoneRepository: ZoneRepository,
             zoneChangeRepository: ZoneChangeRepository,
             recordSetRepository: RecordSetRepository,
             recordSetCacheRepository: RecordSetCacheRepository,

  ): ZoneChange => IO[ZoneChange] =
    zoneChange =>
      // Load authoritative zone configuration from repository before processing
      zoneRepository.getZone(zoneChange.zone.id).flatMap {
        case None =>
          zoneChangeRepository.save(
            zoneChange.copy(
              status = ZoneChangeStatus.Failed,
              systemMessage = Some(s"Zone ${zoneChange.zone.id} not found in repository")
            )
          )
        case Some(dbZone) if zoneChange.changeType == ZoneChangeType.Delete =>
          // Use authoritative zone configuration for deletion scope
          zoneRepository.save(dbZone).flatMap { _ =>
            executeWithinTransaction { db: DB =>
              for {
                _ <- recordSetRepository
                  .deleteRecordSetsInZone(db, dbZone.id, dbZone.name)
                _ <- recordSetCacheRepository
                  .deleteRecordSetDataInZone(db, dbZone.id, dbZone.name)
              } yield ()
            }.attempt.flatMap { _ =>
              zoneChangeRepository.save(zoneChange.copy(status = ZoneChangeStatus.Synced))
            }
          }
        case Some(dbZone) =>
          // Preserve authoritative zone properties for ACL and ownership integrity
          val trustedZone = zoneChange.zone.copy(
            adminGroupId = dbZone.adminGroupId,
            acl = dbZone.acl,
            shared = dbZone.shared
          )
          zoneRepository.save(trustedZone).flatMap {
            case Left(duplicateZoneError) =>
              zoneChangeRepository.save(
                zoneChange.copy(
                  status = ZoneChangeStatus.Failed,
                  systemMessage = Some(duplicateZoneError.message)
                )
              )
            case Right(_) =>
              logger.info(s"Saving zone change with id: '${zoneChange.id}', zone name: '${zoneChange.zone.name}'")
              zoneChangeRepository.save(zoneChange.copy(zone = trustedZone, status = ZoneChangeStatus.Synced))
          }
      }
}
