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

package vinyldns.api

import org.joda.time.DateTime
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._

trait VinylDNSTestHelpers {
  val fakeTime: DateTime = new DateTime(2010, 1, 1, 0, 0)

  def anonymize(recordSet: RecordSet): RecordSet =
    recordSet.copy(id = "a", created = fakeTime, updated = None)

  def anonymize(recordSetChange: RecordSetChange): RecordSetChange =
    recordSetChange.copy(
      id = "a",
      created = fakeTime,
      recordSet = anonymize(recordSetChange.recordSet),
      updates = recordSetChange.updates.map(anonymize),
      zone = anonymizeTimeOnly(recordSetChange.zone)
    )

  def anonymize(changeSet: ChangeSet): ChangeSet =
    changeSet.copy(
      id = "a",
      createdTimestamp = fakeTime.getMillis,
      processingTimestamp = 0,
      changes = changeSet.changes
        .map(anonymize)
        .sortBy(rs => (rs.recordSet.name, rs.recordSet.typ))
    )

  def anonymizeTimeOnly(zone: Zone): Zone = {
    val newUpdate = zone.updated.map(_ => fakeTime)
    val newLatestSync = zone.latestSync.map(_ => fakeTime)
    zone.copy(created = fakeTime, updated = newUpdate, latestSync = newLatestSync)
  }
}
