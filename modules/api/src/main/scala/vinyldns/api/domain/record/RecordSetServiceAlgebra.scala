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

import vinyldns.api.Interfaces.Result
import vinyldns.api.domain.zone.RecordSetInfo
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.zone.ZoneCommandResult
import vinyldns.api.route.ListRecordSetsResponse
import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.{RecordSet, RecordSetChange}

trait RecordSetServiceAlgebra {
  def addRecordSet(recordSet: RecordSet, auth: AuthPrincipal): Result[ZoneCommandResult]

  def updateRecordSet(recordSet: RecordSet, auth: AuthPrincipal): Result[ZoneCommandResult]

  def deleteRecordSet(
      recordSetId: String,
      zoneId: String,
      auth: AuthPrincipal
  ): Result[ZoneCommandResult]

  def getRecordSet(
      recordSetId: String,
      authPrincipal: AuthPrincipal
  ): Result[RecordSetInfo]

  def getRecordSetChanges(
      recordSetId: String,
      startFrom: Option[String],
      maxItems: Int,
      authPrincipal: AuthPrincipal
  ): Result[ListRecordSetRecordSetChangesResponse]

  def getRecordSetByZone(
      recordSetId: String,
      zoneId: String,
      authPrincipal: AuthPrincipal
  ): Result[RecordSetInfo]

  def listRecordSetsByZone(
      zoneId: String,
      startFrom: Option[String],
      maxItems: Option[Int],
      recordNameFilter: Option[String],
      recordTypeFilter: Option[Set[RecordType]],
      nameSort: NameSort,
      authPrincipal: AuthPrincipal
  ): Result[ListRecordSetsResponse]

  def getRecordSetChange(
      zoneId: String,
      changeId: String,
      authPrincipal: AuthPrincipal
  ): Result[RecordSetChange]

  def listRecordSetChanges(
      zoneId: String,
      startFrom: Option[String],
      maxItems: Int,
      authPrincipal: AuthPrincipal
  ): Result[ListRecordSetChangesResponse]

}
