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

import cats.effect.IO
import vinyldns.core.domain.batch.BatchChangeApprovalStatus.BatchChangeApprovalStatus
import vinyldns.core.domain.batch.BatchChangeStatus.BatchChangeStatus
import vinyldns.core.repository.Repository

// $COVERAGE-OFF$
trait BatchChangeRepository extends Repository {

  def save(batch: BatchChange): IO[BatchChange]

  def getBatchChange(batchChangeId: String): IO[Option[BatchChange]]

  def getBatchChangeSummaries(
      userId: Option[String],
      startFrom: Option[Int] = None,
      maxItems: Int = 100,
      batchStatus: Option[BatchChangeStatus] = None,
      approvalStatus: Option[BatchChangeApprovalStatus] = None
  ): IO[BatchChangeSummaryList]

  // updateSingleChanges updates status, recordSetId, recordChangeId and systemMessage (in data).
  def updateSingleChanges(singleChanges: List[SingleChange]): IO[Option[BatchChange]]

  def getSingleChanges(singleChangeIds: List[String]): IO[List[SingleChange]]

}
// $COVERAGE-ON$
