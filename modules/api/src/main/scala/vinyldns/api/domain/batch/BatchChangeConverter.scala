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

import cats.data.NonEmptyList
import cats.syntax.list._
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import vinyldns.api.domain.batch.BatchChangeInterfaces._
import vinyldns.api.domain.batch.BatchTransformations._
import vinyldns.api.domain.batch.BatchTransformations.LogicalChangeType._
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.Zone
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.RecordType.{RecordType, UNKNOWN}
import vinyldns.core.queue.MessageQueue

class BatchChangeConverter(batchChangeRepo: BatchChangeRepository, messageQueue: MessageQueue)
    extends BatchChangeConverterAlgebra {

  private val nonExistentRecordDeleteMessage: String = "This record does not exist. " +
    "No further action is required."
  private val nonExistentRecordDataDeleteMessage: String = "Record data entered does not exist. " +
    "No further action is required."
  private val failedMessage: String = "Error queueing RecordSetChange for processing"
  private val logger = LoggerFactory.getLogger(classOf[BatchChangeConverter])

  def sendBatchForProcessing(
      batchChange: BatchChange,
      existingZones: ExistingZones,
      groupedChanges: ChangeForValidationMap,
      ownerGroupId: Option[String]
  ): BatchResult[BatchConversionOutput] = {
    logger.info(
      s"Converting BatchChange [${batchChange.id}] with SingleChanges [${batchChange.changes.map(_.id)}]"
    )
    for {
      recordSetChanges <- createRecordSetChangesForBatch(
        batchChange.changes,
        existingZones,
        groupedChanges,
        batchChange.userId,
        ownerGroupId
      ).toRightBatchResult
      _ = println("recordSetChanges: ", recordSetChanges)
      _ <- allChangesWereConverted(batchChange.changes, recordSetChanges)
      _ <- batchChangeRepo
        .save(batchChange)
        .toBatchResult // need to save the change before queueing, backend processing expects the changes to exist
      queued <- putChangesOnQueue(recordSetChanges, batchChange.id)
      changeToStore = updateWithQueueingFailures(batchChange, queued)
      _ <- storeQueuingFailures(changeToStore)
    } yield BatchConversionOutput(changeToStore, recordSetChanges)
  }

  def allChangesWereConverted(
      singleChanges: List[SingleChange],
      recordSetChanges: List[RecordSetChange]
  ): BatchResult[Unit] = {
    val convertedIds = recordSetChanges.flatMap(_.singleBatchChangeIds).toSet
    singleChanges.find(ch => !convertedIds.contains(ch.id)) match {
      // Each single change has a corresponding recordset id
      // If they're not equal, then there's a delete request for a record that doesn't exist. So we allow this to process
      case Some(_) if singleChanges.map(_.id).length != recordSetChanges.map(_.id).length && !singleChanges.map(_.typ).contains(UNKNOWN) =>
        logger.info(s"Successfully converted SingleChanges [${singleChanges
          .map(_.id)}] to RecordSetChanges [${recordSetChanges.map(_.id)}]")
        ().toRightBatchResult
      case Some(change) => BatchConversionError(change).toLeftBatchResult
      case None =>
          logger.info(s"Successfully converted SingleChanges [${singleChanges
            .map(_.id)}] to RecordSetChanges [${recordSetChanges.map(_.id)}]")
          ().toRightBatchResult
        }
    }

  def putChangesOnQueue(
      recordSetChanges: List[RecordSetChange],
      batchChangeId: String
  ): BatchResult[List[RecordSetChange]] =
    recordSetChanges.toNel match {
      case None =>
        recordSetChanges.toRightBatchResult // If list is empty, return normally without queueing
      case Some(rsc) =>
        for {
          rscResult <- messageQueue
            .sendBatch(rsc) // Queue changes
            .map(_.successes)
            .toBatchResult
          _ <- messageQueue
            .send(BatchChangeCommand(batchChangeId)) // Queue notification
            .toBatchResult
        } yield rscResult
    }

  def updateWithQueueingFailures(
      batchChange: BatchChange,
      recordSetChanges: List[RecordSetChange]
  ): BatchChange = {
    // idsMap maps batchId to recordSetId
    val idsMap = recordSetChanges.flatMap { rsChange =>
      rsChange.singleBatchChangeIds.map(batchId => (batchId, rsChange.id))
    }.toMap
    val withStatus = batchChange.changes.map { change =>
      idsMap
        .get(change.id)
        .map { _ =>
          // a recordsetchange was successfully queued for this change
          change
        }
        .getOrElse {
          // Match and check if it's a delete change for a record that doesn't exists.
          change match {
            case _: SingleDeleteRRSetChange if change.recordSetId.isEmpty =>
              // Mark as Complete since we don't want to throw it as an error
              change.withDoesNotExistMessage
            case _ =>
              // Failure here means there was a message queue issue for this change
              change.withFailureMessage(failedMessage)
          }
        }
    }
    batchChange.copy(changes = withStatus)
  }

  def storeQueuingFailures(batchChange: BatchChange): BatchResult[Unit] = {
    // Update if Single change is Failed or if a record that does not exist is deleted
    val failedAndNotExistsChanges = batchChange.changes.collect {
      case change if change.status == SingleChangeStatus.Failed || change.systemMessage.contains(nonExistentRecordDeleteMessage) || change.systemMessage.contains(nonExistentRecordDataDeleteMessage) => change
    }
    batchChangeRepo.updateSingleChanges(failedAndNotExistsChanges).as(())
  }.toBatchResult

  def createRecordSetChangesForBatch(
      changes: List[SingleChange],
      existingZones: ExistingZones,
      groupedChanges: ChangeForValidationMap,
      userId: String,
      ownerGroupId: Option[String]
  ): List[RecordSetChange] = {
    // NOTE: this also assumes we are past approval and know the zone/record split at this point
    val supportedChangesByKey = changes
      .filter(sc => SupportedBatchChangeRecordTypes.get.contains(sc.typ))
      .groupBy(_.recordKey)
      .map {
        case (recordKey, singleChangeList) => (recordKey, singleChangeList.toNel)
      }

    supportedChangesByKey
      .collect {
        case (Some(recordKey), Some(singleChangeNel)) =>
          val existingRecordSet = groupedChanges.getExistingRecordSet(recordKey)
          val proposedRecordData = groupedChanges.getProposedRecordData(recordKey)

          for {
            zoneName <- singleChangeNel.head.zoneName
            zone <- existingZones.getByName(zoneName)
            logicalChangeType <- groupedChanges.getLogicalChangeType(recordKey)
            recordSetChange <- generateRecordSetChange(
              logicalChangeType,
              singleChangeNel,
              zone,
              recordKey.recordType,
              proposedRecordData,
              userId,
              existingRecordSet,
              ownerGroupId
            )
          } yield recordSetChange
      }
      .toList
      .flatten
  }

  def generateRecordSetChange(
      logicalChangeType: LogicalChangeType.LogicalChangeType,
      singleChangeNel: NonEmptyList[SingleChange],
      zone: Zone,
      recordType: RecordType,
      proposedRecordData: Set[RecordData],
      userId: String,
      existingRecordSet: Option[RecordSet],
      ownerGroupId: Option[String]
  ): Option[RecordSetChange] = {

    val singleChangeIds = singleChangeNel.map(_.id).toList

    // Determine owner group for add/update
    lazy val setOwnerGroupId = existingRecordSet match {
      // Update
      case Some(existingRs) =>
        if (zone.shared && existingRs.ownerGroupId.isEmpty) {
          ownerGroupId
        } else {
          existingRs.ownerGroupId
        }
      // Add
      case None =>
        if (zone.shared) {
          ownerGroupId
        } else {
          None
        }
    }

    // New record set for add/update or single delete
    lazy val newRecordSet = {
      val firstAddChange = singleChangeNel.collect {
        case sac: SingleAddChange => sac
      }.headOption

      // For adds, grab the first ttl; for updates via single DeleteRecord, use existing TTL
      val newTtlRecordNameTuple = firstAddChange
        .map(add => (Some(add.ttl), add.recordName))
        .orElse(existingRecordSet.map(rs => (Some(rs.ttl), Some(rs.name))))

      newTtlRecordNameTuple.collect {
        case (Some(ttl), Some(recordName)) =>
          RecordSet(
            zone.id,
            recordName,
            recordType,
            ttl,
            RecordSetStatus.Pending,
            Instant.now.truncatedTo(ChronoUnit.MILLIS),
            None,
            proposedRecordData.toList,
            ownerGroupId = setOwnerGroupId
          )
      }
    }

    // Generate RecordSetChange based on logical type
    logicalChangeType match {
      case Add =>
        newRecordSet.map { newRs =>
          RecordSetChangeGenerator.forAdd(newRs, zone, userId, singleChangeIds)
        }
      case FullDelete =>
        existingRecordSet.map { existingRs =>
          RecordSetChangeGenerator.forDelete(existingRs, zone, userId, singleChangeIds)
        }
      case Update =>
        for {
          existingRs <- existingRecordSet
          newRs <- newRecordSet
        } yield RecordSetChangeGenerator.forUpdate(existingRs, newRs, zone, userId, singleChangeIds)
      case _ => None // This case should never happen
    }
  }
}
