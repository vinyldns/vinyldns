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

package vinyldns.api.domain.dns

import java.net.InetAddress

import cats.syntax.either._
import org.joda.time.DateTime
import org.xbill.DNS
import scodec.bits.ByteVector
import vinyldns.api.domain.dns.DnsProtocol._
import vinyldns.core.domain.{DomainHelpers, record}
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record._

import scala.collection.JavaConverters._
import scala.util.Try

class BadDNSRecordException(message: String) extends Exception(message) {}

trait DnsConversions {

  import DomainHelpers._

  /**
    * record names are either relative to the zone name or
    * if the record name is '@' the zone name is used
    */
  def relativize(r: DNS.Name, zoneName: DNS.Name): String =
    if (r.equals(zoneName))
      zoneName.toString
    else {
      r.relativize(zoneName).toString
    }

  def relativize(recordName: String, zoneName: String): String =
    if (recordName == "@" || omitTrailingDot(recordName) == omitTrailingDot(zoneName))
      zoneDnsName(zoneName).toString
    else
      DNS.Name.fromString(recordName).relativize(zoneDnsName(zoneName)).toString

  def getZoneFromNonApexFqdn(domainName: String): String =
    domainName.substring(domainName.indexOf(".") + 1)

  def getIPv4FullReverseName(ip: String): Option[String] =
    Try(DNS.ReverseMap.fromAddress(ip, DNS.Address.IPv4)).toOption.map(_.toString)

  /*
     will get the base zone name for filtering; no guarantee that this is the actual zone name, because there could
     be classless reverse zone delegation happening
   */
  def getIPv4NonDelegatedZoneName(ip: String): Option[String] = getIPv4FullReverseName(ip).map {
    reverseName =>
      reverseName.split('.').drop(1).mkString(".") + "."
  }

  def getIPv6FullReverseName(ip: String): Option[String] =
    Try(DNS.ReverseMap.fromAddress(ip, DNS.Address.IPv6)).toOption.map(_.toString)

  def recordDnsName(recordName: String, zoneName: String): DNS.Name =
    if (omitTrailingDot(recordName) == omitTrailingDot(zoneName))
      zoneDnsName(zoneName)
    else
      DNS.Name.fromString(recordName, zoneDnsName(zoneName))

  def zoneDnsName(origin: String): DNS.Name =
    if (origin.endsWith("."))
      DNS.Name.fromString(origin)
    else
      DNS.Name.fromString(s"$origin.")

  def fromDnsRcodeToError[T](rCode: Int, message: String): Either[Throwable, T] = rCode match {
    case DNS.Rcode.BADKEY => Left(BadKey(message))
    case DNS.Rcode.BADMODE => Left(BadMode(message))
    case DNS.Rcode.BADSIG => Left(BadSig(message))
    case DNS.Rcode.BADTIME => Left(BadTime(message))
    case DNS.Rcode.FORMERR => Left(FormatError(message))
    case DNS.Rcode.NOTAUTH => Left(NotAuthorized(message))
    case DNS.Rcode.NOTIMP => Left(NotImplemented(message))
    case DNS.Rcode.NOTZONE => Left(NotZone(message))
    case DNS.Rcode.NXDOMAIN => Left(NameNotFound(message))
    case DNS.Rcode.NXRRSET => Left(RecordSetNotFound(message))
    case DNS.Rcode.REFUSED => Left(Refused(message))
    case DNS.Rcode.SERVFAIL => Left(ServerFailure(message))
    case DNS.Rcode.YXDOMAIN => Left(NameExists(message))
    case DNS.Rcode.YXRRSET => Left(RecordSetExists(message))
    case _ => Left(UnrecognizedResponse(message))
  }

  def toDnsResponse(message: DNS.Message): Either[Throwable, DnsResponse] = {

    val obscured = obscuredDnsMessage(message)
    obscured.getRcode match {
      case DNS.Rcode.NOERROR => Right(NoError(obscured))
      case _ => fromDnsRcodeToError(obscured.getRcode, obscured.toString)
    }
  }

