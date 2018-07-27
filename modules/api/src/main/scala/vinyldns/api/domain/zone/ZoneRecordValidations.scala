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

import scalaz.ValidationNel
import scalaz.std.list._
import scalaz.syntax.traverse._
import scalaz.syntax.validation._
import vinyldns.api.domain.record.{NSData, RecordSet, RecordType}

import scala.util.matching.Regex

object ZoneRecordValidations {

  /* Checks to see if an individual ns data is part of the approved server list */
  def isApprovedNameServer(
      approvedServerList: List[Regex],
      nsData: NSData): ValidationNel[String, NSData] =
    if (approvedServerList.exists(rx => rx.findAllIn(nsData.nsdname).contains(nsData.nsdname))) {
      nsData.successNel[String]
    } else {
      s"Name Server ${nsData.nsdname} is not an approved name server.".failureNel[NSData]
    }

  /* Inspects each record in the rdata, returning back the record set itself or all ns records that are not approved */
  def containsApprovedNameServers(
      approvedServerList: List[Regex],
      nsRecordSet: RecordSet): ValidationNel[String, RecordSet] = {
    val validations: List[ValidationNel[String, NSData]] =
      nsRecordSet.records
        .map(_.asInstanceOf[NSData])
        .map(isApprovedNameServer(approvedServerList, _))

    validations.sequenceU.map(_ => nsRecordSet)
  }

  /* name server must exist in the approved server list, and have a valid name */
  def validNameServer(
      approvedServerList: List[Regex],
      rs: RecordSet): ValidationNel[String, RecordSet] =
    containsApprovedNameServers(approvedServerList, rs)

  /* Performs the actual validations on the zone */
  def validateDnsZone(
      approvedServerList: List[Regex],
      recordSets: List[RecordSet]): ValidationNel[String, List[RecordSet]] = {
    val validations: List[ValidationNel[String, RecordSet]] = recordSets.map {
      case ns if ns.typ == RecordType.NS =>
        // This is awful, need to redo the core domain model
        validNameServer(approvedServerList, ns)

      case otherRecordType =>
        otherRecordType.successNel[String]
    }

    validations.sequenceU
  }
}
