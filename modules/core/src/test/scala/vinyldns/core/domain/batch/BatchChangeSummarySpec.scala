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

package vinyldns.core.domain.batch

import java.time.Instant
import vinyldns.core.domain.record.{AData, RecordType}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.time.temporal.ChronoUnit

class BatchChangeSummarySpec extends AnyWordSpec with Matchers {
  private val pendingChange = SingleAddChange(
    Some("zoneid"),
    Some("zonename"),
    Some("rname"),
    "inputname",
    RecordType.A,
    123,
    AData("2.2.2.2"),
    SingleChangeStatus.Pending,
    None,
    None,
    None
  )
  private val failedChange = pendingChange.copy(status = SingleChangeStatus.Failed)
  private val completeChange = pendingChange.copy(status = SingleChangeStatus.Complete)

  private val batchChange = BatchChange(
    "userId",
    "userName",
    Some("comments"),
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    List(pendingChange, failedChange, completeChange),
    Some("groupId"),
    BatchChangeApprovalStatus.AutoApproved,
    None,
    None,
    None,
    "id"
  )

  private val batchChangeInfo = BatchChangeInfo(batchChange, Some("groupName"))

  "BatchChangeSummary" should {
    "convert from batch change to batch change summary" in {
      import batchChange._
      val underTest = BatchChangeSummary(batchChange)

      underTest.userId shouldBe userId
      underTest.userName shouldBe userName
      underTest.comments shouldBe comments
      underTest.createdTimestamp shouldBe createdTimestamp
      underTest.totalChanges shouldBe 3
      underTest.ownerGroupId shouldBe ownerGroupId
      underTest.id shouldBe id
      underTest.ownerGroupName shouldBe None
    }

    "convert from batch change info to batch change summary" in {
      import batchChangeInfo._
      val underTest = BatchChangeSummary(batchChangeInfo)

      underTest.userId shouldBe userId
      underTest.userName shouldBe userName
      underTest.comments shouldBe comments
      underTest.createdTimestamp shouldBe createdTimestamp
      underTest.totalChanges shouldBe 3
      underTest.ownerGroupId shouldBe ownerGroupId
      underTest.id shouldBe id
      underTest.ownerGroupName shouldBe Some("groupName")
    }
  }
}
