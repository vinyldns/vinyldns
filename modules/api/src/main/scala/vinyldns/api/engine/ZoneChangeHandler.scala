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
import vinyldns.core.domain.record.{RecordSetCacheRepository, RecordSetRepository}
import vinyldns.core.domain.zone._
import vinyldns.mysql.TransactionProvider

object ZoneChangeHandler {

  private implicit val logger: Logger = LoggerFactory.getLogger("vinyldns.engine.ZoneChangeHandler")

  def apply(
      zoneRepository: ZoneRepository,
      zoneChangeRepository: ZoneChangeRepository,
      recordSetRepository: RecordSetRepository,
      recordSetCacheRepository: RecordSetCacheRepository,
      transactions: TransactionProvider = ZoneSyncHandler
  ): ZoneChange => IO[ZoneChange] =
    zoneChange =>
      zoneRepository.getZone(zoneChange.zone.id).flatMap {
        case None if zoneChange.changeType == ZoneChangeType.Delete =>
          // A retry may arrive after deletion committed but before the change was acknowledged.
          zoneChangeRepository.save(zoneChange.copy(status = ZoneChangeStatus.Synced))
        case None =>
          zoneChangeRepository.save(
            zoneChange.copy(
              status = ZoneChangeStatus.Failed,
              systemMessage = Some(s"Zone ${zoneChange.zone.id} not found in repository")
            )
          )
        case Some(dbZone) if zoneChange.changeType == ZoneChangeType.Delete =>
          val deletedZone = dbZone.copy(status = ZoneStatus.Deleted)
          transactions.executeWithinTransaction { db: DB =>
            for {
              _ <- recordSetRepository.deleteRecordSetsInZone(db, dbZone.id, dbZone.name)
              _ <- recordSetCacheRepository.deleteRecordSetDataInZone(db, dbZone.id, dbZone.name)
            } yield ()
          }.flatMap { _ =>
            zoneRepository.save(deletedZone).flatMap {
              case Left(error) => IO.raiseError(new IllegalStateException(error.message))
              case Right(_) =>
                zoneChangeRepository.save(
                  zoneChange.copy(zone = deletedZone, status = ZoneChangeStatus.Synced)
                )
            }
          }
        case Some(_) =>
          // The authenticated command carries the updates authorized by ZoneService.
          zoneRepository.save(zoneChange.zone).flatMap {
            case Left(duplicateZoneError) =>
              zoneChangeRepository.save(
                zoneChange.copy(
                  status = ZoneChangeStatus.Failed,
                  systemMessage = Some(duplicateZoneError.message)
                )
              )
            case Right(_) =>
              logger.info(s"Saving zone change with id: '${zoneChange.id}', zone name: '${zoneChange.zone.name}'")
              zoneChangeRepository.save(zoneChange.copy(status = ZoneChangeStatus.Synced))
          }
      }
}
