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

package vinyldns.api.repository.mysql

import java.util.UUID

import cats.effect._
import org.joda.time.DateTime
import org.scalatest._
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import scalikejdbc.DB
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.api.domain.dns.DnsConversions
import vinyldns.core.domain.record.{AAAAData, AData, RecordType}
import vinyldns.api.{GroupTestData, ResultHelpers, VinylDNSTestData}
import vinyldns.core.domain.batch._

class JdbcBatchChangeRepositoryIntegrationSpec
    extends WordSpec
    with BeforeAndAfterAll
    with DnsConversions
    with VinylDNSTestData
    with GroupTestData
    with ResultHelpers
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with Inspectors
    with OptionValues {

  private var repo: BatchChangeRepository = _
  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  import SingleChangeStatus._
  import RecordType._

  object TestData {

    val okAuth: AuthPrincipal = okGroupAuth
    val notAuth: AuthPrincipal = dummyUserAuth

    val zoneID: String = "someZoneId"
    val zoneName: String = "somezone.com."

    val sc1: SingleAddChange =
      SingleAddChange(
        zoneID,
        zoneName,
        "test",
        "test.somezone.com.",
        A,
        3600,
        AData("1.2.3.4"),
        Pending,
        None,
        None,
        None)

    val sc2: SingleAddChange =
      SingleAddChange(
        zoneID,
        zoneName,
        "test",
        "test.somezone.com.",
        A,
        3600,
        AData("1.2.3.40"),
        Pending,
        None,
        None,
        None)

    val sc3: SingleAddChange =
      SingleAddChange(
        zoneID,
        zoneName,
        "test",
        "test.somezone.com.",
        AAAA,
        300,
        AAAAData("2001:558:feed:beef:0:0:0:1"),
        Pending,
        None,
        None,
        None)

    val deleteChange: SingleDeleteChange =
      SingleDeleteChange(
        zoneID,
        zoneName,
        "delete",
        "delete.somezone.com.",
        A,
        Pending,
        None,
        None,
        None)

    def randomBatchChange: BatchChange = BatchChange(
      okAuth.userId,
      okAuth.signedInUser.userName,
      Some("description"),
      DateTime.now,
      List(
        sc1.copy(id = UUID.randomUUID().toString),
        sc2.copy(id = UUID.randomUUID().toString),
        sc3.copy(id = UUID.randomUUID().toString),
        deleteChange.copy(id = UUID.randomUUID().toString)
      )
    )

    val bcARecords: BatchChange = randomBatchChange

    def randomBatchChangeWithList(singlechanges: List[SingleChange]): BatchChange =
      bcARecords.copy(id = UUID.randomUUID().toString, changes = singlechanges)

    val pendingBatchChange: BatchChange = randomBatchChange.copy(createdTimestamp = DateTime.now)

    val completeBatchChange: BatchChange = randomBatchChangeWithList(
      randomBatchChange.changes.map(_.complete("recordChangeId", "recordSetId")))
      .copy(createdTimestamp = DateTime.now.plusMillis(1000))

    val failedBatchChange: BatchChange =
      randomBatchChangeWithList(randomBatchChange.changes.map(_.withFailureMessage("failed")))
        .copy(createdTimestamp = DateTime.now.plusMillis(100000))

    val partialFailureBatchChange: BatchChange = randomBatchChangeWithList(
      randomBatchChange.changes.take(2).map(_.complete("recordChangeId", "recordSetId"))
        ++ randomBatchChange.changes.drop(2).map(_.withFailureMessage("failed"))
    ).copy(createdTimestamp = DateTime.now.plusMillis(1000000))
  }

  import TestData._

  override protected def beforeAll(): Unit =
    repo = TestMySqlInstance.batchChangeRepository

  override protected def beforeEach(): Unit =
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM batch_change")
      s.executeUpdate("DELETE FROM single_change")
    }

  private def areSame(a: Option[BatchChange], e: Option[BatchChange]): Assertion = {
    a shouldBe defined
    e shouldBe defined

    val actual = a.get
    val expected = e.get

    areSame(actual, expected)
  }

  /* have to account for the database being different granularity than the JVM for DateTime */
  private def areSame(actual: BatchChange, expected: BatchChange): Assertion = {
    (actual.changes should contain).theSameElementsInOrderAs(expected.changes)
    actual.comments shouldBe expected.comments
    actual.id shouldBe expected.id
    actual.status shouldBe expected.status
    actual.userId shouldBe expected.userId
    actual.userName shouldBe expected.userId
    actual.createdTimestamp.getMillis shouldBe expected.createdTimestamp.getMillis +- 2000
  }

  private def areSame(actual: BatchChangeSummary, expected: BatchChangeSummary): Assertion = {
    actual.comments shouldBe expected.comments
    actual.id shouldBe expected.id
    actual.status shouldBe expected.status
    actual.userId shouldBe expected.userId
    actual.userName shouldBe expected.userId
    actual.createdTimestamp.getMillis shouldBe expected.createdTimestamp.getMillis +- 2000
  }

  private def areSame(
      actual: BatchChangeSummaryList,
      expected: BatchChangeSummaryList): Assertion = {
    forAll(actual.batchChanges.zip(expected.batchChanges)) { case (a, e) => areSame(a, e) }
    actual.batchChanges.length shouldBe expected.batchChanges.length
    actual.startFrom shouldBe expected.startFrom
    actual.nextId shouldBe expected.nextId
    actual.maxItems shouldBe expected.maxItems
  }

  "JdbcBatchChangeRepository" should {
    "save batch changes and single changes" in {
      val f = repo.save(bcARecords).unsafeToFuture()
      whenReady(f, timeout) { saved =>
        saved shouldBe bcARecords
      }
    }

    "get a batchchange by id" in {
      val f =
        for {
          _ <- repo.save(bcARecords)
          retrieved <- repo.getBatchChange(bcARecords.id)
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        areSame(retrieved, Some(bcARecords))
      }
    }

    "return none if a batchchange is not found by id" in {
      whenReady(repo.getBatchChange("doesnotexist").unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe empty
      }
    }

    "get singlechanges by list of id" in {
      val f =
        for {
          _ <- repo.save(bcARecords)
          retrieved <- repo.getSingleChanges(bcARecords.changes.map(_.id))
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe bcARecords.changes
      }
    }

    "not fail on get empty list of singlechanges" in {
      val f = repo.getSingleChanges(List())

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe List()
      }
    }

    "get single changes should match order from batch changes" in {
      val batchChange = randomBatchChange
      val f =
        for {
          _ <- repo.save(batchChange)
          retrieved <- repo.getBatchChange(batchChange.id)
          singleChanges <- retrieved
            .map { r =>
              repo.getSingleChanges(r.changes.map(_.id).reverse)
            }
            .getOrElse(IO.pure[List[SingleChange]](Nil))
        } yield (retrieved, singleChanges)

      whenReady(f.unsafeToFuture(), timeout) {
        case (maybeBatchChange, singleChanges) =>
          maybeBatchChange.value.changes shouldBe singleChanges
      }
    }

    "update singlechanges" in {
      val batchChange = randomBatchChange
      val completed = batchChange.changes.map(_.complete("aaa", "bbb"))
      val f =
        for {
          _ <- repo.save(batchChange)
          _ <- repo.updateSingleChanges(completed)
          retrieved <- repo.getSingleChanges(completed.map(_.id))
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe completed
      }
    }

    "not fail on empty update singlechanges" in {
      val f = repo.updateSingleChanges(List())

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe List()
      }
    }

    "update some changes in a batch" in {
      val batchChange = randomBatchChange
      val completed = batchChange.changes.take(2).map(_.complete("recordChangeId", "recordSetId"))
      val incomplete = batchChange.changes.drop(2)
      val f =
        for {
          _ <- repo.save(batchChange)
          _ <- repo.updateSingleChanges(completed)
          retrieved <- repo.getSingleChanges(batchChange.changes.map(_.id))
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe completed ++ incomplete
      }
    }

    "get batchchange summary by user id" in {
      val change_one = pendingBatchChange.copy(createdTimestamp = DateTime.now)
      val change_two = completeBatchChange.copy(createdTimestamp = DateTime.now.plusMillis(1000))
      val otherUserBatchChange =
        randomBatchChange.copy(userId = "Other", createdTimestamp = DateTime.now.plusMillis(50000))
      val change_three = failedBatchChange.copy(createdTimestamp = DateTime.now.plusMillis(100000))
      val change_four =
        partialFailureBatchChange.copy(createdTimestamp = DateTime.now.plusMillis(1000000))

      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved <- repo.getBatchChangeSummariesByUserId(pendingBatchChange.userId)
        } yield retrieved

      // from most recent descending
      val expectedChanges = BatchChangeSummaryList(
        List(
          BatchChangeSummary(change_four),
          BatchChangeSummary(change_three),
          BatchChangeSummary(change_two),
          BatchChangeSummary(change_one))
      )

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        areSame(retrieved, expectedChanges)
      }
    }

    "get batchchange summary by user id with maxItems" in {
      val change_one = pendingBatchChange.copy(createdTimestamp = DateTime.now)
      val change_two = completeBatchChange.copy(createdTimestamp = DateTime.now.plusMillis(1000))
      val otherUserBatchChange =
        randomBatchChange.copy(userId = "Other", createdTimestamp = DateTime.now.plusMillis(50000))
      val change_three = failedBatchChange.copy(createdTimestamp = DateTime.now.plusMillis(100000))
      val change_four =
        partialFailureBatchChange.copy(createdTimestamp = DateTime.now.plusMillis(1000000))

      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved <- repo.getBatchChangeSummariesByUserId(pendingBatchChange.userId, maxItems = 3)
        } yield retrieved

      // from most recent descending
      val expectedChanges = BatchChangeSummaryList(
        List(
          BatchChangeSummary(change_four),
          BatchChangeSummary(change_three),
          BatchChangeSummary(change_two)),
        None,
        Some(3),
        3
      )

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        areSame(retrieved, expectedChanges)
      }
    }

    "get batchchange summary by user id with explicit startFrom" in {
      val timeBase = DateTime.now
      val change_one = pendingBatchChange.copy(createdTimestamp = timeBase)
      val change_two = completeBatchChange.copy(createdTimestamp = timeBase.plus(1000))
      val otherUserBatchChange =
        randomBatchChange.copy(userId = "Other", createdTimestamp = timeBase.plus(50000))
      val change_three = failedBatchChange.copy(createdTimestamp = timeBase.plus(100000))
      val change_four = partialFailureBatchChange.copy(createdTimestamp = timeBase.plus(1000000))

      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved <- repo.getBatchChangeSummariesByUserId(
            pendingBatchChange.userId,
            startFrom = Some(1),
            maxItems = 3)
        } yield retrieved

      // sorted from most recent descending. startFrom uses zero-based indexing.
      // Expect to get only the second batch change, change_3.
      // No nextId because the maxItems (3) equals the number of batch changes the user has after the offset (3)
      val expectedChanges = BatchChangeSummaryList(
        List(
          BatchChangeSummary(change_three),
          BatchChangeSummary(change_two),
          BatchChangeSummary(change_one)),
        Some(1),
        None,
        3
      )

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        areSame(retrieved, expectedChanges)
      }
    }

    "get batchchange summary by user id with explicit startFrom and maxItems" in {
      val timeBase = DateTime.now
      val change_one = pendingBatchChange.copy(createdTimestamp = timeBase)
      val change_two = completeBatchChange.copy(createdTimestamp = timeBase.plus(1000))
      val otherUserBatchChange =
        randomBatchChange.copy(userId = "Other", createdTimestamp = timeBase.plus(50000))
      val change_three = failedBatchChange.copy(createdTimestamp = timeBase.plus(100000))
      val change_four = partialFailureBatchChange.copy(createdTimestamp = timeBase.plus(1000000))

      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved <- repo.getBatchChangeSummariesByUserId(
            pendingBatchChange.userId,
            startFrom = Some(1),
            maxItems = 1)
        } yield retrieved

      // sorted from most recent descending. startFrom uses zero-based indexing.
      // Expect to get only the second batch change, change_3.
      // Expect the ID of the next batch change to be 2.
      val expectedChanges =
        BatchChangeSummaryList(List(BatchChangeSummary(change_three)), Some(1), Some(2), 1)

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        areSame(retrieved, expectedChanges)
      }
    }

    "get second page of batchchange summaries by user id" in {
      val timeBase = DateTime.now
      val change_one = pendingBatchChange.copy(createdTimestamp = timeBase)
      val change_two = completeBatchChange.copy(createdTimestamp = timeBase.plus(1000))
      val otherUserBatchChange =
        randomBatchChange.copy(userId = "Other", createdTimestamp = timeBase.plus(50000))
      val change_three = failedBatchChange.copy(createdTimestamp = timeBase.plus(100000))
      val change_four = partialFailureBatchChange.copy(createdTimestamp = timeBase.plus(1000000))

      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved1 <- repo.getBatchChangeSummariesByUserId(
            pendingBatchChange.userId,
            maxItems = 1)
          retrieved2 <- repo.getBatchChangeSummariesByUserId(
            pendingBatchChange.userId,
            startFrom = retrieved1.nextId)
        } yield (retrieved1, retrieved2)

      val expectedChanges =
        BatchChangeSummaryList(List(BatchChangeSummary(change_four)), None, Some(1), 1)

      val secondPageExpectedChanges = BatchChangeSummaryList(
        List(
          BatchChangeSummary(change_three),
          BatchChangeSummary(change_two),
          BatchChangeSummary(change_one)),
        Some(1),
        None,
        100
      )

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        areSame(retrieved._1, expectedChanges)
        areSame(retrieved._2, secondPageExpectedChanges)
      }
    }

    "return empty list if a batchchange summary is not found by user id" in {
      whenReady(repo.getBatchChangeSummariesByUserId("doesnotexist").unsafeToFuture(), timeout) {
        retrieved =>
          retrieved.batchChanges shouldBe empty
      }
    }
  }
}
