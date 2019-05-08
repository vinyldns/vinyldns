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
import vinyldns.core.domain.record.RecordSetRepository
import vinyldns.core.domain.zone._

object ZoneChangeHandler {
  private implicit val logger: Logger = LoggerFactory.getLogger("vinyldns.engine.ZoneChangeHandler")

  def apply(
      zoneRepository: ZoneRepository,
      zoneChangeRepository: ZoneChangeRepository,
      recordSetRepository: RecordSetRepository): ZoneChange => IO[ZoneChange] =
    zoneChange =>
      zoneRepository.save(zoneChange.zone).flatMap {
        case Left(duplicateZoneError) =>
          zoneChangeRepository.save(
            zoneChange.copy(
              status = ZoneChangeStatus.Failed,
              systemMessage = Some(duplicateZoneError.message))
          )
        case Right(_) if zoneChange.changeType == ZoneChangeType.Delete =>
          recordSetRepository
            .deleteRecordSetsInZone(zoneChange.zone.id, zoneChange.zone.name)
            .attempt
            .map {
              case Left(e: Throwable) =>
                logger.error(
                  s"""Encountered error deleting recordsets from deleted
                       |zone ${zoneChange.zone.name} (zone id: ${zoneChange.zone.id})""".stripMargin
                    .replaceAll("\n", " "),
                  e
                )
                zoneChangeRepository.save(zoneChange.copy(status = ZoneChangeStatus.Synced))

              case Right(_) =>
                zoneChangeRepository.save(zoneChange.copy(status = ZoneChangeStatus.Synced))
            }
          zoneChangeRepository.save(zoneChange.copy(status = ZoneChangeStatus.Synced))
        case Right(_) =>
          zoneChangeRepository.save(zoneChange.copy(status = ZoneChangeStatus.Synced))
    }
}
