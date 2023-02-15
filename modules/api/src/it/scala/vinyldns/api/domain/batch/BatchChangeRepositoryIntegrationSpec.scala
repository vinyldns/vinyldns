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

package vinyldns.api.domain.batch

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import vinyldns.api.MySqlApiIntegrationSpec
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain.batch.{
  BatchChange,
  BatchChangeApprovalStatus,
  SingleAddChange,
  SingleChangeStatus
}
import vinyldns.core.domain.record.{AData, RecordType}
import vinyldns.mysql.MySqlIntegrationSpec

class BatchChangeRepositoryIntegrationSpec
    extends MySqlApiIntegrationSpec
    with MySqlIntegrationSpec
    with Matchers
    with AnyWordSpecLike {

  import vinyldns.api.domain.DomainValidations._

  "MySqlBatchChangeRepository" should {
    "successfully save single change with max-length input name" in {
      val batchChange = BatchChange(
        okUser.id,
        okUser.userName,
        None,
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        List(
          SingleAddChange(
            Some("some-zone-id"),
            Some("zone-name"),
            Some("record-name"),
            "a" * HOST_MAX_LENGTH,
            RecordType.A,
            300,
            AData("1.1.1.1"),
            SingleChangeStatus.Pending,
            None,
            None,
            None
          )
        ),
        approvalStatus = BatchChangeApprovalStatus.AutoApproved
      )

      val f = for {
        saveBatchChangeResult <- batchChangeRepository.save(batchChange)
        getSingleChangesResult <- batchChangeRepository.getSingleChanges(
          batchChange.changes.map(_.id)
        )
      } yield (saveBatchChangeResult, getSingleChangesResult)
      val (saveResponse, singleChanges) = f.unsafeRunSync()

      saveResponse shouldBe batchChange
      singleChanges.foreach(_.inputName.length shouldBe HOST_MAX_LENGTH)
    }
  }
}
