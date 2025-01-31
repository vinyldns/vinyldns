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

import vinyldns.api.Interfaces.Result
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.zone._

trait ZoneServiceAlgebra {

  def connectToZone(
      ConnectZoneInput: ConnectZoneInput,
      auth: AuthPrincipal
  ): Result[ZoneCommandResult]

  def handleGenerateZoneRequest(request: ZoneGenerationInput,  auth : AuthPrincipal): Result[Unit]

  def updateZone(updateZoneInput: UpdateZoneInput, auth: AuthPrincipal): Result[ZoneCommandResult]

  def deleteZone(zoneId: String, auth: AuthPrincipal): Result[ZoneCommandResult]

  def syncZone(zoneId: String, auth: AuthPrincipal): Result[ZoneCommandResult]

  def getZone(zoneId: String, auth: AuthPrincipal): Result[ZoneInfo]

  def getCommonZoneDetails(zoneId: String, auth: AuthPrincipal): Result[ZoneDetails]

  def getZoneByName(zoneName: String, auth: AuthPrincipal): Result[ZoneInfo]

  def listZones(
      authPrincipal: AuthPrincipal,
      nameFilter: Option[String],
      startFrom: Option[String],
      maxItems: Int,
      searchByAdminGroup: Boolean,
      ignoreAccess: Boolean,
      includeReverse: Boolean
  ): Result[ListZonesResponse]

  def listDeletedZones(
                        authPrincipal: AuthPrincipal,
                        nameFilter: Option[String],
                        startFrom: Option[String],
                        maxItems: Int,
                        ignoreAccess: Boolean
                      ): Result[ListDeletedZoneChangesResponse]

  def listZoneChanges(
      zoneId: String,
      authPrincipal: AuthPrincipal,
      startFrom: Option[String],
      maxItems: Int
  ): Result[ListZoneChangesResponse]

  def addACLRule(
      zoneId: String,
      aclRuleInfo: ACLRuleInfo,
      authPrincipal: AuthPrincipal
  ): Result[ZoneCommandResult]

  def deleteACLRule(
      zoneId: String,
      aclRuleInfo: ACLRuleInfo,
      authPrincipal: AuthPrincipal
  ): Result[ZoneCommandResult]

  def getBackendIds(): Result[List[String]]

  def listFailedZoneChanges(
                             authPrincipal: AuthPrincipal,
                             startFrom: Int,
                             maxItems: Int
                           ): Result[ListFailedZoneChangesResponse]
}
