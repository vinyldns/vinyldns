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

import vinyldns.api.repository.mysql.VinylDNSJDBC

import scala.concurrent.Future

// $COVERAGE-OFF$
trait BatchChangeRepository {

  def save(batch: BatchChange): Future[BatchChange]

  def getBatchChange(batchChangeId: String): Future[Option[BatchChange]]

  def getBatchChangeSummariesByUserId(
      userId: String,
      startFrom: Option[Int] = None,
      maxItems: Int = 100): Future[BatchChangeSummaryList]

  // updateSingleChanges updates status, recordSetId, recordChangeId and systemMessage (in data).
  def updateSingleChanges(singleChanges: List[SingleChange]): Future[List[SingleChange]]

  def getSingleChanges(singleChangeIds: List[String]): Future[List[SingleChange]]

}

object BatchChangeRepository {
  def apply(): BatchChangeRepository = VinylDNSJDBC.instance.batchChangeRepository
}
// $COVERAGE-ON$
