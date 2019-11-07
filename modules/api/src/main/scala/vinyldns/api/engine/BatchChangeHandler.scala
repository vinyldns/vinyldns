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
import cats.implicits._
import org.slf4j.LoggerFactory
import vinyldns.core.domain.batch.{
  BatchChange,
  BatchChangeCommand,
  BatchChangeRepository,
  BatchChangeStatus
}
import vinyldns.core.notifier.{AllNotifiers, Notification}

object BatchChangeHandler {
  private val logger = LoggerFactory.getLogger("vinyldns.api.engine.BatchChangeHandler")

  def apply(
      batchChangeRepository: BatchChangeRepository,
      notifiers: AllNotifiers): BatchChangeCommand => IO[Option[BatchChange]] =
    batchChangeId => {
      process(
        batchChangeRepository: BatchChangeRepository,
        notifiers,
        batchChangeId
      )
    }

  def process(
      batchChangeRepository: BatchChangeRepository,
      notifiers: AllNotifiers,
      batchChangeCommand: BatchChangeCommand): IO[Option[BatchChange]] =
    for {
      batchChange <- batchChangeRepository.getBatchChange(batchChangeCommand.id)
      completedState <- fsm(notifiers, Pending(batchChange, batchChangeCommand))
    } yield completedState.change

  private sealed trait BatchChangeProcessorState {
    def change: Option[BatchChange]
  }

  private final case class Pending(
      change: Option[BatchChange],
      batchChangeCommand: BatchChangeCommand)
      extends BatchChangeProcessorState

  private final case class PendingBatchNotificationError(change: BatchChange) extends Throwable

  private final case class Completed(change: Option[BatchChange]) extends BatchChangeProcessorState

  private def fsm(
      notifiers: AllNotifiers,
      state: BatchChangeProcessorState): IO[BatchChangeProcessorState] =
    state match {
      case Pending(batchChange, batchChangeCommand) =>
        notify(notifiers, batchChange, batchChangeCommand).as(Completed(batchChange))
      case completed => IO.pure(completed)
    }

  private def notify(
      notifiers: AllNotifiers,
      change: Option[BatchChange],
      batchChangeCommand: BatchChangeCommand): IO[Unit] =
    change match {
      case Some(pendingBatch)
          if pendingBatch.status == BatchChangeStatus.PendingProcessing ||
            pendingBatch.status == BatchChangeStatus.PendingReview =>
        logger.info(s"Notification not sent for pending batch: ${getChangeLog(pendingBatch)}.")
        IO.raiseError(PendingBatchNotificationError(pendingBatch))
      case Some(completedBatch) =>
        logger.info(s"Sending notification for batch: ${getChangeLog(completedBatch)}.")
        notifiers.notify(Notification(completedBatch))
      case None =>
        logger.error(
          s"Notification not sent since batch change with ID ${batchChangeCommand.id} not found.")
        IO.unit
    }

  private def getChangeLog(change: BatchChange): String = {
    val sb = new StringBuilder
    sb.append("changeId='").append(change.id).append("'")
    sb.append(" status='").append(change.status).append("'")
    sb.toString
  }
}
