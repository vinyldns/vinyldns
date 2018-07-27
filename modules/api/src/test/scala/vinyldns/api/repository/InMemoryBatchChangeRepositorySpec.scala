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
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.CatsHelpers
import vinyldns.api.domain.batch.SingleChangeStatus.Pending
import vinyldns.api.domain.batch._
import vinyldns.api.domain.record.AData
import vinyldns.api.domain.record.RecordType._

class InMemoryBatchChangeRepositorySpec extends WordSpec with Matchers with CatsHelpers {

  private val addChange1 = SingleAddChange(
    "zoneid",
    "zoneName",
    "apex.test.com.",
    "apex.test.com.",
    A,
    100,
    AData("1.1.1.1"),
    Pending,
    None,
    None,
    None,
    "addChangeId1")
  private val addChange2 =
    addChange1.copy(inputName = "test.test.com.", recordName = "test", id = "addChangeId2")
  private val batchChange = BatchChange(
    "userId",
    "username",
    None,
    DateTime.now,
    List(addChange1, addChange2),
    "batchChangeId")

  private val failedChange = addChange1.copy(status = SingleChangeStatus.Failed)
  private val completeChange = addChange2.copy(status = SingleChangeStatus.Complete)

  private val underTest = InMemoryBatchChangeRepository

  "InMemoryBatchChangeRepository" should {
    "save a batch change, retrieve" in {
      val saved = await(underTest.save(batchChange))
      saved shouldBe batchChange

      val checkBatch = await(underTest.getBatchChange(batchChange.id))
      checkBatch shouldBe Some(batchChange)

      val checkSingleChanges = await(underTest.getSingleChanges(List(addChange1.id, addChange2.id)))
      checkSingleChanges.length shouldBe 2
      checkSingleChanges should contain theSameElementsAs List(addChange1, addChange2)
    }

    "update single changes" in {
      val update1 =
        addChange1.copy(recordChangeId = Some("aRecordChange"), status = SingleChangeStatus.Pending)
      val update2 = addChange2.copy(
        recordChangeId = Some("aRecordChangeAgain"),
        status = SingleChangeStatus.Pending)

      await(underTest.updateSingleChanges(List(update1, update2)))

      val checkBatch = await(underTest.getBatchChange(batchChange.id))
      val expected = batchChange.copy(changes = List(update1, update2))
      checkBatch shouldBe Some(expected)

      val checkSingleChanges = await(underTest.getSingleChanges(List(addChange1.id, addChange2.id)))
      checkSingleChanges.length shouldBe 2
      checkSingleChanges should contain theSameElementsAs List(update1, update2)
    }
  }

  "list batch change summaries with correct status when complete" in {
    underTest.clear()

    val changes = List(completeChange, completeChange)
    val completeBatchChange = batchChange.copy(changes = changes)
    val saved = await(underTest.save(completeBatchChange))
    saved shouldBe completeBatchChange

    val batchChangeSummaryList = await(underTest.getBatchChangeSummariesByUserId("userId"))
    val expected = BatchChangeSummaryList(List(BatchChangeSummary(saved)))

    batchChangeSummaryList shouldBe expected
    batchChangeSummaryList.batchChanges(0).status shouldBe BatchChangeStatus.Complete
  }

  "list batch change summaries with correct status when pending" in {
    underTest.clear()

    val changes = List(completeChange, addChange1)
    val pendingBatchChange = batchChange.copy(changes = changes)
    val saved = await(underTest.save(pendingBatchChange))
    saved shouldBe pendingBatchChange

    val batchChangeSummaryList = await(underTest.getBatchChangeSummariesByUserId("userId"))
    val expected = BatchChangeSummaryList(List(BatchChangeSummary(saved)))

    batchChangeSummaryList shouldBe expected
    batchChangeSummaryList.batchChanges(0).status shouldBe BatchChangeStatus.Pending
  }

  "list batch change summaries with correct status when failed" in {
    underTest.clear()

    val changes = List(failedChange, failedChange)
    val failedBatchChange = batchChange.copy(changes = changes)
    val saved = await(underTest.save(failedBatchChange))
    saved shouldBe failedBatchChange

    val batchChangeSummaryList = await(underTest.getBatchChangeSummariesByUserId("userId"))
    val expected = BatchChangeSummaryList(List(BatchChangeSummary(saved)))

    batchChangeSummaryList shouldBe expected
    batchChangeSummaryList.batchChanges(0).status shouldBe BatchChangeStatus.Failed
  }

  "list batch change summaries with correct status when partial failure" in {
    underTest.clear()

    val changes = List(completeChange, failedChange)
    val partiallyFailedBatchChange = batchChange.copy(changes = changes)
    val saved = await(underTest.save(partiallyFailedBatchChange))
    saved shouldBe partiallyFailedBatchChange

    val batchChangeSummaryList = await(underTest.getBatchChangeSummariesByUserId("userId"))
    val expected = BatchChangeSummaryList(List(BatchChangeSummary(saved)))

    batchChangeSummaryList shouldBe expected
    batchChangeSummaryList.batchChanges(0).status shouldBe BatchChangeStatus.PartialFailure
  }
}
