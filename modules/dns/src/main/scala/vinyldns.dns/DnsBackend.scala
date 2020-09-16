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

package vinyldns.dns

import cats.effect.IO
import org.xbill.DNS.{TSIG, ZoneTransferIn}
import vinyldns.core.domain.backend.{Backend, BackendResponse}
import vinyldns.core.domain.record.{RecordSet, RecordSetChange, RecordType}
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.zone.{Zone, ZoneConnection}

import scala.collection.JavaConverters._
import org.xbill._

class DnsBackend(zoneConnection: ZoneConnection, conn: DnsConnection)
    extends Backend
    with DnsConversions {

  /**
    * Does a lookup for a record given the record name, zone name, and record type
    *
    * The record name + zone name should form the FQDN
    *
    * @param name     The name of the record (without the zone - e.g. www)
    * @param zoneName The full domain name (e.g. example.com)
    * @param typ      The type of record (e.g. AAAA)
    * @return A list of record sets matching the name, empty if not found
    */
  def resolve(name: String, zoneName: String, typ: RecordType): IO[List[RecordSet]] =
    conn.resolve(name, zoneName, typ)

  /**
    * Applies a single record set change against the DNS backend
    *
    * @param change A RecordSetChange to apply.  Note: the key for a record set is the record name + type.
    *               A single RecordSetChange can add or remove multiple individual records in a record set at one time.
    * @return A BackendResponse that is backend provider specific
    */
  def applyChange(change: RecordSetChange): IO[BackendResponse] = conn.applyChange(change)

  /**
    * Loads all record sets in a zone.  Used typically for zone syncs.
    *
    * Note, this will cause memory issues for large zones (100,000s of records).  Need to make
    * zone sync memory safe before changing this
    *
    * @param zone The zone to load
    * @return All record sets in the zone
    */
  def loadZone(zone: Zone, maxZoneSize: Int): IO[List[RecordSet]] = {
    val xfrConn = zone.transferConnection.orElse(zone.connection).getOrElse(zoneConnection)
    val TSIGKey = new TSIG(xfrConn.keyName, xfrConn.key)
    val parts = xfrConn.primaryServer.trim().split(':')
    val (hostName, port) =
      if (parts.length < 2)
        (xfrConn.primaryServer, 53)
      else
        (parts(0), parts(1).toInt)

    val dnsZoneName = zoneDnsName(zone.name)
    val zti = ZoneTransferIn.newAXFR(dnsZoneName, hostName, port, TSIGKey)

    for {
      zoneXfr <- IO {
        zti.run()
        zti.getAXFR.asScala.map(_.asInstanceOf[DNS.Record]).toList.distinct
      }
      rawDnsRecords = zoneXfr.filter(
        record => fromDnsRecordType(record.getType) != RecordType.UNKNOWN
      )
      _ <- if (rawDnsRecords.length > maxZoneSize) {
        IO.raiseError(
          new RuntimeException(
            s"Zone too large ${zone.name}, ${rawDnsRecords.length} records exceeded max $maxZoneSize"
          )
        )
      } else {
        IO.pure(Unit)
      }
      dnsZoneName <- IO(zoneDnsName(zone.name))
      recordSets <- IO(rawDnsRecords.map(toRecordSet(_, dnsZoneName, zone.id)))
    } yield recordSets
  }
}
