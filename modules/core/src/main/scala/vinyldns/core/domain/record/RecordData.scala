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

package vinyldns.core.domain.record

import scodec.bits.ByteVector
import vinyldns.core.domain.Fqdn

import scala.util.Try
import RecordData._
import vinyldns.core.domain.record.RecordType._

sealed trait RecordData {
  def toString: String
}
object RecordData {
  def toInt(value: String): Option[Int] =
    Try(value.toInt).toOption

  def toLong(value: String): Option[Long] =
    Try(value.toLong).toOption

  def fromString(value: String, typ: RecordType): Option[RecordData] = typ match {
    case A => AData.fromString(value)
    case AAAA => AAAAData.fromString(value)
    case CNAME => CNAMEData.fromString(value)
    case DS => DSData.fromString(value)
    case MX => MXData.fromString(value)
    case NAPTR => NAPTRData.fromString(value)
    case NS => NSData.fromString(value)
    case PTR => PTRData.fromString(value)
    case SPF => SPFData.fromString(value)
    case SRV => SRVData.fromString(value)
    case SSHFP => SSHFPData.fromString(value)
    case TXT => TXTData.fromString(value)
    case SOA => SOAData.fromString(value)
    case UNKNOWN => None
  }
}

final case class AData(address: String) extends RecordData {
  override def toString: String = address
}
object AData {
  def fromString(value: String): Option[AData] = Option(value).map(AData(_))
}

final case class AAAAData(address: String) extends RecordData {
  override def toString: String = address
}
object AAAAData {
  def fromString(value: String): Option[AAAAData] = Option(value).map(AAAAData(_))
}

final case class CNAMEData(cname: Fqdn) extends RecordData {
  override def toString: String = cname.fqdn
}
object CNAMEData {
  def apply(cname: Fqdn): CNAMEData =
    new CNAMEData(cname)

  def fromString(value: String): Option[CNAMEData] =
    Option(value).map(Fqdn.apply).map(CNAMEData.apply)
}

final case class MXData(preference: Integer, exchange: Fqdn) extends RecordData {
  override def toString: String = s"$preference ${exchange.fqdn}"
}

object MXData {
  def apply(preference: Integer, exchange: Fqdn): MXData =
    new MXData(preference, exchange)

  /* Assumes format preference fqdn, e.g. 10 www.example.com; otherwise returns None */
  def fromString(value: String): Option[MXData] =
    Option(value).flatMap { v =>
      val parts = v.split(' ')
      if (parts.length != 2) {
        None
      } else {
        toInt(parts(0)).map { pref =>
          new MXData(pref, Fqdn(parts(1)))
        }
      }
    }
}

final case class NSData(nsdname: Fqdn) extends RecordData {
  override def toString: String = nsdname.fqdn
}

object NSData {
  def apply(nsdname: Fqdn): NSData =
    new NSData(nsdname)

  def fromString(value: String): Option[NSData] =
    Option(value).map(Fqdn.apply).map(NSData.apply)
}

final case class PTRData(ptrdname: Fqdn) extends RecordData {
  override def toString: String = ptrdname.fqdn
}

object PTRData {
  def apply(ptrdname: Fqdn): PTRData =
    new PTRData(ptrdname)

  def fromString(value: String): Option[PTRData] =
    Option(value).map(Fqdn.apply).map(PTRData.apply)
}

final case class SOAData(
    mname: Fqdn,
    rname: String,
    serial: Long,
    refresh: Long,
    retry: Long,
    expire: Long,
    minimum: Long
) extends RecordData {
  override def toString: String = s"${mname.fqdn} ${rname} $serial $refresh $retry $expire $minimum"
}
object SOAData {
  def fromString(value: String): Option[SOAData] =
    Option(value).flatMap { v =>
      val parts = v.split(' ')
      if (parts.length != 7) {
        None
      } else {
        for {
          serial <- toLong(parts(2))
          refresh <- toLong(parts(3))
          retry <- toLong(parts(4))
          expire <- toLong(parts(5))
          minimum <- toLong(parts(6))
        } yield SOAData(
          Fqdn(parts(0)),
          parts(1),
          serial,
          refresh,
          retry,
          expire,
          minimum
        )
      }
    }
}

final case class SPFData(text: String) extends RecordData {
  override def toString: String = text
}
object SPFData {
  def fromString(value: String): Option[SPFData] = Option(value).map(SPFData(_))
}

final case class SRVData(priority: Integer, weight: Integer, port: Integer, target: Fqdn)
    extends RecordData {
  override def toString: String = s"$priority $weight $port ${target.fqdn}"
}

object SRVData {
  def fromString(value: String): Option[SRVData] =
    Option(value).flatMap { v =>
      val parts = v.split(' ')
      for {
        priority <- toInt(parts(0))
        weight <- toInt(parts(1))
        port <- toInt(parts(2))
        target = Fqdn(parts(3))
      } yield SRVData(
        priority,
        weight,
        port,
        target
      )
    }
}

