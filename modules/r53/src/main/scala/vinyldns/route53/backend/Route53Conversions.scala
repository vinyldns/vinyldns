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

import com.amazonaws.services.route53.model.{
  DelegationSet,
  RRType,
  ResourceRecord,
  ResourceRecordSet
}
import java.time.temporal.ChronoUnit
import java.time.Instant
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.record.{NSData, OwnershipTransfer, OwnershipTransferStatus, RecordData, RecordSet, RecordSetStatus, RecordType}
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.zone.Zone

import scala.collection.JavaConverters._

trait Route53Conversions {

  def toRoute53RecordType(typ: RecordType): Option[RRType] = typ match {
    case A => Some(RRType.A)
    case AAAA => Some(RRType.AAAA)
    case CNAME => Some(RRType.CNAME)
    case MX => Some(RRType.MX)
    case NAPTR => Some(RRType.NAPTR)
    case NS => Some(RRType.NS)
    case PTR => Some(RRType.PTR)
    case SPF => Some(RRType.SPF)
    case SRV => Some(RRType.SRV)
    case TXT => Some(RRType.TXT)
    case SOA => Some(RRType.SOA)
    case _ => None
  }

  def toVinylRecordType(typ: RRType): RecordType = typ match {
    case RRType.A => A
    case RRType.AAAA => AAAA
    case RRType.CNAME => CNAME
    case RRType.MX => MX
    case RRType.NAPTR => NAPTR
    case RRType.NS => NS
    case RRType.PTR => PTR
    case RRType.SPF => SPF
    case RRType.SRV => SRV
    case RRType.TXT => TXT
    case RRType.SOA => SOA
    case _ => UNKNOWN
  }

  def toVinyl(typ: RecordType, resourceRecord: ResourceRecord): Option[RecordData] =
    RecordData.fromString(resourceRecord.getValue, typ)

  def toVinylRecordSet(
      zoneName: String,
      zoneId: String,
      r53RecordSet: ResourceRecordSet
  ): RecordSet = {
    val typ = toVinylRecordType(RRType.fromValue(r53RecordSet.getType))
    RecordSet(
      zoneId,
      Fqdn.merge(r53RecordSet.getName, zoneName).zoneRecordName(zoneName),
      typ,
      r53RecordSet.getTTL,
      RecordSetStatus.Active,
      Instant.now.truncatedTo(ChronoUnit.MILLIS),
      Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)),
      r53RecordSet.getResourceRecords.asScala.toList.flatMap(toVinyl(typ, _)),
      recordSetGroupChange=Some(OwnershipTransfer(ownershipTransferStatus = OwnershipTransferStatus.AutoApproved)),
      fqdn = Some(r53RecordSet.getName)
    )
  }

  def toVinylRecordSets(
      r53RecordSets: java.util.List[ResourceRecordSet],
      zoneName: String,
      zoneId: String = "unknown"
  ): List[RecordSet] =
    r53RecordSets.asScala.toList.map(toVinylRecordSet(zoneName, zoneId, _))

  def toVinylNSRecordSet(
      delegationSet: DelegationSet,
      zoneName: String,
      zoneId: String
  ): RecordSet = {
    val nsData = delegationSet.getNameServers.asScala.toList.map { ns =>
      NSData(Fqdn(ns))
    }
    RecordSet(
      zoneId,
      zoneName,
      RecordType.NS,
      7200,
      RecordSetStatus.Active,
      Instant.now.truncatedTo(ChronoUnit.MILLIS),
      Some(Instant.now.truncatedTo(ChronoUnit.MILLIS)),
      nsData,
      recordSetGroupChange = Some(OwnershipTransfer(ownershipTransferStatus = OwnershipTransferStatus.AutoApproved)),
      fqdn = Some(Fqdn(zoneName).fqdn)
    )
  }

  def toR53RecordSet(zone: Zone, vinylRecordSet: RecordSet): Option[ResourceRecordSet] =
    toRoute53RecordType(vinylRecordSet.typ).map { typ =>
      new ResourceRecordSet()
        .withName(Fqdn.merge(vinylRecordSet.name, zone.name).fqdn)
        .withTTL(vinylRecordSet.ttl)
        .withType(typ)
        .withResourceRecords(
          vinylRecordSet.records.map(rd => new ResourceRecord().withValue(rd.toString)).asJava
        )
    }
}
