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

package vinyldns.api.engine

import cats.effect._
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.mockito.Matchers.any
import org.mockito.Mockito.{doReturn, verify}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import vinyldns.api.repository.InMemoryBatchChangeRepository
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record._
import vinyldns.core.notifier.{AllNotifiers, Notification, Notifier}

import scala.concurrent.ExecutionContext

class BatchChangeHandlerSpec
    extends AnyWordSpec
    with MockitoSugar
    with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)

  private val batchRepo = new InMemoryBatchChangeRepository
  private val mockNotifier = mock[Notifier]
  private val notifiers = AllNotifiers(List(mockNotifier))

  private val addChange = SingleAddChange(
    Some("zoneId"),
    Some("zoneName"),
    Some("recordName"),
    "recordName.zoneName",
    RecordType.A,
    300,
    AData("1.1.1.1"),
    SingleChangeStatus.Complete,
    None,
    Some("recordChangeId"),
    Some("recordSetId"),
    List(),
    "changeId"
  )

  private val completedBatchChange = BatchChange(
    "userId",
    "userName",
    Some("comments"),
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    List(addChange),
    Some("ownerGroupId"),
    BatchChangeApprovalStatus.AutoApproved
  )

  override protected def beforeEach(): Unit =
    batchRepo.clear()

  "notify on batch change complete" in {
    doReturn(IO.unit).when(mockNotifier).notify(any[Notification[_]])

    batchRepo.save(completedBatchChange).unsafeRunSync()

    BatchChangeHandler
      .process(batchRepo, notifiers, BatchChangeCommand(completedBatchChange.id))
      .unsafeRunSync()

    verify(mockNotifier).notify(Notification(completedBatchChange))
  }

  "notify on failure" in {
    doReturn(IO.unit).when(mockNotifier).notify(any[Notification[_]])

    val partiallyFailedBatchChange =
      completedBatchChange.copy(changes = List(addChange.copy(status = SingleChangeStatus.Failed)))

    batchRepo.save(partiallyFailedBatchChange).unsafeRunSync()

    BatchChangeHandler
      .process(batchRepo, notifiers, BatchChangeCommand(partiallyFailedBatchChange.id))
      .unsafeRunSync()

    verify(mockNotifier).notify(Notification(partiallyFailedBatchChange))
  }
}