  /* Remove the additional record of the TSIG key from the message before generating the string */
  def obscuredDnsMessage(msg: DNS.Message): DNS.Message = {
    val clone = msg.clone.asInstanceOf[DNS.Message]
    val sections = clone.getSectionArray(DNS.Section.ADDITIONAL)
    if (sections != null && sections.nonEmpty) {
      sections.filter(_.getType == DNS.Type.TSIG).foreach { tsigRecord =>
        clone.removeRecord(tsigRecord, DNS.Section.ADDITIONAL)
      }
    }
    clone
  }

  def fromDnsRecordType(typ: Int): RecordType = typ match {
    case DNS.Type.A => RecordType.A
    case DNS.Type.AAAA => RecordType.AAAA
    case DNS.Type.CNAME => RecordType.CNAME
    case DNS.Type.DS => RecordType.DS
    case DNS.Type.MX => RecordType.MX
    case DNS.Type.NS => RecordType.NS
    case DNS.Type.PTR => RecordType.PTR
    case DNS.Type.SOA => RecordType.SOA
    case DNS.Type.SPF => RecordType.SPF
    case DNS.Type.SRV => RecordType.SRV
    case DNS.Type.SSHFP => RecordType.SSHFP
    case DNS.Type.TXT => RecordType.TXT
    case _ => RecordType.UNKNOWN
  }

  def toRecordSet(r: DNS.Record, zoneName: DNS.Name, zoneId: String = "unknown"): RecordSet =
    r match {
      case x: DNS.ARecord => fromARecord(x, zoneName, zoneId)
      case x: DNS.AAAARecord => fromAAAARecord(x, zoneName, zoneId)
      case x: DNS.CNAMERecord => fromCNAMERecord(x, zoneName, zoneId)
      case x: DNS.DSRecord => fromDSRecord(x, zoneName, zoneId)
      case x: DNS.MXRecord => fromMXRecord(x, zoneName, zoneId)
      case x: DNS.NSRecord => fromNSRecord(x, zoneName, zoneId)
      case x: DNS.PTRRecord => fromPTRRecord(x, zoneName, zoneId)
      case x: DNS.SOARecord => fromSOARecord(x, zoneName, zoneId)
      case x: DNS.SPFRecord => fromSPFRecord(x, zoneName, zoneId)
      case x: DNS.SRVRecord => fromSRVRecord(x, zoneName, zoneId)
      case x: DNS.SSHFPRecord => fromSSHFPRecord(x, zoneName, zoneId)
      case x: DNS.TXTRecord => fromTXTRecord(x, zoneName, zoneId)
      case _ => fromUnknownRecordType(r, zoneName, zoneId)
    }

  /**
    * Converts the list of raw DNS records to a list of record sets.
    *
    * Will join / combine DNS.Records that belong in the same record set.
    */
  def toFlattenedRecordSets(
      records: List[DNS.Record],
      zoneName: DNS.Name,
      zoneId: String = "unknown"): List[RecordSet] = {

    /* Combines record sets into a list of one or Nil in case there are no record sets in the list provided */
    def combineRecordSets(lst: List[RecordSet]): RecordSet =
      lst.tail.foldLeft(lst.head)((h, r) => h.copy(records = h.records ++ r.records))

    records
      .map(toRecordSet(_, zoneName, zoneId))
      .groupBy(rs => (rs.name, rs.typ))
      .values
      .map(combineRecordSets)
      .toList
  }

  // Do a "relativize" using the zoneName, this removes the zone name from the record itself
  // For example "test-01.vinyldns." becomes "test-01"...this is necessary as we want to run comparisons upstream
  def fromDnsRecord[A <: DNS.Record](r: A, zoneName: DNS.Name, zoneId: String)(
      f: A => List[RecordData]): RecordSet =
    record.RecordSet(
      zoneId = zoneId,
      name = relativize(r.getName, zoneName),
      typ = fromDnsRecordType(r.getType),
      ttl = r.getTTL,
      status = RecordSetStatus.Active,
      created = DateTime.now,
      records = f(r)
    )

  // if we do not know the record type, then we cannot parse the records, but we should be able to get everything else
  def fromUnknownRecordType(r: DNS.Record, zoneName: DNS.Name, zoneId: String): RecordSet =
    RecordSet(
      zoneId = zoneId,
      name = relativize(r.getName, zoneName),
      typ = fromDnsRecordType(r.getType),
      ttl = r.getTTL,
      status = RecordSetStatus.Active,
      created = DateTime.now,
      records = Nil
    )

