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

package vinyldns.api.domain

import cats.implicits._
import com.comcast.ip4s.{Cidr, Ipv4Address, Ipv6Address}
import vinyldns.api.domain.zone.InvalidRequest
import vinyldns.core.domain.zone.Zone
import vinyldns.api.backend.dns.DnsConversions._

import scala.util.Try

object ReverseZoneHelpers {

  def recordsetIsWithinCidrMask(mask: String, zone: Zone, recordName: String): Boolean =
    if (zone.isIPv4) {
      recordsetIsWithinCidrMaskIpv4(mask: String, zone: Zone, recordName: String)
    } else {
      val ipAddr = Ipv6Address.fromString(convertPTRtoIPv6(zone, recordName))
      Try(Cidr(Cidr.fromString6(mask).get.address,Cidr.fromString6(mask).get.prefixBits).contains(ipAddr.get))
        .getOrElse(false)
    }

  // NOTE: this will not work for zones with less than 3 octets
  def ipIsInIpv4ReverseZone(zone: Zone, ipv4: String): Boolean = {
    val base = getIPv4NonDelegatedZoneName(ipv4)

    base.exists { baseZoneName =>
      if (zone.name == baseZoneName) true
      else if (zone.name.endsWith(s".$baseZoneName")) {
        val recordName = ipv4.split('.').takeRight(1).mkString
        ptrIsInClasslessDelegatedZone(zone, recordName).isRight
      } else false
    }
  }

  // NOTE: this function assumes record/zone interface. Unless prior checks have been made, it should not
  // be used to check if an FQDN is within a reverse zone unless we know the rest of the zone matches the higher
  // octets
  def ptrIsInClasslessDelegatedZone(zone: Zone, recordName: String): Either[Throwable, Unit] =
    if (zone.isIPv4) {
      handleIpv4RecordValidation(zone: Zone, recordName)
    } else if (zone.isIPv6) {
      handleIpv6RecordValidation(zone: Zone, recordName)
    } else {
      InvalidRequest(
        s"RecordSet $recordName does not specify a valid IP address in zone ${zone.name}"
      ).asLeft
    }

  def convertPTRtoIPv4(zone: Zone, recordName: String): String = {
    val zoneName = zone.name.dropRight("in-addr.arpa.".length)
    val zoneOctets = ipv4ReverseSplitByOctets(zoneName)
    val recordOctets = ipv4ReverseSplitByOctets(recordName)

    if (zone.name.contains("/")) {
      (zoneOctets.dropRight(1) ++ recordOctets).mkString(".")
    } else {
      (zoneOctets ++ recordOctets).mkString(".")
    }
  }

  def convertPTRtoIPv6(zone: Zone, recordName: String): String = {
    val zoneName = zone.name.dropRight("ip6.arpa.".length)
    val zoneNameNibblesReversed = zoneName.split('.').reverse.toList
    val recordSetNibblesReversed = recordName.split('.').reverse.toList
    val allUnseparated = (zoneNameNibblesReversed ++ recordSetNibblesReversed).mkString("")
    allUnseparated.grouped(4).reduce(_ + ":" + _)
  }

  private def recordsetIsWithinCidrMaskIpv4(
      mask: String,
      zone: Zone,
      recordName: String
  ): Boolean = {

    val recordIpAddr = Ipv4Address.fromString(convertPTRtoIPv4(zone, recordName))

    Try {
      // make sure mask contains 4 octets, expand if not
      val ipMaskOctets = Cidr.fromString4(mask).get.address.toString.split('.').toList

      val fullIp = ipMaskOctets.length match {
        case 1 => (ipMaskOctets ++ List("0", "0", "0")).mkString(".")
        case 2 => (ipMaskOctets ++ List("0", "0")).mkString(".")
        case 3 => (ipMaskOctets ++ List("0")).mkString(".")
        case 4 => ipMaskOctets.mkString(".")
      }

      val updatedMask = Cidr(recordIpAddr.get,Cidr.fromString4(mask).get.prefixBits)
      updatedMask.contains(Ipv4Address.fromString(fullIp).get)
    }.getOrElse(false)
  }

  private def ipv4ReverseSplitByOctets(string: String): List[String] =
    string.split('.').filter(!_.isEmpty).reverse.toList

  private def getZoneAsCIDRString(zone: Zone): Either[Throwable, String] = {
    val zoneName = zone.name.dropRight("in-addr.arpa.".length)
    val zoneOctets = ipv4ReverseSplitByOctets(zoneName)
    val zoneString = zoneOctets.mkString(".")

    if (zoneString.contains("/")) {
      zoneString.asRight
    } else {
      zoneOctets.length match {
        case 1 => (zoneString + ".0.0.0/8").asRight
        case 2 => (zoneString + ".0.0/16").asRight
        case 3 => (zoneString + ".0/24").asRight
        case _ => InvalidRequest(s"Zone ${zone.name} does not have 1-3 octets: illegal").asLeft
      }
    }
  }

  private def handleIpv4RecordValidation(
      zone: Zone,
      recordName: String
  ): Either[Throwable, Unit] = {
    val isValid = for {
      cidrMask <- getZoneAsCIDRString(zone)
      validated <- if (recordsetIsWithinCidrMask(cidrMask, zone, recordName)) {
        true.asRight
      } else {
        InvalidRequest(
          s"RecordSet $recordName does not specify a valid IP address in zone ${zone.name}"
        ).asLeft
      }
    } yield validated

    isValid.map(_ => ())
  }

  private def handleIpv6RecordValidation(
      zone: Zone,
      recordName: String
  ): Either[Throwable, Unit] = {
    val v6Regex = "(?i)([0-9a-f][.]){32}ip6.arpa.".r

    s"$recordName.${zone.name}" match {
      case v6Regex(_*) => ().asRight
      case _ =>
        InvalidRequest(
          s"RecordSet $recordName does not specify a valid IP address in zone ${zone.name}"
        ).asLeft
    }
  }

  def reverseNameToIp(recordName: String, zone: Zone): String =
    if (zone.isIPv4) {
      ReverseZoneHelpers.convertPTRtoIPv4(zone, recordName)
    } else {
      ReverseZoneHelpers.convertPTRtoIPv6(zone, recordName)
    }
}
