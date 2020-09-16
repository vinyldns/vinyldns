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

import vinyldns.dns.DnsConversions._
import vinyldns.api.domain
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.core.domain.record.{RecordSet, RecordSetChange}
import vinyldns.api.domain.record.RecordSetHelpers._
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.zone.{Zone, ZoneChange}

object ZoneView {

  def apply(zone: Zone, recordSets: List[RecordSet]): ZoneView =
    domain.zone.ZoneView(
      zone,
      recordSets
        .groupBy { rs =>
          val nameFormatted = relativize(rs.name, zone.name)
          (nameFormatted, rs.typ)
        }
        .map {
          case (key, lst) => key -> collapseRecordSets(lst)
        }
    )

  private def collapseRecordSets(lst: List[RecordSet]): RecordSet =
    lst.tail.foldLeft(lst.head)((h, r) => h.copy(records = h.records ++ r.records))
}

case class ZoneView(zone: Zone, recordSetsMap: Map[(String, RecordType), RecordSet]) {

  def diff(otherView: ZoneView): Seq[RecordSetChange] = {

    def toAddRecordSetChange(recordSet: RecordSet): RecordSetChange =
      RecordSetChangeGenerator.forZoneSyncAdd(recordSet, zone)

    def toDeleteRecordSetChange(recordSet: RecordSet): RecordSetChange =
      RecordSetChangeGenerator.forZoneSyncDelete(recordSet, zone)

    def toUpdateRecordSetChange(newRecordSet: RecordSet, oldRecordSet: RecordSet): RecordSetChange =
      RecordSetChangeGenerator.forZoneSyncUpdate(oldRecordSet, newRecordSet, zone)

    def areDifferent(left: RecordSet, right: RecordSet): Boolean = !matches(left, right, zone.name)

    def includeRecordSetIfChanged(key: (String, RecordType)): Option[RecordSetChange] =
      for {
        ourRecordSet <- recordSetsMap.get(key)
        theirRecordSet <- otherView.recordSetsMap.get(key)
        if areDifferent(ourRecordSet, theirRecordSet)
      } yield toUpdateRecordSetChange(theirRecordSet, ourRecordSet)

    val toAdd = (otherView.recordSetsMap -- recordSetsMap.keySet).values
      .map(toAddRecordSetChange)
      .toSeq

    val toDelete = (recordSetsMap -- otherView.recordSetsMap.keySet).values
      .map(toDeleteRecordSetChange)
      .toSeq

    val toUpdate = otherView.recordSetsMap.keySet
      .intersect(recordSetsMap.keySet)
      .flatMap(includeRecordSetIfChanged)

    toAdd ++ toDelete ++ toUpdate
  }
}

case class ZoneDiff(zoneChange: ZoneChange, recordSetChanges: Seq[RecordSetChange])
