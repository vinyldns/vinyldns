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

import akka.actor.Scheduler
import scalaz.\/
import scalaz.std.scalaFuture._
import vinyldns.api.Interfaces._
import vinyldns.api.VinylDNSConfig
import vinyldns.api.domain.dns.DnsConnection
import vinyldns.api.domain.record.{RecordSet, RecordType}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

trait ZoneConnectionValidatorAlgebra {
  def validateZoneConnections(zone: Zone): Result[Unit]
}

class ZoneConnectionValidator(defaultConnection: ZoneConnection, scheduler: Scheduler)(
    implicit ec: ExecutionContext)
    extends ZoneConnectionValidatorAlgebra {

  import ZoneRecordValidations._

  val futureTimeout: FiniteDuration = 6.seconds

  val approvedNameServers: List[Regex] = {
    val ns = VinylDNSConfig.vinyldnsConfig.getStringList("approved-name-servers").asScala.toList

    ns.map(n => n.r)
  }

  def loadDns(zone: Zone): Future[ZoneView] = DnsZoneViewLoader(zone).load()

  def runZoneChecks(zoneView: ZoneView): Result[ZoneView] =
    validateDnsZone(approvedNameServers, zoneView.recordSetsMap.values.toList)
      .map(_ => zoneView)
      .leftMap(
        nel =>
          ZoneValidationFailed(
            zoneView.zone,
            nel.list,
            "Zone could not be loaded due to validation errors."))
      .disjunction
      .toResult

  def getDnsConnection(zone: Zone): Result[DnsConnection] =
    \/.fromTryCatchNonFatal(dnsConnection(zone.connection.getOrElse(defaultConnection))).toResult

  def loadZone(zone: Zone): Result[ZoneView] =
    withTimeout(
      loadDns(zone),
      futureTimeout,
      ConnectionFailed(zone, "Unable to connect to zone: Transfer connection invalid"),
      scheduler)

  def hasSOA(records: List[RecordSet], zone: Zone): Result[Unit] = {
    if (records.isEmpty) {
      ConnectionFailed(zone, "SOA Record for zone not found").left[Unit]
    } else {
      ().right[Throwable]
    }
  }.toResult

  def validateZoneConnections(zone: Zone): Result[Unit] = {
    val result =
      for {
        connection <- getDnsConnection(zone)
        resp <- connection.resolve(zone.name, zone.name, RecordType.SOA)
        view <- loadZone(zone)
        _ <- runZoneChecks(view)
        _ <- hasSOA(resp, zone)
      } yield ()

    result.leftMap {
      case validationFailed: ZoneValidationFailed => validationFailed
      case connectionFailed: ConnectionFailed => connectionFailed
      case e => ConnectionFailed(zone, s"Unable to connect to zone: ${e.getMessage}")
    }
  }

  private[domain] def dnsConnection(conn: ZoneConnection): DnsConnection = DnsConnection(conn)
}
