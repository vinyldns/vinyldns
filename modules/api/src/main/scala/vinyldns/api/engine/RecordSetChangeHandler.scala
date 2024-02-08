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

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import org.slf4j.LoggerFactory
import scalikejdbc.DB
import vinyldns.api.backend.dns.DnsProtocol.TryAgain
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.api.domain.record.RecordSetHelpers._
import vinyldns.core.domain.backend.{Backend, BackendResponse}
import vinyldns.core.domain.batch.{BatchChangeRepository, SingleChange}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.Zone
import vinyldns.mysql.TransactionProvider

object RecordSetChangeHandler extends TransactionProvider {

  private val logger = LoggerFactory.getLogger("vinyldns.api.engine.RecordSetChangeHandler")
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  private val outOfSyncFailureMessage: String = "This record set is out of sync with the DNS backend; sync this zone before attempting to update this record set."
  private val incompatibleRecordFailureMessage: String = "Incompatible record in DNS."
  private val syncZoneMessage: String = "This record set is out of sync with the DNS backend. Sync this zone before attempting to update this record set."
  private val recordConflictMessage: String = "Conflict due to the record being added having the same name as an NS record in the same zone."

  final case class Requeue(change: RecordSetChange) extends Throwable

  def apply(
             recordSetRepository: RecordSetRepository,
             recordChangeRepository: RecordChangeRepository,
             recordSetCacheRepository: RecordSetCacheRepository,
             batchChangeRepository: BatchChangeRepository
  )(implicit timer: Timer[IO]): (Backend, RecordSetChange) => IO[RecordSetChange] =
    (conn, recordSetChange) => {
      process(
        recordSetRepository,
        recordChangeRepository,
        recordSetCacheRepository,
        batchChangeRepository,
        conn,
        recordSetChange
      )
    }

  def process(
               recordSetRepository: RecordSetRepository,
               recordChangeRepository: RecordChangeRepository,
               recordSetCacheRepository: RecordSetCacheRepository,
               batchChangeRepository: BatchChangeRepository,
               conn: Backend,
               recordSetChange: RecordSetChange
  )(implicit timer: Timer[IO]): IO[RecordSetChange] =
    for {
      wildCardExists <- wildCardExistsForRecord(recordSetChange.recordSet, recordSetRepository)
      completedState <- fsm(
        Pending(recordSetChange),
        conn,
        wildCardExists,
        recordSetRepository,
        recordChangeRepository,
        recordSetCacheRepository
      )
      changeSet = ChangeSet(completedState.change).complete(completedState.change)
      _ <- saveChangeSet(recordSetRepository, recordChangeRepository,recordSetCacheRepository, batchChangeRepository, recordSetChange, completedState, changeSet)
    } yield completedState.change

  def saveChangeSet(
                     recordSetRepository: RecordSetRepository,
                     recordChangeRepository: RecordChangeRepository,
                     recordSetCacheRepository: RecordSetCacheRepository,
                     batchChangeRepository: BatchChangeRepository,
                     recordSetChange: RecordSetChange,
                     completedState: ProcessorState,
                     changeSet: ChangeSet
  ): IO[Unit] =
    executeWithinTransaction { db: DB =>
      for {
       _ <-  recordSetRepository.apply(db, changeSet)
       _ <-  recordChangeRepository.save(db, changeSet)
       _ <-  recordSetCacheRepository.save(db, changeSet)
       // Update single changes within this transaction to rollback the changes made to recordset and record change repo
       // when exception occurs while updating single changes
       singleBatchChanges <- batchChangeRepository.getSingleChanges(
         recordSetChange.singleBatchChangeIds
       )
       singleChangeStatusUpdates = updateBatchStatuses(singleBatchChanges, completedState.change)
       _ <- batchChangeRepository.updateSingleChanges(singleChangeStatusUpdates)
      } yield ()
    }

  def updateBatchStatuses(
      singleChanges: List[SingleChange],
      recordSetChange: RecordSetChange
  ): List[SingleChange] =
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

  private final case class Retrying(change: RecordSetChange) extends ProcessorState

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

  // Failure to process change. Permitted to retry.
  final case class Retry(change: RecordSetChange) extends ProcessingStatus

