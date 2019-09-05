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

import vinyldns.api.domain.zone.ZoneRecordValidations
import vinyldns.core.domain.DomainHelpers
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.record.{PTRData, RecordData}
import vinyldns.core.domain.zone.Zone

import scala.util.matching.Regex

final case class GlobalAcl(groupIds: List[String], fqdnRegexList: List[String])

final case class GlobalAcls(acls: List[GlobalAcl]) {

  // Create a map of Group ID -> Regexes valid for that group id
  private val aclMap: Map[String, List[Regex]] = {
    val tuples = for {
      acl <- acls
      groupId <- acl.groupIds
      regex = ZoneRecordValidations.toCaseIgnoredRegexList(acl.fqdnRegexList)
    } yield groupId -> regex

    tuples.groupBy(_._1).map {
      case (groupId, regexes) => groupId -> regexes.flatMap(_._2)
    }
  }

  def hasGlobalAcl(authPrincipal: AuthPrincipal, fqdn: String): Boolean = {
    val regexList = authPrincipal.memberGroupIds.flatMap(aclMap.getOrElse(_, List.empty)).toList
    val normalizedFqdn = DomainHelpers.ensureTrailingDot(fqdn.toLowerCase)
    ZoneRecordValidations.isStringInRegexList(regexList, normalizedFqdn)
  }

  def hasGlobalAcl(authPrincipal: AuthPrincipal, recordName: String, zone: Zone): Boolean = {
    val fqdn = if (recordName.endsWith(".")) recordName else s"$recordName.${zone.name}"
    hasGlobalAcl(authPrincipal, fqdn)
  }

  def hasGlobalReverseAcl(authPrincipal: AuthPrincipal, records: List[RecordData]): Boolean =
    records
      .collect {
        case p: PTRData => p.ptrdname
      }
      .exists(hasGlobalAcl(authPrincipal, _))
}
