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

import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.domain.record.{AData, RecordType}

class BatchChangeSpec extends WordSpec with Matchers {
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
    None)
  private val failedChange = pendingChange.copy(status = SingleChangeStatus.Failed)
  private val completeChange = pendingChange.copy(status = SingleChangeStatus.Complete)

  private val batchChangeBase = BatchChange(
    "userId",
    "userName",
    None,
    DateTime.now,
    List(pendingChange, failedChange, completeChange),
    approvalStatus = BatchChangeApprovalStatus.AutoApproved)

  "BatchChange" should {
    "calculate Pending status based on SingleChanges" in {
      batchChangeBase.status shouldBe BatchChangeStatus.Pending
    }
    "calculate PartialFailure status based on SingleChanges" in {
      batchChangeBase
        .copy(changes = List(completeChange, failedChange))
        .status shouldBe BatchChangeStatus.PartialFailure
    }
    "calculate Failed status based on SingleChanges" in {
      batchChangeBase.copy(changes = List(failedChange)).status shouldBe BatchChangeStatus.Failed
    }
    "calculate Complete status based on SingleChanges" in {
      batchChangeBase
        .copy(changes = List(completeChange))
        .status shouldBe BatchChangeStatus.Complete
    }
    "calculate Pending status when approval status is PendingApproval" in {
      batchChangeBase
        .copy(approvalStatus = BatchChangeApprovalStatus.PendingApproval)
        .status shouldBe BatchChangeStatus.Pending
    }
    "calculate Failed status when approval status is ManuallyRejected" in {
      batchChangeBase
        .copy(approvalStatus = BatchChangeApprovalStatus.ManuallyRejected)
        .status shouldBe BatchChangeStatus.Failed
    }
    "calculate Scheduled status when approval status is PendingApproval and scheduled time is set" in {
      batchChangeBase
        .copy(
          approvalStatus = BatchChangeApprovalStatus.PendingApproval,
          scheduledTime = Some(DateTime.now))
        .status shouldBe BatchChangeStatus.Scheduled
    }
  }
}
