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

import cats._
import cats.data._
import cats.implicits._
import cats.syntax.either._
import cats.instances.list._
import cats.instances.option._
import cats.syntax.traverse._
import vinyldns.api.domain.record.{NSData, RecordSet, RecordType}

import scala.util.matching.Regex

object ZoneRecordValidations {

  /* Checks to see if an individual ns data is part of the approved server list */
  def isApprovedNameServer(
      approvedServerList: List[Regex],
      nsData: NSData): ValidatedNel[String, NSData] =
    if (approvedServerList.exists(rx => rx.findAllIn(nsData.nsdname).contains(nsData.nsdname))) {
      nsData.validNel[String]
    } else {
      s"Name Server ${nsData.nsdname} is not an approved name server.".invalidNel[NSData]
    }

  /* Inspects each record in the rdata, returning back the record set itself or all ns records that are not approved */
  def containsApprovedNameServers(
      approvedServerList: List[Regex],
      nsRecordSet: RecordSet): ValidatedNel[String, RecordSet] = {
    val validations: List[ValidatedNel[String, NSData]] =
      nsRecordSet.records
        .map(_.asInstanceOf[NSData])
        .map(isApprovedNameServer(approvedServerList, _))

    validations.sequence.map(_ => nsRecordSet)
  }

  /* name server must exist in the approved server list, and have a valid name */
  def validNameServer(
      approvedServerList: List[Regex],
      rs: RecordSet): ValidatedNel[String, RecordSet] =
    containsApprovedNameServers(approvedServerList, rs)

  /* Performs the actual validations on the zone */
  def validateDnsZone(
      approvedServerList: List[Regex],
      recordSets: List[RecordSet]): ValidatedNel[String, List[RecordSet]] = {
    val validations: List[ValidatedNel[String, RecordSet]] = recordSets.map {
      case ns if ns.typ == RecordType.NS =>
        // This is awful, need to redo the core domain model
        validNameServer(approvedServerList, ns)

      case otherRecordType =>
        otherRecordType.validNel[String]
    }

    validations.foldLeft(List.empty[RecordSet]) { (acc, cur) =>
      }
  }
}