  def syncAndGetProcessingStatusFromDnsBackend(
                                                change: RecordSetChange,
                                                conn: Backend,
                                                recordSetRepository: RecordSetRepository,
                                                recordChangeRepository: RecordChangeRepository,
                                                recordSetCacheRepository: RecordSetCacheRepository,
                                                performSync: Boolean = false
  ): IO[ProcessingStatus] = {
    def isDnsMatch(dnsResult: List[RecordSet], recordSet: RecordSet, zoneName: String): Boolean =
      dnsResult.exists(matches(_, recordSet, zoneName))

    // Determine processing status by comparing request against disposition of DNS backend
    def getProcessingStatus(
        change: RecordSetChange,
        existingRecords: List[RecordSet]
    ): IO[ProcessingStatus] = IO {
      change.changeType match {
        case RecordSetChangeType.Create =>
          if (existingRecords.isEmpty) ReadyToApply(change)
          else if (isDnsMatch(existingRecords, change.recordSet, change.zone.name))
            AlreadyApplied(change)
          else Failure(change, incompatibleRecordFailureMessage)

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
                outOfSyncFailureMessage
              )
          }

        case RecordSetChangeType.Delete =>
          if (existingRecords.nonEmpty) ReadyToApply(change) // we have a record set, move forward
          else AlreadyApplied(change) // we did not find the record set, so already applied
      }
    }

    conn.resolve(change.recordSet.name, change.zone.name, change.recordSet.typ).attempt.flatMap {
      case Right(existingRecords) =>
        if (performSync) {
          for {
            dnsBackendRRSet <- syncAgainstDnsBackend(
              change,
              existingRecords,
              recordSetRepository,
              recordChangeRepository,
              recordSetCacheRepository
            )
            processingStatus <- getProcessingStatus(change, dnsBackendRRSet)
          } yield processingStatus
        } else {
          getProcessingStatus(change, existingRecords)
        }
      case Left(_: TryAgain) => IO(Retry(change))
      case Left(error) => IO(Failure(change, error.getMessage))
    }
  }

  private def fsm(
      state: ProcessorState,
      conn: Backend,
      wildcardExists: Boolean,
      recordSetRepository: RecordSetRepository,
      recordChangeRepository: RecordChangeRepository,
      recordSetCacheRepository: RecordSetCacheRepository
  )(
      implicit timer: Timer[IO]
  ): IO[ProcessorState] = {

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
    def bypassValidation(
        skip: => ProcessorState
    )(orElse: => IO[ProcessorState]): IO[ProcessorState] = {
      val toRun =
        if (wildcardExists || state.change.recordSet.typ == RecordType.NS) IO.pure(skip) else orElse

      toRun.flatMap(
        fsm(
          _,
          conn,
          wildcardExists,
          recordSetRepository,
          recordChangeRepository,
          recordSetCacheRepository
        )
      )
    }

    state match {
      case Pending(change) =>
        logger.info(s"CHANGE PENDING; ${getChangeLog(change)}")
        bypassValidation(Validated(change))(
          orElse = validate(
            change,
            conn,
            recordSetRepository,
            recordChangeRepository,
            recordSetCacheRepository
          )
        )

      case Validated(change) =>
        logger.info(s"CHANGE VALIDATED; ${getChangeLog(change)}")
        apply(change, conn).flatMap(
          fsm(
            _,
            conn,
            wildcardExists,
            recordSetRepository,
            recordChangeRepository,
            recordSetCacheRepository
          )
        )

      case Applied(change) =>
        logger.info(s"CHANGE APPLIED; ${getChangeLog(change)}")
        bypassValidation(Verified(change.successful))(
          orElse = verify(
            change,
            conn,
            recordSetRepository,
            recordChangeRepository,
            recordSetCacheRepository
          )
        )

      case Verified(change) =>
        logger.info(s"CHANGE VERIFIED; ${getChangeLog(change)}")
        // if we got here, we are good.  Note: Complete could still mean that the change failed
        IO.pure(Completed(change))

      case done: Completed =>
        logger.info(s"CHANGE COMPLETED; ${getChangeLog(done.change)}")
        IO.pure(done)

      case Retrying(change) =>
        logger.info(s"CHANGE RETRYING; ${getChangeLog(change)}")
        IO.raiseError(Requeue(change))
    }
  }

  private def syncAgainstDnsBackend(
                                     change: RecordSetChange,
                                     dnsBackendRRSet: List[RecordSet],
                                     recordSetRepository: RecordSetRepository,
                                     recordChangeRepository: RecordChangeRepository,
                                     recordSetCacheRepository: RecordSetCacheRepository
  ): IO[List[RecordSet]] = {

    /*
     * Sync current VinylDNS disposition of DNS records against DNS backend for the following conditions:
     * - Create record from database for DELETE or UPDATE request if missing in database but exists in DNS backend
     * - Delete record from database for ADD request if exists in database but does not exist in DNS backend
     */
    def syncDnsBackendRRSet(
                             storedRRSet: Option[RecordSet],
                             dnsBackendRRSet: Option[RecordSet],
                             zone: Zone,
                             recordSetRepository: RecordSetRepository,
                             recordChangeRepository: RecordChangeRepository,
                             recordSetCacheRepository: RecordSetCacheRepository
    ): IO[Unit] = {
      val recordSetToSync = (storedRRSet, dnsBackendRRSet) match {
        case (Some(savedRs), None) if change.changeType == RecordSetChangeType.Create =>
          Some(RecordSetChangeGenerator.forRecordSyncDelete(savedRs, zone))
        case (None, Some(existingRs))
            if change.changeType == RecordSetChangeType.Delete ||
              change.changeType == RecordSetChangeType.Update =>
          Some(RecordSetChangeGenerator.forRecordSyncAdd(existingRs, zone))
        case _ => None
      }

      // Generate record set and record set change to store
      recordSetToSync
        .map { rsc =>
          val changeSet = ChangeSet(rsc)

          executeWithinTransaction { db: DB =>
            for {
              _ <-  recordSetRepository.apply(db, changeSet)
              _ <-  recordChangeRepository.save(db, changeSet)
              _ <-  recordSetCacheRepository.save(db, changeSet)

            } yield ()
          }
        }
        .getOrElse(IO.unit)
    }

    for {
      storedRRSet <- recordSetRepository
        .getRecordSetsByName(change.zoneId, change.recordSet.name)
      _ <- syncDnsBackendRRSet(
        storedRRSet.find(_.typ == change.recordSet.typ),
        dnsBackendRRSet.headOption,
        change.zone,
        recordSetRepository,
        recordChangeRepository,
        recordSetCacheRepository
      )
    } yield dnsBackendRRSet
  }

  /* Step 1: Validate the change hasn't already been applied */
  private def validate(
                        change: RecordSetChange,
                        conn: Backend,
                        recordSetRepository: RecordSetRepository,
                        recordChangeRepository: RecordChangeRepository,
                        recordSetCacheRepository: RecordSetCacheRepository
  ): IO[ProcessorState] =
    syncAndGetProcessingStatusFromDnsBackend(
      change,
      conn,
      recordSetRepository,
      recordChangeRepository,
      recordSetCacheRepository
    ).map {
      case AlreadyApplied(_) => Completed(change.successful)
      case ReadyToApply(_) => Validated(change)
      case Failure(_, message) =>
        if(message == outOfSyncFailureMessage || message == incompatibleRecordFailureMessage){
          Completed(
            change.failed(
              syncZoneMessage
            )
          )
        } else if (message == "referral") {
          Completed(
            change.failed(
              recordConflictMessage
            )
          )
        } else {
          Completed(
            change.failed(
              s"""Failed validating update to DNS for change "${change.id}": "${change.recordSet.name}": """ + message
            )
          )
        }
      case Retry(_) => Retrying(change)
    }

  /* Step 2: Apply the change to the dns backend */
  private def apply(change: RecordSetChange, conn: Backend): IO[ProcessorState] =
    conn.applyChange(change).attempt.map {
      case Right(BackendResponse.Retry(_)) =>
        Retrying(change)
      case Right(BackendResponse.NoError(_)) =>
        Applied(change)
      case Left(error) =>
        Completed(
          change.failed(
            s"Failed applying update to DNS for change ${change.id}:${change.recordSet.name}: ${error.getMessage}"
          )
        )
    }

  /* Step 3: Verify the record was created. If the ProcessorState is applied or failed we requeue the record.*/
  private def verify(
      change: RecordSetChange,
      conn: Backend,
      recordSetRepository: RecordSetRepository,
      recordChangeRepository: RecordChangeRepository,
      recordSetCacheRepository: RecordSetCacheRepository
  ): IO[ProcessorState] =
    syncAndGetProcessingStatusFromDnsBackend(
      change,
      conn,
      recordSetRepository,
      recordChangeRepository,
      recordSetCacheRepository
    ).map {
      case AlreadyApplied(_) => Completed(change.successful)
      case Failure(_, message) =>
        Completed(
          change.failed(
            s"""Failed verifying update to DNS for change "${change.id}":"${change.recordSet.name}": $message"""
          )
        )
      case _ => Retrying(change)
    }

  private def getChangeLog(change: RecordSetChange): String = {
    val sb = new StringBuilder
    sb.append("changeId='").append(change.id).append("'")
    sb.append(" changeType='").append(change.changeType).append("'")
    sb.append(" recordSetName='").append(change.recordSet.name).append("'")
    sb.append(" recordSetId='").append(change.recordSet.id).append("'")
    sb.append(" zoneName='").append(change.zone.name).append("'")
    sb.append(" zoneId='").append(change.zone.id).append("'")
    sb.append(" isTestZone='").append(change.zone.isTest).append("'")
    sb.toString
  }

  private def wildCardExistsForRecord(
      recordSet: RecordSet,
      recordSetRepository: RecordSetRepository
  ): IO[Boolean] =
    (
      recordSetRepository.getRecordSets(recordSet.zoneId, "*", recordSet.typ),
      recordSetRepository.getRecordSets(recordSet.zoneId, "*", RecordType.CNAME)
    ).parMapN(_ ++ _)
      .map(_.nonEmpty)
}
