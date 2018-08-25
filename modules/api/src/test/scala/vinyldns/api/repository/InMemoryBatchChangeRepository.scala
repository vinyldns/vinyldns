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
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.domain.batch
import vinyldns.api.domain.batch._

import scala.collection.concurrent
import cats.effect._

object InMemoryBatchChangeRepository extends BatchChangeRepository {

  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_.isAfter(_))

  case class StoredBatchChange(
      userId: String,
      userName: String,
      comments: Option[String],
      createdTimestamp: DateTime,
      changes: List[String],
      id: String)
  object StoredBatchChange {
    def apply(batchChange: BatchChange): StoredBatchChange =
      new StoredBatchChange(
        batchChange.userId,
        batchChange.userName,
        batchChange.comments,
        batchChange.createdTimestamp,
        batchChange.changes.map(_.id),
        batchChange.id)
  }

  private val batches = new concurrent.TrieMap[String, StoredBatchChange]
  private val singleChangesMap = new concurrent.TrieMap[String, SingleChange]
  val logger: Logger = LoggerFactory.getLogger("BatchChangeRepo")

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
        batch.BatchChange(
          sc.userId,
          sc.userName,
          sc.comments,
          sc.createdTimestamp,
          singleChangesFromRepo,
          sc.id)
      }
    }

  def updateSingleChanges(singleChanges: List[SingleChange]): IO[List[SingleChange]] =
    IO.pure {
        singleChanges.foreach(ch => singleChangesMap.put(ch.id, ch))
      }
      .map(_ => singleChanges)

  def getSingleChanges(singleChangeIds: List[String]): IO[List[SingleChange]] = {
    val changes = singleChangeIds.flatMap(singleChangesMap.get)

    val notFound = singleChangeIds.toSet -- changes.map(_.id).toSet
    if (notFound.nonEmpty) {
      // log 1st 5; we shouldnt need all, and if theres a ton it could get long
      logger.error(
        s"!!! Could not find all SingleChangeIds in DB call; missing IDs: ${notFound.take(5)} !!!")
    }

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
      batchChange = batch.BatchChange(
        sc.userId,
        sc.userName,
        sc.comments,
        sc.createdTimestamp,
        changes,
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
