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

package vinyldns.api.domain.record

import java.util.UUID

import org.joda.time.DateTime
import vinyldns.api.domain.dns.DnsConversions.ensureTrailingDot
import vinyldns.api.domain.dns.DnsConversions

object RecordType extends Enumeration {
  type RecordType = Value
  val A, AAAA, CNAME, PTR, MX, NS, SOA, SRV, TXT, SSHFP, SPF, UNKNOWN = Value
}
object RecordSetStatus extends Enumeration {
  type RecordSetStatus = Value
  val Active, Inactive, Pending, PendingUpdate, PendingDelete = Value
}

sealed trait RecordData

final case class AData(address: String) extends RecordData

final case class AAAAData(address: String) extends RecordData

final case class CNAMEData(cname: String) extends RecordData

object CNAMEData {
  def apply(cname: String): CNAMEData =
    new CNAMEData(ensureTrailingDot(cname))
}

final case class MXData(preference: Integer, exchange: String) extends RecordData

object MXData {
  def apply(preference: Integer, exchange: String): MXData =
    new MXData(preference, ensureTrailingDot(exchange))
}

final case class NSData(nsdname: String) extends RecordData

object NSData {
  def apply(nsdname: String): NSData =
    new NSData(ensureTrailingDot(nsdname))
}

final case class PTRData(ptrdname: String) extends RecordData

object PTRData {
  def apply(ptrdname: String): PTRData =
    new PTRData(ensureTrailingDot(ptrdname))
}

final case class SOAData(
    mname: String,
    rname: String,
    serial: Long,
    refresh: Long,
    retry: Long,
    expire: Long,
    minimum: Long)
    extends RecordData

final case class SPFData(text: String) extends RecordData

final case class SRVData(priority: Integer, weight: Integer, port: Integer, target: String)
    extends RecordData

object SRVData {
  def apply(priority: Integer, weight: Integer, port: Integer, target: String): SRVData =
    new SRVData(priority, weight, port, ensureTrailingDot(target))
}

final case class SSHFPData(algorithm: Integer, typ: Integer, fingerprint: String) extends RecordData

final case class TXTData(text: String) extends RecordData

import RecordSetStatus._
import RecordType._

case class RecordSet(
    zoneId: String,
    name: String,
    typ: RecordType,
    ttl: Long,
    status: RecordSetStatus,
    created: DateTime,
    updated: Option[DateTime] = None,
    records: List[RecordData] = List.empty,
    id: String = UUID.randomUUID().toString,
    account: String = "system") {

  def isPending: Boolean =
    (status == RecordSetStatus.Pending
      || status == RecordSetStatus.PendingUpdate
      || status == RecordSetStatus.PendingDelete)

  override def toString: String = {

    val sb = new StringBuilder
    sb.append("RecordSet: [")
    sb.append("id=\"").append(id).append("\"; ")
    sb.append("name=\"").append(name).append("\"; ")
    sb.append("type=\"").append(typ.toString).append("\"; ")
    sb.append("ttl=\"").append(ttl.toString).append("\"; ")
    sb.append("account=\"").append(account).append("\"; ")
    sb.append("status=\"").append(status.toString).append("\"; ")
    sb.append("records=\"").append(records.toString).append("\"")
    sb.append("]")

    sb.toString
  }

  def matches(right: RecordSet, zoneName: String): Boolean = {

    val isSame = for {
      thisDnsRec <- DnsConversions.toDnsRecords(this, zoneName)
      otherDnsRec <- DnsConversions.toDnsRecords(right, zoneName)
    } yield {
      val rsMatch = otherDnsRec.toSet == thisDnsRec.toSet
      val namesMatch = matchesNameQualification(right)
      val headersMatch = this.ttl == right.ttl && this.typ == right.typ
      rsMatch && namesMatch && headersMatch
    }

    isSame.getOrElse(false)
  }

  def matchesNameQualification(right: RecordSet): Boolean =
    isFullyQualified(name) == isFullyQualified(right.name)

  def isFullyQualified(name: String): Boolean = name.endsWith(".")
}
