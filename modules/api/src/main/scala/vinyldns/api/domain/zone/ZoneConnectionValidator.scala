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
import cats.implicits._
import org.joda.time.DateTime
import vinyldns.api.Interfaces._
import vinyldns.api.VinylDNSConfig
import vinyldns.api.domain.dns.DnsConnection
import vinyldns.api.domain.dns.DnsProtocol.{NameNotFound, RecordSetNotFound}
import vinyldns.api.domain.record.{RecordSet, RecordSetChange, RecordSetStatus, RecordType, TXTData}

import scala.concurrent.duration._

trait ZoneConnectionValidatorAlgebra {
  def validateZoneConnections(zone: Zone): Result[Unit]
}

class ZoneConnectionValidator(defaultConnection: ZoneConnection)
    extends ZoneConnectionValidatorAlgebra {

  import ZoneRecordValidations._

  val opTimeout: FiniteDuration = 6.seconds

  def loadDns(zone: Zone): IO[ZoneView] = DnsZoneViewLoader(zone).load()

  def runZoneChecks(zoneView: ZoneView): Result[ZoneView] =
    validateDnsZone(VinylDNSConfig.approvedNameServers, zoneView.recordSetsMap.values.toList)
      .map(_ => zoneView)
      .leftMap(
        nel =>
          ZoneValidationFailed(
            zoneView.zone,
            nel.toList,
            "Zone could not be loaded due to validation errors."))
      .toEither
      .toResult

  def getDnsConnection(zone: Zone): Result[DnsConnection] =
    Either.catchNonFatal(dnsConnection(zone.connection.getOrElse(defaultConnection))).toResult

  def loadZone(zone: Zone): Result[ZoneView] =
    withTimeout(
      loadDns(zone),
      opTimeout,
      ConnectionFailed(zone, "Unable to connect to zone: Transfer connection invalid"))

  def hasSOA(records: List[RecordSet], zone: Zone): Result[Unit] = {
    if (records.isEmpty) {
      ConnectionFailed(zone, "SOA Record for zone not found").asLeft[Unit]
    } else {
      ().asRight[Throwable]
    }
  }.toResult

  def testDdnsConnectivity(dnsConnection: DnsConnection, zone: Zone): Result[Unit] = {
    val rs = RecordSet(
      zone.id,
      "vinyldns-ddns-connectivity-test",
      RecordType.TXT,
      86400,
      RecordSetStatus.Pending,
      DateTime.now,
      records = List(TXTData("connection test")))

    val result = for {
      _ <- dnsConnection
        .applyChange(RecordSetChange.forDelete(rs, zone))
        .map(_ => ())
        .recover {
          case _: RecordSetNotFound | _: NameNotFound => ()
        }
      _ <- dnsConnection.applyChange(RecordSetChange.forAdd(rs, zone))
    } yield ()

    result.leftMap {
      case e => ConnectionFailed(zone, s"Unable to test DDNS connectivity to zone: ${e.getMessage}")
    }
  }

  def validateZoneConnections(zone: Zone): Result[Unit] = {
    val result =
      for {
        connection <- getDnsConnection(zone)
        resp <- connection.resolve(zone.name, zone.name, RecordType.SOA)
        view <- loadZone(zone)
        _ <- runZoneChecks(view)
        _ <- hasSOA(resp, zone)
        _ <- testDdnsConnectivity(connection, zone)
      } yield ()

    result.leftMap {
      case validationFailed: ZoneValidationFailed => validationFailed
      case connectionFailed: ConnectionFailed => connectionFailed
      case e => ConnectionFailed(zone, s"Unable to connect to zone: ${e.getMessage}")
    }
  }

  private[domain] def dnsConnection(conn: ZoneConnection): DnsConnection = DnsConnection(conn)
}
