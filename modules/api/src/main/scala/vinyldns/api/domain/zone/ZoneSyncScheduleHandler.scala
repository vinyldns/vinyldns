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

import cats.effect.IO
import com.cronutils.model.CronType
import com.cronutils.model.definition.{CronDefinition, CronDefinitionBuilder}
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import org.slf4j.LoggerFactory
import vinyldns.core.domain.zone.{Zone, ZoneChange, ZoneRepository}
import java.time.{Instant, ZoneId}
import java.time.temporal.ChronoUnit

object ZoneSyncScheduleHandler {
  private val logger = LoggerFactory.getLogger("ZoneSyncScheduleHandler")

  def zoneSyncScheduler(zoneRepository: ZoneRepository): IO[Set[ZoneChange]] = {
    for {
      zones <- zoneRepository.getAllZonesWithSyncSchedule
      zoneScheduleIds = getZonesWithSchedule(zones.toList)
      zoneChanges <- getZoneChanges(zoneRepository, zoneScheduleIds)
    } yield zoneChanges
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
        if (diff == 1) {
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
