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
import cats.implicits._
import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.CatsHelpers
import vinyldns.api.domain.batch.BatchTransformations.{ExistingRecordSets, ExistingZones}
import vinyldns.api.engine.TestMessageQueue
import vinyldns.api.repository._
import vinyldns.core.TestMembershipData.okUser
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData.{okZone, _}
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.RecordSetChangeType.RecordSetChangeType
import vinyldns.core.domain.record.RecordType.{RecordType, _}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.Zone

class BatchChangeConverterSpec extends WordSpec with Matchers with CatsHelpers {

  private def makeSingleAddChange(
      name: String,
      recordData: RecordData,
      typ: RecordType = A,
      zone: Zone = okZone) = {
    val fqdn = s"$name.${zone.name}"
    SingleAddChange(
      Some(zone.id),
      Some(zone.name),
      Some(name),
      fqdn,
      typ,
      123,
      recordData,
      SingleChangeStatus.Pending,
      None,
      None,
      None)
  }

  private def makeSingleDeleteChange(name: String, typ: RecordType, zone: Zone = okZone) = {
    val fqdn = s"$name.${zone.name}"
    SingleDeleteChange(
      Some(zone.id),
      Some(zone.name),
      Some(name),
      fqdn,
      typ,
      SingleChangeStatus.Pending,
      None,
      None,
      None)
  }

  private val addSingleChangesGood = List(
    makeSingleAddChange("one", AData("1.1.1.1")),
    makeSingleAddChange("two", AData("1.1.1.2")),
    makeSingleAddChange("repeat", AData("1.1.1.3")),
    makeSingleAddChange("repeat", AData("1.1.1.4")),
    makeSingleAddChange("aaaaRecord", AAAAData("1::1"), AAAA),
    makeSingleAddChange("cnameRecord", CNAMEData("cname.com."), CNAME),
    makeSingleAddChange("10.1.1.1", PTRData("ptrData"), PTR),
    makeSingleAddChange("txtRecord", TXTData("text"), TXT),
    makeSingleAddChange("mxRecord", MXData(1, "foo.bar."), MX)
  )

  private val deleteSingleChangesGood = List(
    makeSingleDeleteChange("aToDelete", A),
    makeSingleDeleteChange("cnameToDelete", CNAME),
    makeSingleDeleteChange("cnameToDelete", CNAME), // duplicate should basically be ignored
    makeSingleDeleteChange("txtToDelete", TXT),
    makeSingleDeleteChange("mxToDelete", MX)
  )

  private val updateSingleChangesGood = List(
    makeSingleDeleteChange("aToUpdate", A),
    makeSingleAddChange("aToUpdate", AData("1.1.1.1")),
    makeSingleDeleteChange("cnameToUpdate", CNAME),
    makeSingleAddChange("cnameToUpdate", CNAMEData("newcname.com."), CNAME),
    makeSingleDeleteChange("txtToUpdate", TXT),
    makeSingleAddChange("txtToUpdate", TXTData("update"), TXT),
    makeSingleDeleteChange("mxToUpdate", MX),
    makeSingleAddChange("mxToUpdate", MXData(1, "update.com."), MX)
  )

  private val singleChangesOneBad = List(
    makeSingleAddChange("one", AData("1.1.1.1")),
    makeSingleAddChange("two", AData("1.1.1.2")),
    makeSingleAddChange("bad", AData("1.1.1.1"))
  )

  private val singleChangesOneUnsupported = List(
    makeSingleAddChange("one", AData("1.1.1.1")),
    makeSingleAddChange("two", AData("1.1.1.2")),
    makeSingleAddChange("wrongType", TXTData("Unsupported!"), UNKNOWN)
  )

  private def existingZones = ExistingZones(Set(okZone, sharedZone))

