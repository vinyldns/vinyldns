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
import cats.syntax.all._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import vinyldns.api.domain.dns.DnsConversions
import vinyldns.api.domain.zone.{DnsZoneViewLoader, VinylDNSZoneViewLoader}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{Zone, ZoneChange, ZoneChangeStatus, ZoneStatus}
import vinyldns.api.route.Monitored

object ZoneSyncHandler extends DnsConversions with Monitored {

  private implicit val logger = LoggerFactory.getLogger("vinyldns.engine.ZoneSyncHandler")

  def apply(
      recordSetRepository: RecordSetRepository,
      recordChangeRepository: RecordChangeRepository,
      dnsLoader: Zone => DnsZoneViewLoader = DnsZoneViewLoader.apply,
      vinyldnsLoader: (Zone, RecordSetRepository) => VinylDNSZoneViewLoader =
        VinylDNSZoneViewLoader.apply): ZoneChange => IO[ZoneChange] =
    zoneChange =>
      monitor("zone.sync") {
        time(s"zone.sync(${zoneChange.zoneId})") {
          val zone = zoneChange.zone

          val dnsView = time(s"zone.sync.loadDnsView(${zoneChange.id})")(dnsLoader(zone).load())
          val vinyldnsView = time(s"zone.sync.loadVinylDNSView(${zoneChange.id})")(
            vinyldnsLoader(zone, recordSetRepository).load())
          val recordSetChanges = (dnsView, vinyldnsView).parTupled.map {
            case (dnsZoneView, vinylDnsZoneView) => vinylDnsZoneView.diff(dnsZoneView)
          }

          recordSetChanges.flatMap { allChanges =>
            val changesWithUserIds = allChanges.map(_.withUserId(zoneChange.userId))
            // not accepting unknown record types or dotted hosts
            val (changes, dropped) = changesWithUserIds.partition { rs =>
              rs.recordSet.typ != RecordType.UNKNOWN
            }

            if (dropped.nonEmpty) {
              val droppedInfo = dropped
                .map(chg => chg.recordSet.name + " " + chg.recordSet.typ)
                .mkString(", ")
              logger.warn(
                s"Zone sync for change $zoneChange dropped ${dropped.size} recordsets: $droppedInfo")
            }

            if (changes.isEmpty) {
              logger.info(s"Zone sync for change $zoneChange had no records to sync")
              IO.pure(
                zoneChange.copy(
                  zone.copy(status = ZoneStatus.Active, latestSync = Some(DateTime.now))))
            } else {
              logger.info(
                s"Zone sync for change $zoneChange found ${changes.size} changes to be saved")
              val changeSet = ChangeSet(changes).copy(status = ChangeSetStatus.Applied)

              // we want to make sure we write to both the change repo and record set repo
              // at the same time as this can take a while
              val saveRecordChanges = time(s"zone.sync.saveChanges(${zoneChange.zoneId})")(
                recordChangeRepository.save(changeSet))
              val saveRecordSets = time(s"zone.sync.saveRecordSets(${zoneChange.zoneId})")(
                recordSetRepository.apply(changeSet))

              // join together the results of saving both the record changes as well as the record sets
              for {
                _ <- saveRecordChanges
                _ <- saveRecordSets
              } yield
                zoneChange.copy(
                  zone.copy(status = ZoneStatus.Active, latestSync = Some(DateTime.now)))
            }
          }
        }
      }.attempt
        .map {
          case Left(e: Throwable) =>
            logger.error(s"Encountered error syncing zone ${zoneChange.zone.name}", e)
            // We want to just move back to an active status, do not update latest sync
            zoneChange.copy(
              zone = zoneChange.zone.copy(status = ZoneStatus.Active),
              status = ZoneChangeStatus.Failed)
          case Right(ok) => ok
      }
}
