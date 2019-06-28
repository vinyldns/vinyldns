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

import vinyldns.api.domain.batch.BatchChangeInterfaces.BatchResult
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.batch.{BatchChange, BatchChangeInfo, BatchChangeSummaryList}

// $COVERAGE-OFF$
trait BatchChangeServiceAlgebra {
  def applyBatchChange(
      batchChangeInput: BatchChangeInput,
      auth: AuthPrincipal): BatchResult[BatchChange]

  def getBatchChange(id: String, auth: AuthPrincipal): BatchResult[BatchChangeInfo]

  def listBatchChangeSummaries(
      auth: AuthPrincipal,
      startFrom: Option[Int],
      maxItems: Int,
      ignoreAccess: Boolean): BatchResult[BatchChangeSummaryList]

  def rejectBatchChange(
      batchChangeId: String,
      authPrincipal: AuthPrincipal,
      rejectBatchChangeInput: Option[RejectBatchChangeInput]): BatchResult[BatchChange]
}
// $COVERAGE-ON$