final case class NAPTRData(
    order: Integer,
    preference: Integer,
    flags: String,
    service: String,
    regexp: String,
    replacement: Fqdn
) extends RecordData {
  override def toString: String = s"$order $preference $flags $service $regexp ${replacement.fqdn}"
}

object NAPTRData {
  def apply(
      order: Integer,
      preference: Integer,
      flags: String,
      service: String,
      regexp: String,
      replacement: Fqdn
  ): NAPTRData =
    new NAPTRData(order, preference, flags, service, regexp, replacement)

  def fromString(value: String): Option[NAPTRData] =
    Option(value).flatMap { v =>
      val parts = v.split(' ')
      if (parts.length != 6) {
        None
      } else {
        for {
          order <- toInt(parts(0))
          pref <- toInt(parts(1))
          flags = parts(2)
          service = parts(3)
          reg = parts(4)
          rep = Fqdn(parts(5))
        } yield NAPTRData(order, pref, flags, service, reg, rep)
      }
    }
}

final case class SSHFPData(algorithm: Integer, typ: Integer, fingerprint: String)
    extends RecordData {
  override def toString: String = s"$algorithm $typ $fingerprint"
}
object SSHFPData {
  def fromString(value: String): Option[SSHFPData] =
    Option(value).flatMap { v =>
      val parts = v.split(' ')
      if (parts.length != 3) {
        None
      } else {
        for {
          alg <- toInt(parts(0))
          typ <- toInt(parts(1))
          fp = parts(2)
        } yield SSHFPData(alg, typ, fp)
      }
    }
}

final case class TXTData(text: String) extends RecordData {
  override def toString: String = text
}
object TXTData {
  def fromString(value: String): Option[TXTData] = Option(value).map(TXTData(_))
}

sealed abstract class DigestType(val value: Int)
object DigestType {
  // see https://www.iana.org/assignments/ds-rr-types/ds-rr-types.xhtml
  case object SHA1 extends DigestType(1)
  case object SHA256 extends DigestType(2)
  case object GOSTR341194 extends DigestType(3)
  case object SHA384 extends DigestType(4)
  final case class UnknownDigestType(x: Int) extends DigestType(x)

  def apply(value: Int): DigestType =
    value match {
      case 1 => SHA1
      case 2 => SHA256
      case 3 => GOSTR341194
      case 4 => SHA384
      case other => UnknownDigestType(other)
    }
}

sealed abstract class DnsSecAlgorithm(val value: Int)
object DnsSecAlgorithm {
  // see https://www.iana.org/assignments/dns-sec-alg-numbers/dns-sec-alg-numbers.xhtml
  case object DSA extends DnsSecAlgorithm(3)
  case object RSASHA1 extends DnsSecAlgorithm(5)
  case object DSA_NSEC3_SHA1 extends DnsSecAlgorithm(6)
  case object RSASHA1_NSEC3_SHA1 extends DnsSecAlgorithm(7)
  case object RSASHA256 extends DnsSecAlgorithm(8)
  case object RSASHA512 extends DnsSecAlgorithm(10)
  case object ECC_GOST extends DnsSecAlgorithm(12)
  case object ECDSAP256SHA256 extends DnsSecAlgorithm(13)
  case object ECDSAP384SHA384 extends DnsSecAlgorithm(14)
  case object ED25519 extends DnsSecAlgorithm(15)
  case object ED448 extends DnsSecAlgorithm(16)
  case object PRIVATEDNS extends DnsSecAlgorithm(253)
  case object PRIVATEOID extends DnsSecAlgorithm(254)
  final case class UnknownAlgorithm private (x: Int) extends DnsSecAlgorithm(x)

  def apply(value: Int): DnsSecAlgorithm =
    value match {
      case 3 => DSA
      case 5 => RSASHA1
      case 6 => DSA_NSEC3_SHA1
      case 7 => RSASHA1_NSEC3_SHA1
      case 8 => RSASHA256
      case 10 => RSASHA512
      case 12 => ECC_GOST
      case 13 => ECDSAP256SHA256
      case 14 => ECDSAP384SHA384
      case 15 => ED25519
      case 16 => ED448
      case 253 => PRIVATEDNS
      case 254 => PRIVATEOID
      case other => UnknownAlgorithm(other)
    }
}

final case class DSData(
    keyTag: Integer, // footprint in DNSJava
    algorithm: DnsSecAlgorithm,
    digestType: DigestType, //digestid in DNSJava
    digest: ByteVector
) extends RecordData {
  override def toString: String = s"$keyTag $algorithm $digestType $digest"
}
object DSData {
  def fromString(value: String): Option[DSData] =
    Option(value).flatMap { v =>
      val parts = v.split(' ')
      if (parts.length != 3) {
        None
      } else {
        for {
          kt <- toInt(parts(0))
          alg <- toInt(parts(1)).map(DnsSecAlgorithm.apply)
          dt <- toInt(parts(2)).map(DigestType.apply)
          dig <- Some(ByteVector(parts(3).getBytes))
        } yield DSData(kt, alg, dt, dig)
      }
    }
}
