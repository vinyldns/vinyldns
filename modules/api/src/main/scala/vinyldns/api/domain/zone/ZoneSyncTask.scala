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

import cats.data.NonEmptyList
import cats.effect.IO
import com.cronutils.model.CronType
import com.cronutils.model.definition.{CronDefinition, CronDefinitionBuilder}
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.domain.zone.{Zone, ZoneChange, ZoneRepository}
import vinyldns.core.task.Task
import vinyldns.core.queue.MessageQueue
import java.time.{Instant, ZoneId}
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class ZoneSyncTask(
                    zoneRepository: ZoneRepository,
                    messageQueue: MessageQueue,
                    val runEvery: FiniteDuration = 1.seconds,
                    val timeout: FiniteDuration = 1.minutes,
                    val checkInterval: FiniteDuration = 1.second
                  ) extends Task {
  val name: String = "zone_sync"
  private val logger: Logger = LoggerFactory.getLogger("ZoneSyncTask")

  def run(): IO[Unit] = {
    logger.debug("Initiating zone sync")
    for {
      zones <- zoneRepository.getAllZonesWithSyncSchedule
      zoneScheduleIds = getZonesWithSchedule(zones.toList)
      zoneChanges <- getZoneChanges(zoneRepository, zoneScheduleIds)
      _ <- if (zoneChanges.nonEmpty) messageQueue.sendBatch(NonEmptyList.fromList(zoneChanges.toList).get) else IO.unit
      _ <- IO(logger.debug(s"""zones synced="${zoneChanges.map(_.zone.name)}"; zoneSyncCount="${zoneChanges.size}" """))
    } yield ()
  }

  def getZoneChanges(zoneRepository: ZoneRepository, zoneScheduleIds: List[String]): IO[Set[ZoneChange]] = {
    if(zoneScheduleIds.nonEmpty) {
      for{
        getZones <- zoneRepository.getZones(zoneScheduleIds.toSet)
        syncZoneChange = getZones.map(zone => ZoneChangeGenerator.forSyncs(zone))
      } yield syncZoneChange
    } else {
      IO(Set.empty)
    }
  }

  def getZonesWithSchedule(zone: List[Zone]): List[String] = {
    var zonesWithSchedule: List[String] = List.empty
    for(z <- zone) {
      if (z.recurrenceSchedule.isDefined) {
        val now = Instant.now().atZone(ZoneId.of("UTC"))
        val cronDefinition: CronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
        val parser: CronParser = new CronParser(cronDefinition)
        val executionTime: ExecutionTime = ExecutionTime.forCron(parser.parse(z.recurrenceSchedule.get))
        val nextExecution = executionTime.nextExecution(now).get()
        val diff = ChronoUnit.SECONDS.between(now, nextExecution)
        // Diff is always 1 or 2 as the task scheduler runs every second with 1 second interval
        if (diff == 2 || diff == 1) {
          zonesWithSchedule = zonesWithSchedule :+ z.id
          logger.info("Zones with sync schedule: " + zonesWithSchedule)
        } else {
          List.empty
        }
      } else {
        List.empty
      }
    }
    zonesWithSchedule
  }
}
