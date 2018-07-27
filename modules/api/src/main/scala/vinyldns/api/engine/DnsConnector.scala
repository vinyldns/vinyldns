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
import scalaz.\/
import vinyldns.api.VinylDNSConfig
import vinyldns.api.domain.dns.DnsProtocol.DnsResponse
import vinyldns.api.domain.dns.{DnsConnection, DnsConversions}
import vinyldns.api.domain.record.RecordType.RecordType
import vinyldns.api.domain.record.{RecordSet, RecordSetChange}
import vinyldns.api.domain.zone.Zone

import scala.concurrent.ExecutionContext

/* Wraps DnsConnection things in IO */
case class DnsConnector(conn: DnsConnection) extends DnsConversions {
  def dnsResolve(name: String, zoneName: String, typ: RecordType)(
      implicit ec: ExecutionContext): IO[Throwable \/ List[RecordSet]] =
    IO.fromFuture(IO(conn.resolve(name, zoneName, typ).run))

  def dnsUpdate(change: RecordSetChange): IO[Throwable \/ DnsResponse] =
    IO.fromFuture(IO(conn.applyChange(change).run))
}

object DnsConnector {
  def apply(zone: Zone): DnsConnector =
    DnsConnector(DnsConnection(zone.connection.getOrElse(VinylDNSConfig.defaultZoneConnection)))
}
