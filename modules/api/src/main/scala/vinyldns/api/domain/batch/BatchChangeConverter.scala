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
import cats.syntax.functor._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import vinyldns.api.domain.batch.BatchChangeInterfaces._
import vinyldns.api.domain.batch.BatchTransformations.{
  BatchConversionOutput,
  ExistingRecordSets,
  ExistingZones
}
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.core.domain.record
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.Zone
import vinyldns.core.domain.batch._
import vinyldns.core.queue.MessageQueue

class BatchChangeConverter(batchChangeRepo: BatchChangeRepository, messageQueue: MessageQueue)
    extends BatchChangeConverterAlgebra {

  private val logger = LoggerFactory.getLogger("BatchChangeConverter")

  def sendBatchForProcessing(
      batchChange: BatchChange,
      existingZones: ExistingZones,
      existingRecordSets: ExistingRecordSets,
      ownerGroupId: Option[String]): BatchResult[BatchConversionOutput] = {
    logger.info(
      s"Converting BatchChange [${batchChange.id}] with SingleChanges [${batchChange.changes.map(_.id)}]")
    for {
      recordSetChanges <- createRecordSetChangesForBatch(
        batchChange.changes,
        existingZones,
        existingRecordSets,
        batchChange.userId,
        ownerGroupId).toRightBatchResult
      _ <- allChangesWereConverted(batchChange.changes, recordSetChanges)
      _ <- batchChangeRepo
        .save(batchChange)
        .toBatchResult // need to save the change before queueing, backend processing expects the changes to exist
      queued <- putChangesOnQueue(recordSetChanges)
      changeToStore = updateWithQueueingFailures(batchChange, queued)
      _ <- storeQueuingFailures(changeToStore)
    } yield BatchConversionOutput(changeToStore, recordSetChanges)
  }

  def allChangesWereConverted(
      singleChanges: List[SingleChange],
      recordSetChanges: List[RecordSetChange]): BatchResult[Unit] = {
    val convertedIds = recordSetChanges.flatMap(_.singleBatchChangeIds).toSet

    singleChanges.find(ch => !convertedIds.contains(ch.id)) match {
      case Some(change) => BatchConversionError(change).toLeftBatchResult
      case None =>
        logger.info(s"Successfully converted SingleChanges [${singleChanges
          .map(_.id)}] to RecordSetChanges [${recordSetChanges.map(_.id)}]")
        ().toRightBatchResult
    }
  }

  def putChangesOnQueue(
      recordSetChanges: List[RecordSetChange]): BatchResult[List[RecordSetChange]] =
    recordSetChanges.toNel match {
      case None =>
        recordSetChanges.toRightBatchResult // If list is empty, return normally without queueing
      case Some(rsc) =>
        messageQueue
          .sendBatch(rsc)
          .map(_.successes)
          .toBatchResult
    }

  def updateWithQueueingFailures(
      batchChange: BatchChange,
      recordSetChanges: List[RecordSetChange]): BatchChange = {
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
          // failure here means there was a message queue issue for this change
          change.withFailureMessage("Error queueing RecordSetChange for processing")
        }
    }

    batchChange.copy(changes = withStatus)
  }

  def storeQueuingFailures(batchChange: BatchChange): BatchResult[Unit] = {
    val failedChanges = batchChange.changes.collect {
      case change if change.status == SingleChangeStatus.Failed => change
    }
    batchChangeRepo.updateSingleChanges(failedChanges).as(())
  }.toBatchResult

  def createRecordSetChangesForBatch(
      changes: List[SingleChange],
      existingZones: ExistingZones,
      existingRecordSets: ExistingRecordSets,
      userId: String,
      ownerGroupId: Option[String]): List[RecordSetChange] = {
    // TODO: note, this also assumes we are past approval and know the zone/record split at this point
    val grouped = changes.groupBy(_.recordKey)

    grouped.toList.flatMap {
      case (_, groupedChanges) =>
        /* TODO note: the flatmap means if we couldnt get a zone here, we wouldn't be warned
        that is fine as we will only be passed this change from the service if the zone exists in that map.
        If we move to getting zones from the DB in the converter, we should report status back on changes
        where we can no longer find the zone (edge case - means someone submitted batch and then zone was deleted)
         */
        combineChanges(groupedChanges, existingZones, existingRecordSets, userId, ownerGroupId)
    }
  }

  def combineChanges(
      changes: List[SingleChange],
      existingZones: ExistingZones,
      existingRecordSets: ExistingRecordSets,
      userId: String,
      ownerGroupId: Option[String]): Option[RecordSetChange] = {
    val adds = NonEmptyList.fromList {
      changes.collect {
        case add: SingleAddChange if SupportedBatchChangeRecordTypes.get.contains(add.typ) => add
      }
    }
    val deletes = NonEmptyList.fromList {
      changes.collect {
        case del: SingleDeleteChange if SupportedBatchChangeRecordTypes.get.contains(del.typ) => del
      }
    }

    // Note: deletes are applied before adds by this logic
    (deletes, adds) match {
      case (None, Some(a)) => generateAddChange(a, existingZones, userId, ownerGroupId)
      case (Some(d), None) => generateDeleteChange(d, existingZones, existingRecordSets, userId)
      case (Some(d), Some(a)) =>
        generateUpdateChange(d, a, existingZones, existingRecordSets, userId, ownerGroupId)
      case _ => None
    }
  }

  def generateUpdateChange(
      deleteChanges: NonEmptyList[SingleDeleteChange],
      addChanges: NonEmptyList[SingleAddChange],
      existingZones: ExistingZones,
      existingRecordSets: ExistingRecordSets,
      userId: String,
      ownerGroupId: Option[String]): Option[RecordSetChange] =
    for {
      deleteChange <- Some(deleteChanges.head)
      zoneName <- deleteChange.zoneName
      zoneId <- deleteChange.zoneId
      recordName <- deleteChange.recordName
      zone <- existingZones.getByName(zoneName)
      existingRecordSet <- existingRecordSets.get(zoneId, recordName, deleteChange.typ)
      newRecordSet <- {
        val setOwnerGroupId = if (zone.shared && existingRecordSet.ownerGroupId.isEmpty) {
          ownerGroupId
        } else {
          existingRecordSet.ownerGroupId
        }
        combineAddChanges(addChanges, zone, setOwnerGroupId)
      }
      changeIds = deleteChanges.map(_.id) ++ addChanges.map(_.id).toList
    } yield
      RecordSetChangeGenerator.forUpdate(
        existingRecordSet,
        newRecordSet,
        zone,
        userId,
        changeIds.toList)

  def generateDeleteChange(
      deleteChanges: NonEmptyList[SingleDeleteChange],
      existingZones: ExistingZones,
      existingRecordSets: ExistingRecordSets,
      userId: String): Option[RecordSetChange] =
    for {
      deleteChange <- Some(deleteChanges.head)
      zoneName <- deleteChange.zoneName
      zoneId <- deleteChange.zoneId
      recordName <- deleteChange.recordName
      zone <- existingZones.getByName(zoneName)
      existingRecordSet <- existingRecordSets.get(zoneId, recordName, deleteChange.typ)
    } yield
      RecordSetChangeGenerator.forDelete(
        existingRecordSet,
        zone,
        userId,
        deleteChanges.map(_.id).toList)

  def generateAddChange(
      addChanges: NonEmptyList[SingleAddChange],
      existingZones: ExistingZones,
      userId: String,
      ownerGroupId: Option[String]): Option[RecordSetChange] =
    for {
      zoneName <- addChanges.head.zoneName
      zone <- existingZones.getByName(zoneName)
      newRecordSet <- {
        val setOwnerGroupId = if (zone.shared) ownerGroupId else None
        combineAddChanges(addChanges, zone, setOwnerGroupId)
      }
      ids = addChanges.map(_.id)
    } yield RecordSetChangeGenerator.forAdd(newRecordSet, zone, userId, ids.toList)

  // Combines changes where the RecordData can just be appended to list (A, AAAA, CNAME, PTR)
  // NOTE: CNAME & PTR will only have one data field due to validations, so the combination is fine
  def combineAddChanges(
      changes: NonEmptyList[SingleAddChange],
      zone: Zone,
      ownerGroupId: Option[String]): Option[RecordSet] = {
    val combinedData =
      changes.foldLeft(List[RecordData]())((acc, ch) => ch.recordData :: acc).distinct
    // recordName and typ are shared by all changes passed into this function, can pull those from any change
    // TTL choice is arbitrary here; this is taking the 1st
    changes.head.recordName.map { recordName =>
      record.RecordSet(
        zone.id,
        recordName,
        changes.head.typ,
        changes.head.ttl,
        RecordSetStatus.Pending,
        DateTime.now,
        None,
        combinedData,
        ownerGroupId = ownerGroupId)
    }
  }
}
