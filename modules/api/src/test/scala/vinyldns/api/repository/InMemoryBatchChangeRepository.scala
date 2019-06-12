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

package vinyldns.api.repository

import org.joda.time.DateTime
import vinyldns.core.domain.batch._

import scala.collection.concurrent
import cats.effect._
import cats.implicits._

class InMemoryBatchChangeRepository extends BatchChangeRepository {

  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_.isAfter(_))

  case class StoredBatchChange(
      userId: String,
      userName: String,
      comments: Option[String],
      createdTimestamp: DateTime,
      changes: List[String],
      ownerGroupId: Option[String],
      id: String)
  object StoredBatchChange {
    def apply(batchChange: BatchChange): StoredBatchChange =
      new StoredBatchChange(
        batchChange.userId,
        batchChange.userName,
        batchChange.comments,
        batchChange.createdTimestamp,
        batchChange.changes.map(_.id),
        batchChange.ownerGroupId,
        batchChange.id
      )
  }

  private val batches = new concurrent.TrieMap[String, StoredBatchChange]
  private val singleChangesMap = new concurrent.TrieMap[String, SingleChange]

  def save(batch: BatchChange): IO[BatchChange] =
    IO.pure {
        batches.put(batch.id, StoredBatchChange(batch))
        batch.changes.foreach(ch => singleChangesMap.put(ch.id, ch))
      }
      .map(_ => batch)

  def getBatchChange(batchChangeId: String): IO[Option[BatchChange]] =
    IO.pure {
      val storedChange = batches.get(batchChangeId)
      val singleChangesFromRepo = for {
        sc <- storedChange.toList
        ids <- sc.changes
        changes <- singleChangesMap.get(ids)
      } yield changes

      storedChange.map { sc =>
        BatchChange(
          sc.userId,
          sc.userName,
          sc.comments,
          sc.createdTimestamp,
          singleChangesFromRepo,
          sc.ownerGroupId,
          BatchChangeApprovalStatus.AutoApproved,
          None,
          None,
          None,
          sc.id
        )
      }
    }

  def updateSingleChanges(singleChanges: List[SingleChange]): IO[Option[BatchChange]] =
    IO.pure {
        singleChanges.foreach(ch => singleChangesMap.put(ch.id, ch))
      }
      .flatMap { _ =>
        (for {
          sc <- singleChanges.headOption
          storedChange <- batches.values.find(_.changes.contains(sc.id))
        } yield getBatchChange(storedChange.id)).sequence.map(_.flatten)
      }

  def getSingleChanges(singleChangeIds: List[String]): IO[List[SingleChange]] = {
    val changes = singleChangeIds.flatMap(singleChangesMap.get)
    IO.pure(changes)
  }

  def getBatchChangeSummariesByUserId(
      userId: String,
      startFrom: Option[Int] = None,
      maxItems: Int = 100): IO[BatchChangeSummaryList] = {
    val userBatchChanges = batches.values.toList.filter(_.userId == userId)
    val batchChangeSummaries = for {
      sc <- userBatchChanges
      ids = sc.changes
      changes = ids.flatMap(singleChangesMap.get)
      batchChange = BatchChange(
        sc.userId,
        sc.userName,
        sc.comments,
        sc.createdTimestamp,
        changes,
        sc.ownerGroupId,
        BatchChangeApprovalStatus.AutoApproved,
        None,
        None,
        None,
        sc.id)
    } yield BatchChangeSummary(batchChange)
    val sorted = batchChangeSummaries.sortBy(_.createdTimestamp)
    val start = startFrom.getOrElse(0)
    val until = maxItems + start
    val limited = sorted.slice(start, until)
    val nextId = if (limited.size < maxItems) None else Some(start + limited.size)
    IO.pure(
      BatchChangeSummaryList(limited, startFrom = startFrom, nextId = nextId, maxItems = maxItems))
  }

  def clear(): Unit = {
    batches.clear()
    singleChangesMap.clear()
  }
}
