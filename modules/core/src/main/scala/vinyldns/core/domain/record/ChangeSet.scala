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

package vinyldns.core.domain.record

import java.util.UUID

import java.time.Instant
import java.time.temporal.ChronoUnit

sealed trait ChangeSetStatus {
  def intValue: Int = this match {
    case ChangeSetStatus.Pending => 0
    case ChangeSetStatus.Complete => 2
    case ChangeSetStatus.Applied => 100 // must be greater than all the others
  }
}

object ChangeSetStatus {
  case object Pending extends ChangeSetStatus
  case object Complete extends ChangeSetStatus
  case object Applied extends ChangeSetStatus

  def fromInt(value: Int): ChangeSetStatus = value match {
    case 0 => ChangeSetStatus.Pending
    case 2 => ChangeSetStatus.Complete
    case 100 => ChangeSetStatus.Applied
  }
}

case class ChangeSet(
    id: String,
    zoneId: String,
    createdTimestamp: Long,
    processingTimestamp: Long,
    changes: Seq[RecordSetChange],
    status: ChangeSetStatus
) {

  def complete(change: RecordSetChange): ChangeSet = {
    val updatedChanges = this.changes.filterNot(_.id == change.id) :+ change
    if (isFinished)
      copy(changes = updatedChanges, status = ChangeSetStatus.Complete)
    else
      copy(changes = updatedChanges)
  }

  def isFinished: Boolean = changes.forall(_.isDone)
}

object ChangeSet {

  /**
    * Convenience method for creating a change set for a single RecordSetChange
    *
    * This allows us to be backward compatible with v1 of VinylDNS
    */
  def apply(change: RecordSetChange): ChangeSet =
    ChangeSet(
      id = UUID.randomUUID().toString,
      zoneId = change.zoneId,
      createdTimestamp = Instant.now.truncatedTo(ChronoUnit.MILLIS).toEpochMilli,
      processingTimestamp = 0L,
      changes = Seq(change),
      status = ChangeSetStatus.Pending
    )

  def apply(changes: Seq[RecordSetChange]): ChangeSet =
    ChangeSet(
      id = UUID.randomUUID().toString,
      zoneId = changes.head.zoneId,
      createdTimestamp = Instant.now.truncatedTo(ChronoUnit.MILLIS).toEpochMilli,
      processingTimestamp = 0L,
      changes = changes,
      status = ChangeSetStatus.Pending
    )
}
