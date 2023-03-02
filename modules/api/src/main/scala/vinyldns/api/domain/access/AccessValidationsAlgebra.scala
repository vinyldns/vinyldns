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

package vinyldns.api.domain.access

import vinyldns.api.domain.zone.{RecordSetInfo, RecordSetListInfo}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.record.RecordData
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.zone.AccessLevel.AccessLevel
import vinyldns.core.domain.zone.Zone

trait AccessValidationsAlgebra {

  def canSeeZone(auth: AuthPrincipal, zone: Zone): Either[Throwable, Unit]

  def canChangeZone(
      auth: AuthPrincipal,
      zoneName: String,
      zoneAdminGroupId: String
  ): Either[Throwable, Unit]

  def canAddRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone,
      recordData: List[RecordData] = List.empty
  ): Either[Throwable, Unit]

  def canUpdateRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone,
      recordOwnerGroupId: Option[String],
      newRecordData: List[RecordData] = List.empty
      ): Either[Throwable, Unit]

  def canDeleteRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone,
      recordOwnerGroupId: Option[String],
      existingRecordData: List[RecordData] = List.empty
  ): Either[Throwable, Unit]

  def canViewRecordSet(
      auth: AuthPrincipal,
      recordName: String,
      recordType: RecordType,
      zone: Zone,
      recordOwnerGroupId: Option[String],
      recordData: List[RecordData] = List.empty
  ): Either[Throwable, Unit]

  def getListAccessLevels(
      auth: AuthPrincipal,
      recordSets: List[RecordSetInfo],
      zone: Zone
  ): List[RecordSetListInfo]

  def getZoneAccess(auth: AuthPrincipal, zone: Zone): AccessLevel
}
