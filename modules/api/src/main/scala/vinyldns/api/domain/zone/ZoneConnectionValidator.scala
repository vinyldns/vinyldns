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

import java.net.{InetSocketAddress, Socket}

import cats.effect._
import cats.syntax.all._
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.Interfaces._
import vinyldns.api.VinylDNSConfig
import vinyldns.api.domain.dns.DnsConnection
import vinyldns.core.domain.record.{RecordSet, RecordType}
import vinyldns.core.domain.zone.{ConfiguredDnsConnections, DnsBackend, Zone, ZoneConnection}
import vinyldns.core.health.HealthCheck._

import scala.concurrent.duration._

trait ZoneConnectionValidatorAlgebra {

  def validateZoneConnections(zone: Zone): Result[Unit]
  def isValidBackendId(backendId: Option[String]): Either[Throwable, Unit]

}

object ZoneConnectionValidator {

  val logger: Logger = LoggerFactory.getLogger(classOf[ZoneConnectionValidator])

  def getZoneConnection(
      zone: Zone,
      configuredDnsConnections: ConfiguredDnsConnections
  ): ZoneConnection =
    zone.connection
      .orElse(getDnsBackend(zone, configuredDnsConnections).map(_.zoneConnection))
      .getOrElse(configuredDnsConnections.defaultZoneConnection)

  def getTransferConnection(
      zone: Zone,
      configuredDnsConnections: ConfiguredDnsConnections
  ): ZoneConnection =
    zone.transferConnection
      .orElse(getDnsBackend(zone, configuredDnsConnections).map(_.transferConnection))
      .getOrElse(configuredDnsConnections.defaultTransferConnection)

  def getDnsBackend(
      zone: Zone,
      configuredDnsConnections: ConfiguredDnsConnections
  ): Option[DnsBackend] =
    zone.backendId
      .flatMap { bid =>
        val backend = configuredDnsConnections.dnsBackends.find(_.id == bid)
        if (backend.isEmpty) {
          logger.error(
            s"BackendId [$bid] for zone [${zone.id}: ${zone.name}] is not defined in config"
          )
        }
        backend
      }
}

class ZoneConnectionValidator(connections: ConfiguredDnsConnections)
    extends ZoneConnectionValidatorAlgebra {

  import ZoneConnectionValidator._
  import ZoneRecordValidations._

  // Takes a long time to load large zones
  val opTimeout: FiniteDuration = 60.seconds

  val (healthCheckAddress, healthCheckPort) =
    DnsConnection.parseHostAndPort(connections.defaultZoneConnection.primaryServer)

  def loadDns(zone: Zone): IO[ZoneView] = DnsZoneViewLoader(zone).load()

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

  def getDnsConnection(zone: Zone): Result[DnsConnection] =
    Either.catchNonFatal(dnsConnection(getZoneConnection(zone, connections))).toResult

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
        connection <- getDnsConnection(zone)
        resp <- connection.resolve(zone.name, zone.name, RecordType.SOA)
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

  def healthCheck(timeout: Int): HealthCheck =
    Resource
      .fromAutoCloseable(IO(new Socket()))
      .use(
        socket =>
          IO(socket.connect(new InetSocketAddress(healthCheckAddress, healthCheckPort), timeout))
      )
      .attempt
      .asHealthCheck

  def isValidBackendId(backendId: Option[String]): Either[Throwable, Unit] =
    ensuring(InvalidRequest(s"Invalid backendId: [$backendId]; please check system configuration")) {
      backendId.forall(id => connections.dnsBackends.exists(_.id == id))
    }

  private[domain] def dnsConnection(conn: ZoneConnection): DnsConnection = DnsConnection(conn)
}
