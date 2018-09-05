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
import org.xbill.DNS
import org.xbill.DNS.{TSIG, ZoneTransferIn}
import vinyldns.api.VinylDNSConfig
import vinyldns.api.crypto.Crypto
import vinyldns.api.domain.dns.DnsConversions
import vinyldns.core.domain.record.RecordSetRepository
import vinyldns.core.domain.zone.Zone
import vinyldns.api.route.Monitored

import scala.collection.JavaConverters._

trait ZoneViewLoader {
  def load: () => IO[ZoneView]
}

object DnsZoneViewLoader extends DnsConversions {

  def dnsZoneTransfer(zone: Zone): ZoneTransferIn = {
    val conn =
      zone.transferConnection
        .getOrElse(VinylDNSConfig.defaultTransferConnection)
        .decrypted(Crypto.instance)
    val TSIGKey = new TSIG(conn.keyName, conn.key)
    val parts = conn.primaryServer.trim().split(':')
    val (hostName, port) =
      if (parts.length < 2)
        (conn.primaryServer, 53)
      else
        (parts(0), parts(1).toInt)

    val dnsZoneName = zoneDnsName(zone.name)
    ZoneTransferIn.newAXFR(dnsZoneName, hostName, port, TSIGKey)
  }

  def apply(zone: Zone): DnsZoneViewLoader =
    DnsZoneViewLoader(zone, dnsZoneTransfer)
}

case class DnsZoneViewLoader(zone: Zone, zoneTransfer: Zone => ZoneTransferIn)
    extends ZoneViewLoader
    with DnsConversions
    with Monitored {

  def load: () => IO[ZoneView] =
    () =>
      monitor("dns.loadZoneView") {
        IO {
          val xfr = zoneTransfer(zone)
          xfr.run()

          val rawDnsRecords: List[DNS.Record] =
            xfr.getAXFR.asScala.map(_.asInstanceOf[DNS.Record]).toList.distinct

          val dnsZoneName = zoneDnsName(zone.name)
          val recordSets = rawDnsRecords.map(toRecordSet(_, dnsZoneName, zone.id))

          ZoneView(zone, recordSets)
        }
    }
}

case class VinylDNSZoneViewLoader(zone: Zone, recordSetRepository: RecordSetRepository)
    extends ZoneViewLoader
    with Monitored {
  def load: () => IO[ZoneView] =
    () =>
      monitor("vinyldns.loadZoneView") {
        recordSetRepository
          .listRecordSets(
            zoneId = zone.id,
            startFrom = None,
            maxItems = None,
            recordNameFilter = None)
          .map(result => ZoneView(zone, result.recordSets))
    }
}
