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

import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc._
import vinyldns.api.backend.dns.DnsConversions
import vinyldns.api.domain.zone.{DnsZoneViewLoader, VinylDNSZoneViewLoader}
import vinyldns.core.domain.backend.BackendResolver
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.{Zone, ZoneStatus}
import vinyldns.core.route.Monitored
import vinyldns.core.domain.zone.{ZoneChange, ZoneChangeRepository, ZoneChangeStatus, ZoneRepository}

object ZoneSyncHandler extends DnsConversions with Monitored {

  private implicit val logger: Logger = LoggerFactory.getLogger("vinyldns.engine.ZoneSyncHandler")
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  def apply(
      recordSetRepository: RecordSetRepository,
      recordChangeRepository: RecordChangeRepository,
      zoneChangeRepository: ZoneChangeRepository,
      zoneRepository: ZoneRepository,
      backendResolver: BackendResolver,
      maxZoneSize: Int,
      vinyldnsLoader: (Zone, RecordSetRepository) => VinylDNSZoneViewLoader =
        VinylDNSZoneViewLoader.apply
  ): ZoneChange => IO[ZoneChange] =
    zoneChange =>
      for {
        _ <- saveZoneAndChange(zoneRepository, zoneChangeRepository, zoneChange) // initial save to store zone status
        // as Syncing
        syncChange <- runSync(
          recordSetRepository,
          recordChangeRepository,
          zoneChange,
          backendResolver,
          maxZoneSize,
          vinyldnsLoader
        )
        _ <- saveZoneAndChange(zoneRepository, zoneChangeRepository, syncChange) // final save to store zone status
        // as Active
      } yield syncChange

  def saveZoneAndChange(
      zoneRepository: ZoneRepository,
      zoneChangeRepository: ZoneChangeRepository,
      zoneChange: ZoneChange
  ): IO[ZoneChange] =
    zoneRepository.save(zoneChange.zone).flatMap {
      case Left(duplicateZoneError) =>
        zoneChangeRepository.save(
          zoneChange.copy(
            status = ZoneChangeStatus.Failed,
            systemMessage = Some(duplicateZoneError.message)
          )
        )
      case Right(_) =>
        zoneChangeRepository.save(zoneChange)
    }

  def executeWithinTransaction[A](execution: DB => A): A = {
    val db=DB(ConnectionPool.borrow())
    db.beginIfNotYet() // keep the connection open
    db.autoClose(false)
    try {
      execution(db)
    } catch {
      case error: Throwable =>
        throw error
    }
  }

  def runSync(
      recordSetRepository: RecordSetRepository,
      recordChangeRepository: RecordChangeRepository,
      zoneChange: ZoneChange,
      backendResolver: BackendResolver,
      maxZoneSize: Int,
      vinyldnsLoader: (Zone, RecordSetRepository) => VinylDNSZoneViewLoader =
        VinylDNSZoneViewLoader.apply
  ): IO[ZoneChange] =
    monitor("zone.sync") {
      time(s"zone.sync; zoneName='${zoneChange.zone.name}'") {
        val zone = zoneChange.zone
        val dnsLoader = DnsZoneViewLoader(zone, backendResolver.resolve(zone), maxZoneSize)
        val dnsView =
          time(
            s"zone.sync.loadDnsView; zoneName='${zone.name}'; zoneChange='${zoneChange.id}'"
          )(dnsLoader.load())
        val vinyldnsView = time(s"zone.sync.loadVinylDNSView; zoneName='${zone.name}'")(
          vinyldnsLoader(zone, recordSetRepository).load()
        )
        val recordSetChanges = (dnsView, vinyldnsView).parTupled.map {
          case (dnsZoneView, vinylDnsZoneView) => vinylDnsZoneView.diff(dnsZoneView)
        }

        recordSetChanges.flatMap { allChanges =>
          val changesWithUserIds = allChanges.map(_.withUserId(zoneChange.userId))

          if (changesWithUserIds.isEmpty) {
            logger.info(
              s"zone.sync.changes; zoneName='${zone.name}'; changeCount=0; zoneChange='${zoneChange.id}'"
            )
            IO.pure(
              zoneChange.copy(
                zone.copy(status = ZoneStatus.Active, latestSync = Some(DateTime.now)),
                status = ZoneChangeStatus.Synced
              )
            )
          } else {
            changesWithUserIds
              .filter { chg =>
                chg.recordSet.name != zone.name && chg.recordSet.name.contains(".") &&
                chg.recordSet.typ != RecordType.SRV && chg.recordSet.typ != RecordType.TXT &&
                chg.recordSet.typ != RecordType.NAPTR
              }
              .map(_.recordSet.name)
              .grouped(1000)
              .foreach { dottedGroup =>
                val dottedGroupString = dottedGroup.mkString(", ")
                logger.info(
                  s"Zone sync for zoneName='${zone.name}'; zoneId='${zone.id}'; " +
                    s"zoneChange='${zoneChange.id}' includes the following ${dottedGroup.length} " +
                    s"dotted host records: [$dottedGroupString]"
                )
              }

            logger.info(
              s"zone.sync.changes; zoneName='${zone.name}'; " +
                s"changeCount=${changesWithUserIds.size}; zoneChange='${zoneChange.id}'"
            )
            val changeSet = ChangeSet(changesWithUserIds).copy(status = ChangeSetStatus.Applied)
            // we want to make sure we write to both the change repo and record set repo
            // at the same time as this can take a while
            executeWithinTransaction { db: DB =>
              // keep the connection open
              {
                val saveRecordChanges = time(s"zone.sync.saveChanges; zoneName='${zone.name}'")(
                  recordChangeRepository.save(db, changeSet)
                )
                val saveRecordSets = time(s"zone.sync.saveRecordSets; zoneName='${zone.name}'")(
                  recordSetRepository.apply(db,changeSet)
                )
                for {
                  _ <- saveRecordChanges
                  _ <- saveRecordSets
                } yield {
                  zoneChange.copy(
                    zone.copy(status = ZoneStatus.Active, latestSync = Some(DateTime.now)),
                    status = ZoneChangeStatus.Synced
                  )
                }
              }.attempt.map {
                case Left(e: Throwable) =>
                  db.rollbackIfActive() //Roll back the changes if error occurs
                  db.close() //DB Connection Close
                  throw e
                case Right(ok) =>
                  db.commit() //commit the changes
                  db.close() //DB Connection Close
                  ok
              }
            }
          }
        }
      }
    }.attempt
      .map {
        case Left(e: Throwable) =>
          logger.error(
            s"Encountered error syncing ; zoneName='${zoneChange.zone.name}'; zoneChange='${zoneChange.id}'",
            e
          )
          // We want to just move back to an active status, do not update latest sync
          zoneChange.copy(
            zone = zoneChange.zone.copy(status = ZoneStatus.Active),
            status = ZoneChangeStatus.Failed
          )
        case Right(ok) => ok
      }
}