  def fromARecord(r: DNS.ARecord, zoneName: DNS.Name, zoneId: String): RecordSet =
    fromDnsRecord(r, zoneName, zoneId) { data =>
      List(AData(data.getAddress.getHostAddress))
    }

  def fromAAAARecord(r: DNS.AAAARecord, zoneName: DNS.Name, zoneId: String): RecordSet =
    fromDnsRecord(r, zoneName, zoneId) { data =>
      List(AAAAData(data.getAddress.getHostAddress))
    }

  def fromCNAMERecord(r: DNS.CNAMERecord, zoneName: DNS.Name, zoneId: String): RecordSet =
    fromDnsRecord(r, zoneName, zoneId) { data =>
      List(CNAMEData(data.getAlias.toString))
    }

  def fromDSRecord(r: DNS.DSRecord, zoneName: DNS.Name, zoneId: String): RecordSet =
    fromDnsRecord(r, zoneName, zoneId) { data =>
      List(
        DSData(
          data.getFootprint,
          DnsSecAlgorithm(data.getAlgorithm),
          DigestType(data.getDigestID),
          ByteVector(data.getDigest)
        ))
    }

  def fromMXRecord(r: DNS.MXRecord, zoneName: DNS.Name, zoneId: String): RecordSet =
    fromDnsRecord(r, zoneName, zoneId) { data =>
      List(MXData(data.getPriority, data.getTarget.toString))
    }

  def fromNSRecord(r: DNS.NSRecord, zoneName: DNS.Name, zoneId: String): RecordSet =
    fromDnsRecord(r, zoneName, zoneId) { data =>
      List(NSData(data.getTarget.toString))
    }

  def fromPTRRecord(r: DNS.PTRRecord, zoneName: DNS.Name, zoneId: String): RecordSet =
    fromDnsRecord(r, zoneName, zoneId) { data =>
      List(PTRData(data.getTarget.toString))
    }

  def fromSOARecord(r: DNS.SOARecord, zoneName: DNS.Name, zoneId: String): RecordSet =
    fromDnsRecord(r, zoneName, zoneId) { data =>
      List(
        SOAData(
          data.getHost.toString,
          data.getAdmin.toString,
          data.getSerial,
          data.getRefresh,
          data.getRetry,
          data.getExpire,
          data.getMinimum))
    }

  def fromSPFRecord(r: DNS.SPFRecord, zoneName: DNS.Name, zoneId: String): RecordSet =
    fromDnsRecord(r, zoneName, zoneId) { data =>
      List(SPFData(data.getStrings.asScala.mkString(",")))
    }

  def fromSRVRecord(r: DNS.SRVRecord, zoneName: DNS.Name, zoneId: String): RecordSet =
    fromDnsRecord(r, zoneName, zoneId) { data =>
      List(SRVData(data.getPriority, data.getWeight, data.getPort, data.getTarget.toString))
    }

  def fromSSHFPRecord(r: DNS.SSHFPRecord, zoneName: DNS.Name, zoneId: String): RecordSet =
    fromDnsRecord(r, zoneName, zoneId) { data =>
      List(SSHFPData(data.getAlgorithm, data.getDigestType, new String(data.getFingerPrint)))
    }

  def fromTXTRecord(r: DNS.TXTRecord, zoneName: DNS.Name, zoneId: String): RecordSet =
    fromDnsRecord(r, zoneName, zoneId) { data =>
      List(TXTData(data.getStrings.asScala.mkString))
    }