  private val aToDelete = RecordSet(
    okZone.id,
    "aToDelete",
    A,
    123,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("1.1.1.1")))
  private val cnameToDelete = RecordSet(
    okZone.id,
    "cnameToDelete",
    CNAME,
    123,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(CNAMEData("old.com.")))
  private val aToUpdate = RecordSet(
    okZone.id,
    "aToUpdate",
    A,
    123,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(AData("1.1.1.1")))
  private val cnameToUpdate = RecordSet(
    okZone.id,
    "cnameToUpdate",
    CNAME,
    123,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(CNAMEData("old.com.")))
  private val txtToUpdate = RecordSet(
    okZone.id,
    "txtToUpdate",
    TXT,
    123,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(TXTData("old")))
  private val txtToDelete = RecordSet(
    okZone.id,
    "txtToDelete",
    TXT,
    123,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(TXTData("test")))
  private val mxToUpdate = RecordSet(
    okZone.id,
    "mxToUpdate",
    MX,
    123,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(MXData(1, "old.com.")))
  private val mxToDelete = RecordSet(
    okZone.id,
    "mxToDelete",
    MX,
    123,
    RecordSetStatus.Active,
    DateTime.now,
    None,
    List(MXData(1, "delete.com.")))
  private def existingRecordSets =
    ExistingRecordSets(
      List(
        aToDelete,
        cnameToDelete,
        aToUpdate,
        cnameToUpdate,
        txtToUpdate,
        txtToDelete,
        mxToUpdate,
        mxToDelete))

  private val batchChangeRepo = new InMemoryBatchChangeRepository
  private val underTest = new BatchChangeConverter(batchChangeRepo, TestMessageQueue)

  "convertAndSendBatchForProcessing" should {
    "successfully generate add RecordSetChange and map IDs for all adds" in {
      val batchChange =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          DateTime.now,
          addSingleChangesGood,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      val result = rightResultOf(
        underTest
          .sendBatchForProcessing(batchChange, existingZones, existingRecordSets, None)
          .value)
      val rsChanges = result.recordSetChanges

      // validate recordset changes generated from batch
      rsChanges.length shouldBe addSingleChangesGood.map(_.recordName).distinct.length
      List("one", "two", "repeat", "aaaaRecord", "cnameRecord", "10.1.1.1", "txtRecord", "mxRecord")
        .foreach { rName =>
          validateRecordSetChange(rName, rsChanges, batchChange, RecordSetChangeType.Create)
          validateRecordDataCombination(rName, rsChanges, batchChange)
        }

      // validate statuses unchanged as returned
      result.batchChange shouldBe batchChange
    }

    "successfully generate delete RecordSetChange and map IDs for all deletes" in {
      val batchChange =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          DateTime.now,
          deleteSingleChangesGood,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      val result = rightResultOf(
        underTest
          .sendBatchForProcessing(batchChange, existingZones, existingRecordSets, None)
          .value)
      val rsChanges = result.recordSetChanges

      // validate recordset change basics generated from batch
      rsChanges.length shouldBe 4
      validateRecordSetChange(aToDelete.name, rsChanges, batchChange, RecordSetChangeType.Delete)
      validateRecordSetChange(
        cnameToDelete.name,
        rsChanges,
        batchChange,
        RecordSetChangeType.Delete)
      validateRecordSetChange(txtToDelete.name, rsChanges, batchChange, RecordSetChangeType.Delete)
      validateRecordSetChange(mxToDelete.name, rsChanges, batchChange, RecordSetChangeType.Delete)

      // confirm the IDs in the generated change
      rsChanges.map(_.recordSet.id) should contain theSameElementsAs List(
        aToDelete.id,
        cnameToDelete.id,
        txtToDelete.id,
        mxToDelete.id)

      // validate statuses unchanged as returned
      result.batchChange shouldBe batchChange
    }

    "successfully generate update RecordSetChange and map IDs for mix of adds/deletes" in {
      val batchChange =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          DateTime.now,
          updateSingleChangesGood,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      val result = rightResultOf(
        underTest
          .sendBatchForProcessing(batchChange, existingZones, existingRecordSets, None)
          .value)
      val rsChanges = result.recordSetChanges

      // validate recordset changes generated from batch
      rsChanges.length shouldBe 4
      List(aToUpdate.name, cnameToUpdate.name, txtToUpdate.name, mxToUpdate.name).foreach { rName =>
        validateRecordSetChange(rName, rsChanges, batchChange, RecordSetChangeType.Update)
        validateRecordDataCombination(rName, rsChanges, batchChange)
      }

      // confirm the IDs in the generated change
      rsChanges.map(_.recordSet.id) should contain theSameElementsAs List(
        aToUpdate.id,
        cnameToUpdate.id,
        txtToUpdate.id,
        mxToUpdate.id)

      // validate statuses unchanged as returned
      result.batchChange shouldBe batchChange
    }

    "successfully handle a combination of adds, updates, and deletes" in {
      val changes = addSingleChangesGood ++ deleteSingleChangesGood ++ updateSingleChangesGood
      val batchChange =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          DateTime.now,
          changes,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      val result = rightResultOf(
        underTest
          .sendBatchForProcessing(batchChange, existingZones, existingRecordSets, None)
          .value)
      val rsChanges = result.recordSetChanges

      // validate recordset changes generated from batch
      rsChanges.length shouldBe 16
      // adds
      List("one", "two", "repeat", "aaaaRecord", "cnameRecord", "10.1.1.1", "txtRecord", "mxRecord")
        .foreach { rName =>
          validateRecordSetChange(rName, rsChanges, batchChange, RecordSetChangeType.Create)
          validateRecordDataCombination(rName, rsChanges, batchChange)
        }
      // deletes
      List(aToDelete.name, cnameToDelete.name, txtToDelete.name, mxToDelete.name).foreach { rName =>
        validateRecordSetChange(rName, rsChanges, batchChange, RecordSetChangeType.Delete)
      }

      // updates
      List(aToUpdate.name, cnameToUpdate.name, txtToUpdate.name, mxToUpdate.name).foreach { rName =>
        validateRecordSetChange(rName, rsChanges, batchChange, RecordSetChangeType.Update)
        validateRecordDataCombination(rName, rsChanges, batchChange)
      }

      // validate statuses unchanged as returned
      result.batchChange shouldBe batchChange

      // check the batch has been stored in the DB
      val savedBatch: Option[BatchChange] =
        await(batchChangeRepo.getBatchChange(batchChange.id))

      savedBatch shouldBe Some(batchChange)
    }

    "successfully return for an empty batch" in {
      val batchChange =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          DateTime.now,
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      val result = rightResultOf(
        underTest
          .sendBatchForProcessing(batchChange, existingZones, existingRecordSets, None)
          .value)

      result.batchChange shouldBe batchChange
    }

    "set status to failure for changes with queueing issues" in {
      val batchWithBadChange =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          DateTime.now,
          singleChangesOneBad,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      val result = rightResultOf(
        underTest
          .sendBatchForProcessing(batchWithBadChange, existingZones, existingRecordSets, None)
          .value)
      val rsChanges = result.recordSetChanges

      rsChanges.length shouldBe 3
      List("one", "two").foreach { rName =>
        validateRecordSetChange(rName, rsChanges, batchWithBadChange, RecordSetChangeType.Create)
        validateRecordDataCombination(rName, rsChanges, batchWithBadChange)
      }

      val returnedBatch = result.batchChange

      // validate failed status update returned
      val failedChange = returnedBatch.changes(2)
      failedChange.status shouldBe SingleChangeStatus.Failed
      failedChange.recordChangeId shouldBe None
      failedChange.systemMessage shouldBe Some("Error queueing RecordSetChange for processing")

      // validate other changes have no update
      returnedBatch.changes(0) shouldBe singleChangesOneBad(0)
      returnedBatch.changes(1) shouldBe singleChangesOneBad(1)

      // check the update has been made in the DB
      val savedBatch: Option[BatchChange] =
        await(batchChangeRepo.getBatchChange(batchWithBadChange.id))
      savedBatch shouldBe Some(returnedBatch)
    }

    "return error if an unsupported record is received" in {
      val batchChangeUnsupported =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          DateTime.now,
          singleChangesOneUnsupported,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved)
      val result = leftResultOf(
        underTest
          .sendBatchForProcessing(batchChangeUnsupported, existingZones, existingRecordSets, None)
          .value)
      result shouldBe an[BatchConversionError]

      val notSaved: Option[BatchChange] =
        await(batchChangeRepo.getBatchChange(batchChangeUnsupported.id))
      notSaved shouldBe None
    }
  }

  "generateAddChange" should {
    val singleAddChange = makeSingleAddChange("shared-rs", AData("1.2.3.4"), A, sharedZone)
    val ownerGroupId = Some("some-owner-group-id")

    "generate record set changes for shared zone without owner group ID if not provided" in {
      val result =
        underTest.generateAddChange(
          NonEmptyList.of(singleAddChange),
          existingZones,
          okUser.id,
          None)
      result shouldBe defined
      result.foreach(_.recordSet.ownerGroupId shouldBe None)
    }

    "generate record set changes for shared zone with owner group ID if provided" in {
      val result =
        underTest.generateAddChange(
          NonEmptyList.of(singleAddChange),
          existingZones,
          okUser.id,
          ownerGroupId)
      result shouldBe defined
      result.foreach(_.recordSet.ownerGroupId shouldBe ownerGroupId)
    }

    "generate record set changes for non-shared zone without owner group ID" in {
      val result =
        underTest.generateAddChange(
          NonEmptyList.fromListUnsafe(addSingleChangesGood),
          existingZones,
          okUser.id,
          ownerGroupId)
      result shouldBe defined
      result.foreach(_.recordSet.ownerGroupId shouldBe None)
    }
  }

  "generateUpdateChange" should {
    val addChange = makeSingleAddChange("aaaa", AAAAData("2:3:4:5:6:7:8:9"), AAAA, sharedZone)
    val deleteChange = makeSingleDeleteChange("aaaa", AAAA, sharedZone)

    "not overwrite existing owner group ID for existing record set in shared zone" in {
      val result =
        underTest.generateUpdateChange(
          NonEmptyList.of(deleteChange),
          NonEmptyList.of(addChange),
          existingZones,
          ExistingRecordSets(List(sharedZoneRecord)),
          okUser.id,
          Some("new-owner-group-id")
        )
      result shouldBe defined
      result.foreach(_.recordSet.ownerGroupId shouldBe sharedZoneRecord.ownerGroupId)
    }

    "use specified owner group ID if undefined for existing record set in shared zone" in {
      val ownerGroupId = Some("new-owner-group-id")
      val result =
        underTest.generateUpdateChange(
          NonEmptyList.of(deleteChange),
          NonEmptyList.of(addChange),
          existingZones,
          ExistingRecordSets(List(sharedZoneRecord.copy(ownerGroupId = None))),
          okUser.id,
          ownerGroupId
        )
      result shouldBe defined
      result.foreach(_.recordSet.ownerGroupId shouldBe ownerGroupId)
    }

    "generate record set without updating owner group ID for record set in unshared zone" in {
      val result =
        underTest.generateUpdateChange(
          NonEmptyList.of(
            deleteChange.copy(zoneId = Some(okZone.id), zoneName = Some(okZone.name))),
          NonEmptyList.of(addChange.copy(zoneId = Some(okZone.id), zoneName = Some(okZone.name))),
          existingZones,
          ExistingRecordSets(List(sharedZoneRecord.copy(ownerGroupId = None, zoneId = okZone.id))),
          okUser.id,
          Some("new-owner-group-id")
        )
      result shouldBe defined
      result.foreach(_.recordSet.ownerGroupId shouldBe None)
    }
  }

  private def validateRecordSetChange(
      name: String,
      recordSetChanges: List[RecordSetChange],
      batchChange: BatchChange,
      typ: RecordSetChangeType) = {
    val singleChangesOut = batchChange.changes.filter { change =>
      change.recordName match {
        case Some(rn) if rn == name => true
        case _ => false
      }
    }
    singleChangesOut.length should be > 0

    val recordChangeOut = recordSetChanges
      .find(_.recordSet.name == name)
      .getOrElse(fail(s"Missing result change for batchChange $name"))

    recordChangeOut.singleBatchChangeIds should contain theSameElementsAs singleChangesOut.map(_.id)

    recordChangeOut.zone shouldBe okZone
    recordChangeOut.userId shouldBe okUser.id
    recordChangeOut.changeType shouldBe typ
    recordChangeOut.status shouldBe RecordSetChangeStatus.Pending
    recordChangeOut.recordSet.name shouldBe name

    val recordSet = recordChangeOut.recordSet
    recordSet.name shouldBe name
    recordSet.typ shouldBe singleChangesOut.head.typ
  }

  private def validateRecordDataCombination(
      name: String,
      recordSetChanges: List[RecordSetChange],
      batchChange: BatchChange) = {
    val singleChangesOut = batchChange.changes.filter { change =>
      change.recordName match {
        case Some(rn) if rn == name => true
        case _ => false
      }
    }
    val expectedRecords = singleChangesOut.collect {
      case add: SingleAddChange => add.recordData
    }

    val recordChangeOut = recordSetChanges
      .find(_.recordSet.name == name)
      .getOrElse(fail(s"Missing result change for batchChange $name"))

    recordChangeOut.recordSet.records should contain theSameElementsAs expectedRecords
  }
}
