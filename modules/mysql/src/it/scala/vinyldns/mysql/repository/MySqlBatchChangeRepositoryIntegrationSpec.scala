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

package vinyldns.mysql.repository

import java.util.UUID

import cats.effect._
import org.joda.time.DateTime
import org.scalatest._
import scalikejdbc.DB
import vinyldns.core.domain.record.{AAAAData, AData, RecordData, RecordType}
import vinyldns.core.domain.batch._
import vinyldns.core.TestZoneData.okZone
import vinyldns.core.TestMembershipData.okAuth
import vinyldns.core.domain.{SingleChangeError, ZoneDiscoveryError}
import vinyldns.mysql.TestMySqlInstance

class MySqlBatchChangeRepositoryIntegrationSpec
    extends WordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with Inspectors
    with OptionValues {

  private var repo: BatchChangeRepository = _

  import SingleChangeStatus._
  import RecordType._

  object TestData {
    def generateSingleAddChange(recordType: RecordType,
                                recordData: RecordData,
                                status: SingleChangeStatus = Pending,
                                errors: List[SingleChangeError] = List.empty): SingleAddChange =
      SingleAddChange(
        Some(okZone.id),
        Some(okZone.name),
        Some("test"),
        "test.somezone.com.",
        recordType, 3600,
        recordData,
        status,
        None,
        None,
        None,
        errors)

    val deleteChange: SingleDeleteChange =
      SingleDeleteChange(
        Some(okZone.id),
        Some(okZone.name),
        Some("delete"),
        "delete.somezone.com.",
        A,
        Pending,
        None,
        None,
        None)


    def randomChangeList: List[SingleChange] = List(
      generateSingleAddChange(A, AData("1.2.3.4"), Pending, List(SingleChangeError(ZoneDiscoveryError("test err")))),
      generateSingleAddChange(A, AData("1.2.3.40"), Complete),
      generateSingleAddChange(AAAA, AAAAData("2001:558:feed:beef:0:0:0:1"), Pending),
      deleteChange.copy(id = UUID.randomUUID().toString))

    def randomBatchChange(changes: List[SingleChange] = randomChangeList): BatchChange = BatchChange(
      okAuth.userId,
      okAuth.signedInUser.userName,
      Some("description"),
      DateTime.now,
      changes,
      Some(UUID.randomUUID().toString),
      BatchChangeApprovalStatus.AutoApproved,
      Some(UUID.randomUUID().toString),
      Some("review comment"),
      Some(DateTime.now.plusSeconds(2))
    )

    val bcARecords: BatchChange = randomBatchChange()

    def randomBatchChangeWithList(singleChanges: List[SingleChange]): BatchChange =
      bcARecords.copy(id = UUID.randomUUID().toString, changes = singleChanges)

    val pendingBatchChange: BatchChange = randomBatchChange().copy(createdTimestamp = DateTime.now)

    val completeBatchChange: BatchChange = randomBatchChangeWithList(
      randomBatchChange().changes.map(_.complete("recordChangeId", "recordSetId")))
      .copy(createdTimestamp = DateTime.now.plusMillis(1000))

    val failedBatchChange: BatchChange =
      randomBatchChangeWithList(randomBatchChange().changes.map(_.withFailureMessage("failed")))
        .copy(createdTimestamp = DateTime.now.plusMillis(100000))

    val partialFailureBatchChange: BatchChange = randomBatchChangeWithList(
      randomBatchChange().changes.take(2).map(_.complete("recordChangeId", "recordSetId"))
        ++ randomBatchChange().changes.drop(2).map(_.withFailureMessage("failed"))
    ).copy(createdTimestamp = DateTime.now.plusMillis(1000000))


    // listing/ordering changes
    val timeBase: DateTime = DateTime.now
    val change_one: BatchChange = pendingBatchChange.copy(createdTimestamp = timeBase,
      approvalStatus = BatchChangeApprovalStatus.PendingApproval)
    val change_two: BatchChange =
      completeBatchChange.copy(createdTimestamp = timeBase.plus(1000), ownerGroupId = None)
    val otherUserBatchChange: BatchChange = randomBatchChange().copy(userId = "Other",
        createdTimestamp = timeBase.plus(50000))
    val change_three: BatchChange = failedBatchChange.copy(createdTimestamp = timeBase.plus(100000))
    val change_four: BatchChange = partialFailureBatchChange.copy(createdTimestamp = timeBase.plus(1000000))
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
    actual.ownerGroupId shouldBe expected.ownerGroupId
    actual.approvalStatus shouldBe expected.approvalStatus
    actual.reviewerId shouldBe expected.reviewerId
    actual.reviewComment shouldBe expected.reviewComment
    actual.reviewTimestamp match {
      case Some(art) => art.getMillis shouldBe expected.reviewTimestamp.get.getMillis +- 2000
      case None => actual.reviewTimestamp shouldBe expected.reviewTimestamp
    }
  }

  private def areSame(actual: BatchChangeSummary, expected: BatchChangeSummary): Assertion = {
    actual.comments shouldBe expected.comments
    actual.id shouldBe expected.id
    actual.status shouldBe expected.status
    actual.userId shouldBe expected.userId
    actual.userName shouldBe expected.userName
    actual.createdTimestamp.getMillis shouldBe expected.createdTimestamp.getMillis +- 2000
    actual.ownerGroupId shouldBe expected.ownerGroupId
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

  "MySqlBatchChangeRepository" should {
    "save batch changes and single changes" in {
      repo.save(bcARecords).unsafeRunSync() shouldBe bcARecords
    }

    "update batch change" in {
      val batchChange = randomBatchChange(List())
      val singleChanges = batchChange.changes.map {
        case sad: SingleAddChange => sad.copy(recordName = sad.recordName.map(name => s"updated-$name"))
        case sdc: SingleDeleteChange => sdc.copy(recordName = sdc.recordName.map(name => s"updated-$name"))
      }
      val updatedBatch = batchChange.copy(comments = Some("updated comments"),
        ownerGroupId = Some("new-owner-group-id"), approvalStatus = BatchChangeApprovalStatus.ManuallyRejected,
        reviewerId = Some("reviewer-id"), reviewComment = Some("updated reviewer comment"),
        reviewTimestamp = Some(DateTime.now), changes = singleChanges)

      val f = for {
        _ <- repo.save(batchChange)
        _ <- repo.save(updatedBatch)
        getBatch <- repo.getBatchChange(batchChange.id)
      } yield getBatch

      areSame(f.unsafeRunSync(), Some(updatedBatch))
    }

    "get a batch change by ID" in {
      val f =
        for {
          _ <- repo.save(bcARecords)
          retrieved <- repo.getBatchChange(bcARecords.id)
        } yield retrieved

      areSame(f.unsafeRunSync(), Some(bcARecords))
    }

    "save/get a batch change with empty comments, ownerGroup, reviewerId, reviewComment, reviewTimeStamp" in {
      val testBatch = randomBatchChange().copy(comments = None, ownerGroupId = None, reviewerId = None,
        reviewComment = None, reviewTimestamp = None)
      val f =
        for {
          _ <- repo.save(testBatch)
          retrieved <- repo.getBatchChange(testBatch.id)
        } yield retrieved

      areSame(f.unsafeRunSync(), Some(testBatch))
    }

    "save/get a batch change with BatchChangeApprovalStatus.PendingApproval" in {
      val testBatch = randomBatchChange().copy(approvalStatus = BatchChangeApprovalStatus.PendingApproval)
      val f =
        for {
          _ <- repo.save(testBatch)
          retrieved <- repo.getBatchChange(testBatch.id)
        } yield retrieved

      areSame(f.unsafeRunSync(), Some(testBatch))
    }

    "save/get a batch change with BatchChangeApprovalStatus.ManuallyApproved" in {
      val testBatch = randomBatchChange().copy(approvalStatus = BatchChangeApprovalStatus.ManuallyApproved)
      val f =
        for {
          _ <- repo.save(testBatch)
          retrieved <- repo.getBatchChange(testBatch.id)
        } yield retrieved

      areSame(f.unsafeRunSync(), Some(testBatch))
    }

    "save/get a batch change with BatchChangeApprovalStatus.ManuallyRejected" in {
      val testBatch = randomBatchChange().copy(approvalStatus = BatchChangeApprovalStatus.ManuallyRejected)
      val f =
        for {
          _ <- repo.save(testBatch)
          retrieved <- repo.getBatchChange(testBatch.id)
        } yield retrieved

      areSame(f.unsafeRunSync(), Some(testBatch))
    }

    "return none if a batch change is not found by ID" in {
      repo.getBatchChange("doesnotexist").unsafeRunSync() shouldBe empty
    }

    "get single changes by list of ID" in {
      val f =
        for {
          _ <- repo.save(bcARecords)
          retrieved <- repo.getSingleChanges(bcARecords.changes.map(_.id))
        } yield retrieved

      f.unsafeRunSync() shouldBe bcARecords.changes
    }

    "not fail on get empty list of single changes" in {
      val f = repo.getSingleChanges(List())

      f.unsafeRunSync() shouldBe List()
    }

    "get single changes should match order from batch changes" in {
      val batchChange = randomBatchChange()
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

      val (maybeBatchChange, singleChanges) = f.unsafeRunSync()
      maybeBatchChange.value.changes shouldBe singleChanges
    }

    "update single changes" in {
      val batchChange = randomBatchChange()
      val completed = batchChange.changes.map(_.complete("aaa", "bbb"))
      val f =
        for {
          _ <- repo.save(batchChange)
          _ <- repo.updateSingleChanges(completed)
          retrieved <- repo.getSingleChanges(completed.map(_.id))
        } yield retrieved

      f.unsafeRunSync() shouldBe completed
    }

    "not fail on empty update single changes" in {
      val f = repo.updateSingleChanges(List())

      f.unsafeRunSync() shouldBe None
    }

    "update some changes in a batch" in {
      val batchChange = randomBatchChange()
      val completed = batchChange.changes.take(2).map(_.complete("recordChangeId", "recordSetId"))
      val incomplete = batchChange.changes.drop(2)
      val f =
        for {
          _ <- repo.save(batchChange)
          _ <- repo.updateSingleChanges(completed)
          retrieved <- repo.getSingleChanges(batchChange.changes.map(_.id))
        } yield retrieved

      f.unsafeRunSync() shouldBe completed ++ incomplete
    }

    "return the batch when updating single changes" in {
      val batchChange = randomBatchChange()
      val completed = batchChange.changes.take(2).map(_.complete("recordChangeId", "recordSetId"))
      val f =
        for {
          _ <- repo.save(batchChange)
          updated <- repo.updateSingleChanges(completed)
          saved <- repo.getBatchChange(batchChange.id)
        } yield (updated, saved)

      val (retrieved, saved) = f.unsafeRunSync
      retrieved shouldBe saved
    }

    "return no batch when single changes list is empty" in {
      val f = repo.updateSingleChanges(List.empty)
      f.unsafeRunSync shouldBe None
    }

    "get all batch change summaries" in {
      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved <- repo.getBatchChangeSummaries(None)
        } yield retrieved

      // from most recent descending
      val expectedChanges = BatchChangeSummaryList(
        List(
          BatchChangeSummary(change_four),
          BatchChangeSummary(change_three),
          BatchChangeSummary(otherUserBatchChange),
          BatchChangeSummary(change_two),
          BatchChangeSummary(change_one))
      )

      areSame(f.unsafeRunSync(), expectedChanges)
    }

    "get batch change summaries by approval status" in {
      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved <- repo.getBatchChangeSummaries(
            None,
            approvalStatus = Some(BatchChangeApprovalStatus.PendingApproval))
        } yield retrieved

      // from most recent descending
      val expectedChanges = BatchChangeSummaryList(
        List(BatchChangeSummary(change_one))
      )

      areSame(f.unsafeRunSync(), expectedChanges)
    }

    "get batch change summaries by user ID" in {
      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved <- repo.getBatchChangeSummaries(Some(pendingBatchChange.userId))
        } yield retrieved

      // from most recent descending
      val expectedChanges = BatchChangeSummaryList(
        List(
          BatchChangeSummary(change_four),
          BatchChangeSummary(change_three),
          BatchChangeSummary(change_two),
          BatchChangeSummary(change_one))
      )

      areSame(f.unsafeRunSync(), expectedChanges)
    }

    "get batch change summaries by user ID with maxItems" in {
      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved <- repo.getBatchChangeSummaries(Some(pendingBatchChange.userId), maxItems = 3)
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

      areSame(f.unsafeRunSync(), expectedChanges)
    }

    "get batch change summaries by user ID with explicit startFrom" in {
      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved <- repo.getBatchChangeSummaries(
            Some(pendingBatchChange.userId),
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

      areSame(f.unsafeRunSync(), expectedChanges)
    }

    "get batch change summaries by user ID with explicit startFrom and maxItems" in {
      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved <- repo.getBatchChangeSummaries(
            Some(pendingBatchChange.userId),
            startFrom = Some(1),
            maxItems = 1)
        } yield retrieved

      // sorted from most recent descending. startFrom uses zero-based indexing.
      // Expect to get only the second batch change, change_3.
      // Expect the ID of the next batch change to be 2.
      val expectedChanges =
        BatchChangeSummaryList(List(BatchChangeSummary(change_three)), Some(1), Some(2), 1)

      areSame(f.unsafeRunSync(), expectedChanges)
    }

    "get second page of batch change summaries by user ID" in {
      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved1 <- repo.getBatchChangeSummaries(
            Some(pendingBatchChange.userId),
            maxItems = 1)
          retrieved2 <- repo.getBatchChangeSummaries(
            Some(pendingBatchChange.userId),
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
        None
      )
      val retrieved = f.unsafeRunSync()
      areSame(retrieved._1, expectedChanges)
      areSame(retrieved._2, secondPageExpectedChanges)
    }

    "get batch change summaries by user ID and approval status" in {
      val f =
        for {
          _ <- repo.save(change_one)
          _ <- repo.save(change_two)
          _ <- repo.save(change_three)
          _ <- repo.save(change_four)
          _ <- repo.save(otherUserBatchChange)

          retrieved <- repo.getBatchChangeSummaries(Some(pendingBatchChange.userId),
            approvalStatus = Some(BatchChangeApprovalStatus.AutoApproved))
        } yield retrieved

      // from most recent descending
      val expectedChanges = BatchChangeSummaryList(
        List(
          BatchChangeSummary(change_four),
          BatchChangeSummary(change_three),
          BatchChangeSummary(change_two))
      )

      areSame(f.unsafeRunSync(), expectedChanges)
    }

    "return empty list if a batch change summary is not found by user ID" in {
      val batchChangeSummaries = repo.getBatchChangeSummaries(Some("doesnotexist")).unsafeRunSync()
      batchChangeSummaries.batchChanges shouldBe empty
    }
    "properly status check (pending)" in {
      val chg = randomBatchChange(
        List(generateSingleAddChange(A, AData("1.2.3.4"), Complete),
          generateSingleAddChange(A, AData("2.2.3.4"), Pending),
          generateSingleAddChange(A, AData("3.2.3.4"), Complete)
        )
      )
      val saved =
        for {
          _ <- repo.save(chg)
          retrieved <- repo.getBatchChange(chg.id)
        } yield retrieved

      saved.unsafeRunSync().get.status shouldBe BatchChangeStatus.Pending
    }
    "properly status check (pending) when approvalStatus is PendingApproval" in {
      val chg = randomBatchChange().copy(approvalStatus = BatchChangeApprovalStatus.PendingApproval)
      val saved =
        for {
          _ <- repo.save(chg)
          retrieved <- repo.getBatchChange(chg.id)
        } yield retrieved

      saved.unsafeRunSync().get.status shouldBe BatchChangeStatus.Pending
    }
    "properly status check (complete)" in {
      val chg = randomBatchChange(
        List(generateSingleAddChange(A, AData("1.2.3.4"), Complete),
          generateSingleAddChange(A, AData("2.2.3.4"), Complete),
          generateSingleAddChange(A, AData("3.2.3.4"), Complete))
      )
      val saved =
        for {
          _ <- repo.save(chg)
          retrieved <- repo.getBatchChange(chg.id)
        } yield retrieved

      saved.unsafeRunSync().get.status shouldBe BatchChangeStatus.Complete
    }
    "properly status check (failed)" in {
      val chg = randomBatchChange(
        List(generateSingleAddChange(A, AData("1.2.3.4"), Failed),
          generateSingleAddChange(A, AData("2.2.3.4"), Failed),
          generateSingleAddChange(A, AData("3.2.3.4"), Failed))
      )
      val saved =
        for {
          _ <- repo.save(chg)
          retrieved <- repo.getBatchChange(chg.id)
        } yield retrieved

      saved.unsafeRunSync().get.status shouldBe BatchChangeStatus.Failed
    }
    "properly status check (failed) when approval status is ManuallyRejected" in {
      val chg = randomBatchChange().copy(approvalStatus = BatchChangeApprovalStatus.ManuallyRejected)
      val saved =
        for {
          _ <- repo.save(chg)
          retrieved <- repo.getBatchChange(chg.id)
        } yield retrieved

      saved.unsafeRunSync().get.status shouldBe BatchChangeStatus.Failed
    }
  }
}
