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

package vinyldns.route53.backend

import com.amazonaws.services.route53.model.{RRType, ResourceRecord, ResourceRecordSet}
import org.joda.time.DateTime
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.record.{
  AAAAData,
  AData,
  CNAMEData,
  MXData,
  NSData,
  PTRData,
  RecordData,
  RecordSet,
  RecordSetStatus,
  TXTData
}
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.zone.Zone

import scala.collection.JavaConverters._

trait Route53Conversions {

  def toRoute53RecordType(typ: RecordType): Option[RRType] = typ match {
    case A => Some(RRType.A)
    case AAAA => Some(RRType.AAAA)
    case CNAME => Some(RRType.CNAME)
    case TXT => Some(RRType.TXT)
    case NS => Some(RRType.NS)
    case PTR => Some(RRType.PTR)
    case MX => Some(RRType.MX)
    case _ => None
  }

  def toVinylRecordType(typ: RRType): RecordType = typ match {
    case RRType.A => A
    case RRType.AAAA => AAAA
    case RRType.CNAME => CNAME
    case RRType.TXT => TXT
    case RRType.NS => NS
    case RRType.PTR => PTR
    case RRType.MX => MX
    case _ => UNKNOWN
  }

  def toVinylA(r53: ResourceRecord): AData =
    AData(r53.getValue)

  def toVinylAAAA(r53: ResourceRecord): AAAAData =
    AAAAData(r53.getValue)

  def toVinylCNAME(r53: ResourceRecord): CNAMEData =
    CNAMEData(Fqdn(r53.getValue))

  def toVinylMX(r53: ResourceRecord): MXData = {
    // format is preference fqdn, ex. 10 mail.example.com
    val parts = r53.getValue.split(' ')
    MXData(parts(0).toInt, Fqdn(parts(1)))
  }

  def toVinylNS(r53: ResourceRecord): NSData =
    NSData(Fqdn(r53.getValue))

  def toVinylPTR(r53: ResourceRecord): PTRData =
    PTRData(Fqdn(r53.getValue))

  def toVinylTXT(r53: ResourceRecord): TXTData =
    TXTData(r53.getValue)

  def toVinyl(typ: RecordType, resourceRecord: ResourceRecord): Option[RecordData] = typ match {
    case A => Some(toVinylA(resourceRecord))
    case AAAA => Some(toVinylAAAA(resourceRecord))
    case CNAME => Some(toVinylCNAME(resourceRecord))
    case MX => Some(toVinylMX(resourceRecord))
    case NS => Some(toVinylNS(resourceRecord))
    case PTR => Some(toVinylPTR(resourceRecord))
    case TXT => Some(toVinylTXT(resourceRecord))
    case _ => None
  }

  def toVinylRecordSet(r53RecordSet: ResourceRecordSet): RecordSet = {
    val typ = toVinylRecordType(RRType.fromValue(r53RecordSet.getType))
    RecordSet(
      "unknown",
      r53RecordSet.getName,
      typ,
      r53RecordSet.getTTL,
      RecordSetStatus.Active,
      DateTime.now,
      Some(DateTime.now),
      r53RecordSet.getResourceRecords.asScala.toList.flatMap(toVinyl(typ, _))
    )
  }

  def toVinylRecordSets(r53RecordSets: java.util.List[ResourceRecordSet]): List[RecordSet] =
    r53RecordSets.asScala.toList.map(toVinylRecordSet)

  def toR53RecordSet(zone: Zone, vinylRecordSet: RecordSet): Option[ResourceRecordSet] =
    toRoute53RecordType(vinylRecordSet.typ).map { typ =>
      new ResourceRecordSet()
        .withName(fqdn(zone, vinylRecordSet))
        .withTTL(vinylRecordSet.ttl)
        .withType(typ)
        .withResourceRecords(
          vinylRecordSet.records.map(rd => new ResourceRecord().withValue(rd.toString)).asJava
        )
    }

  private def fqdn(zone: Zone, rs: RecordSet): String = {
    // make sure we combine the rs name and zone name to make an fqdn
    val zoneName = if (zone.name.endsWith(".")) zone.name else s"${zone.name}."
    if (rs.name.endsWith(".")) rs.name + zoneName else s"${rs.name}.$zoneName"
  }
}
