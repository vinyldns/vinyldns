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
import vinyldns.api.route.{ListGlobalRecordSetsResponse, ListRecordSetsByZoneResponse}
import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.RecordTypeSort.RecordTypeSort
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

  def getRecordSetByZone(
                          recordSetId: String,
                          zoneId: String,
                          authPrincipal: AuthPrincipal
                        ): Result[RecordSetInfo]

  def listRecordSets(
                      startFrom: Option[String],
                      maxItems: Option[Int],
                      recordNameFilter: String,
                      recordTypeFilter: Option[Set[RecordType]],
                      recordOwnerGroupId: Option[String],
                      nameSort: NameSort,
                      authPrincipal: AuthPrincipal,
                      recordTypeSort: RecordTypeSort
                    ): Result[ListGlobalRecordSetsResponse]

  /**
   * Searches recordsets, optionally using the recordset cache (controlled by the 'use-recordset-cache' setting)
   *
   * @param startFrom          The starting record
   * @param maxItems           The maximum number of items
   * @param recordNameFilter   The record name filter
   * @param recordTypeFilter   The record type filter
   * @param recordOwnerGroupId THe owner group identifier
   * @param nameSort           The sort direction
   * @param authPrincipal      The authenticated principal
   * @return A {@link ListGlobalRecordSetsResponse}
   */
  def searchRecordSets(
                        startFrom: Option[String],
                        maxItems: Option[Int],
                        recordNameFilter: String,
                        recordTypeFilter: Option[Set[RecordType]],
                        recordOwnerGroupId: Option[String],
                        nameSort: NameSort,
                        authPrincipal: AuthPrincipal,
                        recordTypeSort: RecordTypeSort
                      ): Result[ListGlobalRecordSetsResponse]

  def listRecordSetsByZone(
                            zoneId: String,
                            startFrom: Option[String],
                            maxItems: Option[Int],
                            recordNameFilter: Option[String],
                            recordTypeFilter: Option[Set[RecordType]],
                            recordOwnerGroupId: Option[String],
                            nameSort: NameSort,
                            authPrincipal: AuthPrincipal,
                            recordTypeSort: RecordTypeSort
                          ): Result[ListRecordSetsByZoneResponse]

  def getRecordSetChange(
                          zoneId: String,
                          changeId: String,
                          authPrincipal: AuthPrincipal
                        ): Result[RecordSetChange]

  def listRecordSetChanges(
                            zoneId: Option[String],
                            startFrom: Option[Int],
                            maxItems: Int,
                            fqdn: Option[String],
                            recordType: Option[RecordType],
                            authPrincipal: AuthPrincipal
                          ): Result[ListRecordSetChangesResponse]

  def listRecordSetChangeHistory(
                            zoneId: Option[String],
                            startFrom: Option[Int],
                            maxItems: Int,
                            fqdn: Option[String],
                            recordType: Option[RecordType],
                            authPrincipal: AuthPrincipal
                          ): Result[ListRecordSetHistoryResponse]

  def listFailedRecordSetChanges(
                                  authPrincipal: AuthPrincipal,
                                  zoneId: Option[String],
                                  startFrom: Int,
                                  maxItems: Int
                                ): Result[ListFailedRecordSetChangesResponse]

}
