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

import cats.effect.IO
import fs2._
import org.slf4j.LoggerFactory
import vinyldns.api.domain.batch.{BatchChangeRepository, SingleChange}
import vinyldns.api.domain.dns.DnsProtocol.NoError
import vinyldns.api.domain.record._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object RecordSetChangeHandler {

  private val logger = LoggerFactory.getLogger("vinyldns.api.engine.RecordSetChangeHandler")

  def apply(
      recordSetRepository: RecordSetRepository,
      recordChangeRepository: RecordChangeRepository,
      batchChangeRepository: BatchChangeRepository)(
      implicit executionContext: ExecutionContext,
      scheduler: Scheduler): (DnsConnector, RecordSetChange) => IO[RecordSetChange] =
    (conn, recordSetChange) => {
      process(
        recordSetRepository,
        recordChangeRepository,
        batchChangeRepository,
        conn,
        recordSetChange)
    }

  def process(
      recordSetRepository: RecordSetRepository,
      recordChangeRepository: RecordChangeRepository,
      batchChangeRepository: BatchChangeRepository,
      conn: DnsConnector,
      recordSetChange: RecordSetChange)(
      implicit executionContext: ExecutionContext,
      scheduler: Scheduler): IO[RecordSetChange] =
    for {
      wildCardExists <- wildCardExistsForRecord(recordSetChange.recordSet, recordSetRepository)
      completedState <- fsm(Pending(recordSetChange), conn, wildCardExists)
      changeSet = ChangeSet(completedState.change).complete(completedState.change)
      _ <- IO.fromFuture(IO(recordChangeRepository.save(changeSet)))
      _ <- IO.fromFuture(IO(recordSetRepository.apply(changeSet)))
      singleBatchChanges <- IO.fromFuture(
        IO(batchChangeRepository.getSingleChanges(recordSetChange.singleBatchChangeIds)))
      singleChangeStatusUpdates = updateBatchStatuses(singleBatchChanges, completedState.change)
      _ <- IO.fromFuture(IO(batchChangeRepository.updateSingleChanges(singleChangeStatusUpdates)))
    } yield completedState.change

  def updateBatchStatuses(
      singleChanges: List[SingleChange],
      recordSetChange: RecordSetChange): List[SingleChange] =
    recordSetChange.status match {
      case RecordSetChangeStatus.Complete =>
        singleChanges.map(_.complete(recordSetChange.id, recordSetChange.recordSet.id))
      case RecordSetChangeStatus.Failed =>
        singleChanges.map(_.withProcessingError(recordSetChange.systemMessage, recordSetChange.id))
      case _ => singleChanges
    }

  private sealed trait ProcessorState {
    def change: RecordSetChange
  }

  private final case class Pending(change: RecordSetChange) extends ProcessorState

  private final case class Validated(change: RecordSetChange) extends ProcessorState

  private final case class Applied(change: RecordSetChange) extends ProcessorState

  private final case class Verified(change: RecordSetChange) extends ProcessorState

  private final case class Completed(change: RecordSetChange) extends ProcessorState

  private case class ProcessingError(message: String)

  sealed trait ProcessingStatus

  // Failure to process change. Abort processing.
  final case class Failure(change: RecordSetChange, message: String) extends ProcessingStatus

  // Change has already been applied. Abort processing and return successful response to user.
  final case class AlreadyApplied(change: RecordSetChange) extends ProcessingStatus

  // Change can proceed to the next state of processing. In the Verified ProcessorState,
  // ReadyToApply will attempt to retry until max retries limit is reached,
  // at which point the response will be returned.
  final case class ReadyToApply(change: RecordSetChange) extends ProcessingStatus

  def getProcessingStatus(change: RecordSetChange, dnsConnector: DnsConnector)(
      implicit executionContext: ExecutionContext): IO[ProcessingStatus] = {
    def isDnsMatch(dnsResult: List[RecordSet], recordSet: RecordSet, zoneName: String): Boolean =
      dnsResult.exists(_.matches(recordSet, zoneName))

    dnsConnector.dnsResolve(change.recordSet.name, change.zone.name, change.recordSet.typ).map {
      case Right(existingRecords) =>
        change.changeType match {
          case RecordSetChangeType.Create =>
            if (existingRecords.isEmpty) ReadyToApply(change)
            else if (isDnsMatch(existingRecords, change.recordSet, change.zone.name))
              AlreadyApplied(change)
            else Failure(change, "Incompatible record already exists in DNS.")

          case RecordSetChangeType.Update =>
            if (isDnsMatch(existingRecords, change.recordSet, change.zone.name))
              AlreadyApplied(change)
            else {
              // record must not exist in the DNS backend, or be synced if it exists
              val canApply = existingRecords.isEmpty ||
                change.updates.exists(oldRs => isDnsMatch(existingRecords, oldRs, change.zone.name))

              if (canApply) ReadyToApply(change)
              else
                Failure(
                  change,
                  "This record set is out of sync with the DNS backend; " +
                    "sync this zone before attempting to update this record set."
                )
            }

          case RecordSetChangeType.Delete =>
            if (existingRecords.nonEmpty) ReadyToApply(change) // we have a record set, move forward
            else AlreadyApplied(change) // we did not find the record set, so already applied
        }
      case Left(error) => Failure(change, error.getMessage)
    }
  }

  private def fsm(state: ProcessorState, conn: DnsConnector, wildcardExists: Boolean)(
      implicit executionContext: ExecutionContext,
      scheduler: Scheduler): IO[ProcessorState] = {

    /**
      * If there is a wildcard record with the same type, then we skip validation and verification steps.
      *
      * This is because DNS query will yield a false positive (that the record already exists even though
      * it does not.
      *
      * There is no way to know if the response is due to a wildcard record or if it is due to a
      * real record existing.  The only check we can perform is to bypass the validate step
      * if there is a wild card record.
      *
      * We also skip verification for NS records.  We cannot create delegations for zones hosted on the
      * same ANS if we attempt to validate NS records
      */
    def bypassValidation(skip: => ProcessorState)(
        orElse: => IO[ProcessorState]): IO[ProcessorState] = {
      val toRun =
        if (wildcardExists || state.change.recordSet.typ == RecordType.NS) IO.pure(skip) else orElse

      toRun.flatMap(fsm(_, conn, wildcardExists))
    }

    state match {
      case Pending(change) =>
        logger.info(
          s"PENDING for change ${change.id}:${change.recordSet.name} change type ${change.changeType}")

        bypassValidation(Validated(change))(orElse = validate(change, conn))

      case Validated(change) =>
        logger.info(
          s"VALIDATED for change ${change.id}:${change.recordSet.name} change type ${change.changeType}")
        apply(change, conn).flatMap(fsm(_, conn, wildcardExists))

      case Applied(change) =>
        logger.info(
          s"APPLIED for change ${change.id}:${change.recordSet.name} change type ${change.changeType}")

        bypassValidation(Verified(change.successful))(orElse = verify(change, conn))

      case Verified(change) =>
        logger.info(
          s"VERIFIED for change ${change.id}:${change.recordSet.name} change type ${change.changeType}")
        // if we got here, we are good.  Note: Complete could still mean that the change failed
        IO.pure(Completed(change))

      case done: Completed =>
        logger.info(
          s"COMPLETED for change ${done.change.id}:${done.change.recordSet.name} change type " +
            s"${done.change.changeType} with result ${done.change.status}")
        IO.pure(done)
    }
  }

  /* Step 1: Validate the change hasn't already been applied */
  private def validate(change: RecordSetChange, dnsConnector: DnsConnector)(
      implicit executionContext: ExecutionContext): IO[ProcessorState] =
    getProcessingStatus(change, dnsConnector).map {
      case AlreadyApplied(_) => Completed(change.successful)
      case ReadyToApply(_) => Validated(change)
      case Failure(_, message) =>
        Completed(change.failed(
          s"Failed validating update to DNS for change ${change.id}:${change.recordSet.name}: " + message))
    }

  /* Step 2: Apply the change to the dns backend */
  private def apply(change: RecordSetChange, dnsConnector: DnsConnector): IO[ProcessorState] =
    dnsConnector.dnsUpdate(change).map {
      case Right(_: NoError) =>
        Applied(change)
      case Left(error) =>
        Completed(change.failed(
          s"Failed applying update to DNS for change ${change.id}:${change.recordSet.name}: ${error.getMessage}"))
    }

  /* Step 3: Verify the record was created.  We attempt 12 times over 6 seconds */
  private def verify(change: RecordSetChange, dnsConnector: DnsConnector)(
      implicit executionContext: ExecutionContext,
      scheduler: Scheduler): IO[ProcessorState] = {
    def loop(retries: Int = 11): IO[ProcessorState] =
      getProcessingStatus(change, dnsConnector).flatMap {
        case AlreadyApplied(_) => IO.pure(Completed(change.successful))
        case ReadyToApply(_) if retries <= 0 =>
          IO.pure(Completed(change.failed(s"""Failed verifying update to DNS for
               |change ${change.id}:${change.recordSet.name}: Verify out of retries.""".stripMargin)))
        case ReadyToApply(_) => scheduler.effect.delay(loop(retries - 1), 500.milliseconds)
        case Failure(_, message) =>
          IO.pure(Completed(change.failed(
            s"Failed verifying update to DNS for change ${change.id}:${change.recordSet.name}: $message")))
      }

    loop()
  }

  private def wildCardExistsForRecord(
      recordSet: RecordSet,
      recordSetRepository: RecordSetRepository)(implicit ec: ExecutionContext): IO[Boolean] =
    IO.fromFuture(
      IO(recordSetRepository.getRecordSets(recordSet.zoneId, "*", recordSet.typ).map(_.nonEmpty)))
}
