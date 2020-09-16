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

import vinyldns.core.domain.record.RecordSet
import vinyldns.dns.DnsConversions

object RecordSetHelpers {

  def matches(left: RecordSet, right: RecordSet, zoneName: String): Boolean = {

    val isSame = for {
      thisDnsRec <- DnsConversions.toDnsRecords(left, zoneName)
      otherDnsRec <- DnsConversions.toDnsRecords(right, zoneName)
    } yield {
      val rsMatch = otherDnsRec.toSet == thisDnsRec.toSet
      val namesMatch = matchesNameQualification(left, right)
      val headersMatch = left.ttl == right.ttl && left.typ == right.typ
      rsMatch && namesMatch && headersMatch
    }

    isSame.getOrElse(false)
  }

  def matchesNameQualification(left: RecordSet, right: RecordSet): Boolean =
    isFullyQualified(left.name) == isFullyQualified(right.name)

  def isFullyQualified(name: String): Boolean = name.endsWith(".")

}