  def toDnsRecords(recordSet: RecordSet, zoneName: String): Either[Throwable, List[DNS.Record]] =
    Either.catchNonFatal {
      val ttl = recordSet.ttl
      val recordName = recordDnsName(recordSet.name, zoneName)

      recordSet.records.map {
        case AData(address) =>
          new DNS.ARecord(recordName, DNS.DClass.IN, ttl, InetAddress.getByName(address))

        case AAAAData(address) =>
          new DNS.AAAARecord(recordName, DNS.DClass.IN, ttl, InetAddress.getByName(address))

        case CNAMEData(cname) =>
          new DNS.CNAMERecord(recordName, DNS.DClass.IN, ttl, DNS.Name.fromString(cname))

        case DSData(keyTag, algorithm, digestType, digest) =>
          new DNS.DSRecord(
            recordName,
            DNS.DClass.IN,
            ttl,
            keyTag,
            algorithm.value,
            digestType.value,
            digest.toArray)

        case NSData(nsdname) =>
          new DNS.NSRecord(recordName, DNS.DClass.IN, ttl, DNS.Name.fromString(nsdname))

        case MXData(preference, exchange) =>
          new DNS.MXRecord(
            recordName,
            DNS.DClass.IN,
            ttl,
            preference,
            DNS.Name.fromString(exchange))

        case PTRData(ptrdname) =>
          new DNS.PTRRecord(recordName, DNS.DClass.IN, ttl, DNS.Name.fromString(ptrdname))

        case SOAData(mname, rname, serial, refresh, retry, expire, minimum) =>
          new DNS.SOARecord(
            recordName,
            DNS.DClass.IN,
            ttl,
            DNS.Name.fromString(mname),
            DNS.Name.fromString(rname),
            serial,
            refresh,
            retry,
            expire,
            minimum)

        case SRVData(priority, weight, port, target) =>
          new DNS.SRVRecord(
            recordName,
            DNS.DClass.IN,
            ttl,
            priority,
            weight,
            port,
            DNS.Name.fromString(target))

        case SSHFPData(algorithm, typ, fingerprint) =>
          new DNS.SSHFPRecord(recordName, DNS.DClass.IN, ttl, algorithm, typ, fingerprint.getBytes)

        case SPFData(text) =>
          new DNS.SPFRecord(recordName, DNS.DClass.IN, ttl, text)

        case TXTData(text) =>
          val texts = text.grouped(255).toList
          new DNS.TXTRecord(recordName, DNS.DClass.IN, ttl, texts.asJava)
      }
    }

  def toDnsRRset(recordSet: RecordSet, zoneName: String): Either[Throwable, DNS.RRset] = {
    val dnsRecordSet = new DNS.RRset()
    toDnsRecords(recordSet, zoneName).map { record =>
      record.foreach(dnsRecordSet.addRR)
      dnsRecordSet
    }
  }

  def toDnsRecordType(typ: RecordType): Integer = typ match {
    case RecordType.A => DNS.Type.A
    case RecordType.AAAA => DNS.Type.AAAA
    case RecordType.CNAME => DNS.Type.CNAME
    case RecordType.DS => DNS.Type.DS
    case RecordType.MX => DNS.Type.MX
    case RecordType.NS => DNS.Type.NS
    case RecordType.PTR => DNS.Type.PTR
    case RecordType.SOA => DNS.Type.SOA
    case RecordType.SPF => DNS.Type.SPF
    case RecordType.SSHFP => DNS.Type.SSHFP
    case RecordType.SRV => DNS.Type.SRV
    case RecordType.TXT => DNS.Type.TXT
  }

  def toAddRecordMessage(r: DNS.RRset, zoneName: String): Either[Throwable, DNS.Update] = {
    val update = new DNS.Update(zoneDnsName(zoneName))
    update.add(r)
    Right(update)
  }

  def toUpdateRecordMessage(
      r: DNS.RRset,
      old: DNS.RRset,
      zoneName: String): Either[Throwable, DNS.Update] = {
    val update = new DNS.Update(zoneDnsName(zoneName))

    if (!r.getName.equals(old.getName) || r.getTTL != old.getTTL) { // Name or TTL has changed
      update.delete(old.getName, old.getType)
      update.add(r)
    } else {
      val oldRecordList = old.rrs().asScala.toList.asInstanceOf[List[DNS.Record]].toSet
      val newRecordList = r.rrs().asScala.toList.asInstanceOf[List[DNS.Record]].toSet

      val deleteRecords = oldRecordList.diff(newRecordList)
      val addRecords = newRecordList.diff(oldRecordList)

      // For DDNS, we pass the exact DNS record for deletion, resulting in a DClass.NONE for each deletion
      deleteRecords.foreach(update.delete)
      addRecords.foreach(update.add)
    }
    Right(update)
  }

  def toDeleteRecordMessage(r: DNS.RRset, zoneName: String): Either[Throwable, DNS.Update] = {
    val update = new DNS.Update(zoneDnsName(zoneName))
    update.delete(r.getName, r.getType)
    Right(update)
  }
}

object DnsConversions extends DnsConversions
