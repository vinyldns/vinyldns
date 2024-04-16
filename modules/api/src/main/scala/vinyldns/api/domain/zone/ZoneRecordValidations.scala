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

import cats.implicits._
import cats.data._
import com.comcast.ip4s.IpAddress
import vinyldns.core.Messages._
import vinyldns.core.domain.{DomainHelpers, DomainValidationError, HighValueDomainError, RecordRequiresManualReview}
import vinyldns.core.domain.record.{NSData, RecordSet}

import scala.util.matching.Regex

object ZoneRecordValidations {

  def toCaseIgnoredRegexList(rawList: List[String]): List[Regex] =
    rawList.map(raw => s"(?i)$raw".r)

  /* Checks to see if an individual string is part of the regex list */
  def isStringInRegexList(regexList: List[Regex], string: String): Boolean =
    regexList.exists(rx => rx.findAllIn(string).contains(string))

  /* Checks to see if an ip address is part of the ip address list */
  def isIpInIpList(ipList: List[IpAddress], ipToTest: String): Boolean =
    IpAddress.fromString(ipToTest).exists(ip => ipList.exists(_ === ip))

  /* Checks to see if an individual ns data is part of the approved server list */
  def isApprovedNameServer(
      approvedServerList: List[Regex],
      nsData: NSData
  ): ValidatedNel[String, NSData] =
    if (isStringInRegexList(approvedServerList, nsData.nsdname.fqdn)) {
      nsData.validNel[String]
    } else {
      ApprovedNameServerMsg.format(nsData.nsdname.fqdn).invalidNel[NSData]
    }

  /* Inspects each record in the rdata, returning back the record set itself or all ns records that are not approved */
  def containsApprovedNameServers(
      approvedServerList: List[Regex],
      nsRecordSet: RecordSet
  ): ValidatedNel[String, RecordSet] = {
    val validations: List[ValidatedNel[String, NSData]] = nsRecordSet.records
      .collect { case ns: NSData => ns }
      .map(isApprovedNameServer(approvedServerList, _))

    validations.sequence.map(_ => nsRecordSet)
  }

  def isNotHighValueFqdn(
      highValueRegexList: List[Regex],
      fqdn: String
  ): ValidatedNel[DomainValidationError, Unit] =
    if (!isStringInRegexList(highValueRegexList, fqdn)) {
      ().validNel
    } else {
      HighValueDomainError(fqdn).invalidNel
    }

  def isNotHighValueIp(
      highValueIpList: List[IpAddress],
      ip: String
  ): ValidatedNel[DomainValidationError, Unit] =
    if (!isIpInIpList(highValueIpList, ip)) {
      ().validNel
    } else {
      HighValueDomainError(ip).invalidNel
    }

  def domainDoesNotRequireManualReview(
      regexList: List[Regex],
      fqdn: String
  ): ValidatedNel[DomainValidationError, Unit] =
    if (!isStringInRegexList(regexList, fqdn)) {
      ().validNel
    } else {
      RecordRequiresManualReview(fqdn).invalidNel
    }

  def ipDoesNotRequireManualReview(
      regexList: List[IpAddress],
      ip: String
  ): ValidatedNel[DomainValidationError, Unit] =
    if (!isIpInIpList(regexList, ip)) {
      ().validNel
    } else {
      RecordRequiresManualReview(ip).invalidNel
    }

  def zoneDoesNotRequireManualReview(
      zonesRequiringReview: Set[String],
      zoneName: String,
      fqdn: String
  ): ValidatedNel[DomainValidationError, Unit] =
    if (!zonesRequiringReview.contains(DomainHelpers.ensureTrailingDot(zoneName.toLowerCase))) {
      ().validNel
    } else {
      RecordRequiresManualReview(fqdn).invalidNel
    }
}
