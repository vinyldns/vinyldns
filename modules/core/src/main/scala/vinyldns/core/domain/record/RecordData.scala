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

sealed trait RecordData

final case class AData(address: String) extends RecordData

final case class AAAAData(address: String) extends RecordData

final case class CNAMEData(cname: Fqdn) extends RecordData

object CNAMEData {
  def apply(cname: Fqdn): CNAMEData =
    new CNAMEData(cname)
}

final case class MXData(preference: Integer, exchange: Fqdn) extends RecordData

object MXData {
  def apply(preference: Integer, exchange: Fqdn): MXData =
    new MXData(preference, exchange)
}

final case class NSData(nsdname: Fqdn) extends RecordData

object NSData {
  def apply(nsdname: Fqdn): NSData =
    new NSData(nsdname)
}

final case class PTRData(ptrdname: Fqdn) extends RecordData

object PTRData {
  def apply(ptrdname: Fqdn): PTRData =
    new PTRData(ptrdname)
}

final case class SOAData(
    mname: Fqdn,
    rname: String,
    serial: Long,
    refresh: Long,
    retry: Long,
    expire: Long,
    minimum: Long
) extends RecordData

final case class SPFData(text: String) extends RecordData

final case class SRVData(priority: Integer, weight: Integer, port: Integer, target: Fqdn)
    extends RecordData

object SRVData {
  def apply(priority: Integer, weight: Integer, port: Integer, target: Fqdn): SRVData =
    new SRVData(priority, weight, port, target)
}

final case class NAPTRData(
    order: Integer,
    preference: Integer,
    flags: String,
    service: String,
    regexp: String,
    replacement: Fqdn
) extends RecordData

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
}

final case class SSHFPData(algorithm: Integer, typ: Integer, fingerprint: String) extends RecordData

final case class TXTData(text: String) extends RecordData

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
) extends RecordData
