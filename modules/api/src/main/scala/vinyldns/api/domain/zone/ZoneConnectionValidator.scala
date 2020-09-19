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
import cats.syntax.all._
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.Interfaces._
import vinyldns.api.VinylDNSConfig
import vinyldns.core.domain.backend.{BackendConnection, BackendRegistry}
import vinyldns.core.domain.record.{RecordSet, RecordType}
import vinyldns.core.domain.zone.Zone

import scala.concurrent.duration._

trait ZoneConnectionValidatorAlgebra {
  def validateZoneConnections(zone: Zone): Result[Unit]
  def isValidBackendId(backendId: Option[String]): Either[Throwable, Unit]
}

object ZoneConnectionValidator {

  val logger: Logger = LoggerFactory.getLogger(classOf[ZoneConnectionValidator])
}

class ZoneConnectionValidator(backendRegistry: BackendRegistry)
    extends ZoneConnectionValidatorAlgebra {

  import ZoneRecordValidations._

  // Takes a long time to load large zones
  val opTimeout: FiniteDuration = 60.seconds

  def loadDns(zone: Zone): IO[ZoneView] =
    DnsZoneViewLoader(zone, backendRegistry.connectTo(zone)).load()

  def hasApexNS(zoneView: ZoneView): Result[Unit] = {
    val apexRecord = zoneView.recordSetsMap.get(zoneView.zone.name, RecordType.NS) match {
      case Some(ns) => containsApprovedNameServers(VinylDNSConfig.approvedNameServers, ns)
      case None => "Missing apex NS record".invalidNel
    }

    apexRecord
      .map(_ => ())
      .leftMap(
        nel =>
          ZoneValidationFailed(
            zoneView.zone,
            nel.toList,
            "Zone could not be loaded due to validation errors."
          )
      )
      .toEither
      .toResult
  }

  def getBackendConnection(zone: Zone): Result[BackendConnection] =
    backendRegistry.connectTo(zone).toResult

  def loadZone(zone: Zone): Result[ZoneView] =
    withTimeout(
      loadDns(zone),
      opTimeout,
      ConnectionFailed(zone, "Unable to connect to zone: Transfer connection invalid")
    )

  def hasSOA(records: List[RecordSet], zone: Zone): Result[Unit] = {
    if (records.isEmpty) {
      ConnectionFailed(zone, "SOA Record for zone not found").asLeft[Unit]
    } else {
      ().asRight[Throwable]
    }
  }.toResult

  def validateZoneConnections(zone: Zone): Result[Unit] = {
    val result =
      for {
        connection <- getBackendConnection(zone)
        resp <- connection.resolve(zone.name, zone.name, RecordType.SOA).toResult[List[RecordSet]]
        view <- loadZone(zone)
        _ <- hasApexNS(view)
        _ <- hasSOA(resp, zone)
      } yield ()

    result.leftMap {
      case validationFailed: ZoneValidationFailed => validationFailed
      case connectionFailed: ConnectionFailed => connectionFailed
      case e => ConnectionFailed(zone, s"Unable to connect to zone: ${e.getMessage}")
    }
  }

  def isValidBackendId(backendId: Option[String]): Either[Throwable, Unit] =
    ensuring(InvalidRequest(s"Invalid backendId: [$backendId]; please check system configuration")) {
      backendId.forall(id => backendRegistry.isRegistered(id))
    }
}
