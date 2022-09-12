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

import cats.effect._
import org.slf4j.LoggerFactory
import vinyldns.api.backend.dns.DnsConversions
import vinyldns.core.domain.backend.Backend
import vinyldns.core.domain.record.{NameSort, RecordSetCacheRepository, RecordSetRepository, RecordTypeSort}
import vinyldns.core.domain.zone.Zone
import vinyldns.core.route.Monitored

trait ZoneViewLoader {
  def load: () => IO[ZoneView]
}

object DnsZoneViewLoader extends DnsConversions {
  val logger = LoggerFactory.getLogger("DnsZoneViewLoader")
}

case class DnsZoneViewLoader(
    zone: Zone,
    backendConnection: Backend,
    maxZoneSize: Int
) extends ZoneViewLoader
    with DnsConversions
    with Monitored {

  def load: () => IO[ZoneView] =
    () =>
      monitor("dns.loadZoneView") {
        for {
          recordSets <- backendConnection.loadZone(zone, maxZoneSize)
        } yield ZoneView(zone, recordSets)
      }
}

object VinylDNSZoneViewLoader {
  val logger = LoggerFactory.getLogger("VinylDNSZoneViewLoader")
}
case class VinylDNSZoneViewLoader(
    zone: Zone,
    recordSetRepository: RecordSetRepository,
    recordSetCacheRepository: RecordSetCacheRepository
) extends ZoneViewLoader
    with Monitored {
  def load: () => IO[ZoneView] =
    () =>
      monitor("vinyldns.loadZoneView") {
        recordSetRepository
          .listRecordSets(
            zoneId = Some(zone.id),
            startFrom = None,
            maxItems = None,
            recordNameFilter = None,
            recordTypeFilter = None,
            recordOwnerGroupFilter = None,
            nameSort = NameSort.ASC,
            recordTypeSort = RecordTypeSort.ASC
          )
          .map { result =>
            VinylDNSZoneViewLoader.logger.info(
              s"vinyldns.loadZoneView zoneName=${zone.name}; rsCount=${result.recordSets.size}"
            )
            ZoneView(zone, result.recordSets)
          }
      }
}
