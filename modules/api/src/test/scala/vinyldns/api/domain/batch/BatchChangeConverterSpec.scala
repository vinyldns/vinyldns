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
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.domain.batch.BatchTransformations._
import vinyldns.api.domain.batch.BatchTransformations.LogicalChangeType._
import vinyldns.api.engine.TestMessageQueue
import vinyldns.api.repository._
import vinyldns.core.TestMembershipData.okUser
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData.{okZone, _}
import vinyldns.core.domain.Fqdn
import vinyldns.core.domain.batch._
import vinyldns.core.domain.record.RecordSetChangeType.RecordSetChangeType
import vinyldns.core.domain.record.RecordType.{RecordType, _}
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone.Zone

class BatchChangeConverterSpec extends AnyWordSpec with Matchers {
  private val nonExistentRecordDeleteMessage: String = "This record does not exist. " +
    "No further action is required."

  private def makeSingleAddChange(
                                   name: String,
                                   recordData: RecordData,
                                   typ: RecordType = A,
                                   zone: Zone = okZone
                                 ) = {
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
      None
    )
  }

  private def makeSingleDeleteRRSetChange(name: String, typ: RecordType, zone: Zone = okZone, systemMessage: Option[String] = None) = {
    val fqdn = s"$name.${zone.name}"
    SingleDeleteRRSetChange(
      Some(zone.id),
      Some(zone.name),
      Some(name),
      fqdn,
      typ,
      None,
      SingleChangeStatus.Pending,
      systemMessage,
      None,
      None
    )
  }

  private def makeAddChangeForValidation(
                                          recordName: String,
                                          recordData: RecordData,
                                          typ: RecordType = RecordType.A
                                        ): AddChangeForValidation =
    AddChangeForValidation(
      okZone,
      s"$recordName",
      AddChangeInput(s"$recordName.ok.", typ, None, Some(123), recordData),
      7200L
    )

  private def makeDeleteRRSetChangeForValidation(
                                                  recordName: String,
                                                  typ: RecordType = RecordType.A,
                                                  systemMessage: Option[String] = None
                                                ): DeleteRRSetChangeForValidation =
    DeleteRRSetChangeForValidation(
      okZone,
      s"$recordName",
      DeleteRRSetChangeInput(s"$recordName.ok.", typ, systemMessage, None)
    )

  private val addSingleChangesGood = List(
    makeSingleAddChange("one", AData("1.1.1.1")),
    makeSingleAddChange("two", AData("1.1.1.2")),
    makeSingleAddChange("repeat", AData("1.1.1.3")),
    makeSingleAddChange("repeat", AData("1.1.1.4")),
    makeSingleAddChange("aaaaRecord", AAAAData("1::1"), AAAA),
    makeSingleAddChange("cnameRecord", CNAMEData(Fqdn("cname.com.")), CNAME),
    makeSingleAddChange("10.1.1.1", PTRData(Fqdn("ptrData")), PTR),
    makeSingleAddChange("txtRecord", TXTData("text"), TXT),
    makeSingleAddChange("mxRecord", MXData(1, Fqdn("foo.bar.")), MX)
  )

  private val addChangeForValidationGood = List(
    makeAddChangeForValidation("one", AData("1.1.1.1")),
    makeAddChangeForValidation("two", AData("1.1.1.2")),
    makeAddChangeForValidation("repeat", AData("1.1.1.3")),
    makeAddChangeForValidation("repeat", AData("1.1.1.4")),
    makeAddChangeForValidation("aaaaRecord", AAAAData("1::1"), AAAA),
    makeAddChangeForValidation("cnameRecord", CNAMEData(Fqdn("cname.com.")), CNAME),
    makeAddChangeForValidation("10.1.1.1", PTRData(Fqdn("ptrData")), PTR),
    makeAddChangeForValidation("txtRecord", TXTData("text"), TXT),
    makeAddChangeForValidation("mxRecord", MXData(1, Fqdn("foo.bar.")), MX)
  )

  private val deleteSingleChangesGood = List(
    makeSingleDeleteRRSetChange("aToDelete", A),
    makeSingleDeleteRRSetChange("cnameToDelete", CNAME),
    makeSingleDeleteRRSetChange("cnameToDelete", CNAME), // duplicate should basically be ignored
    makeSingleDeleteRRSetChange("txtToDelete", TXT),
    makeSingleDeleteRRSetChange("mxToDelete", MX)
  )

  private val deleteRRSetChangeForValidationGood = List(
    makeDeleteRRSetChangeForValidation("aToDelete"),
    makeDeleteRRSetChangeForValidation("cnameToDelete", CNAME),
    makeDeleteRRSetChangeForValidation("cnameToDelete", CNAME), // duplicate should basically be ignored
    makeDeleteRRSetChangeForValidation("txtToDelete", TXT),
    makeDeleteRRSetChangeForValidation("mxToDelete", MX)
  )

  private val updateSingleChangesGood = List(
    makeSingleDeleteRRSetChange("aToUpdate", A),
    makeSingleAddChange("aToUpdate", AData("1.1.1.1")),
    makeSingleDeleteRRSetChange("cnameToUpdate", CNAME),
    makeSingleAddChange("cnameToUpdate", CNAMEData(Fqdn("newcname.com.")), CNAME),
    makeSingleDeleteRRSetChange("txtToUpdate", TXT),
    makeSingleAddChange("txtToUpdate", TXTData("update"), TXT),
    makeSingleDeleteRRSetChange("mxToUpdate", MX),
    makeSingleAddChange("mxToUpdate", MXData(1, Fqdn("update.com.")), MX)
  )

  private val updateChangeForValidationGood = List(
    makeDeleteRRSetChangeForValidation("aToUpdate", A),
    makeAddChangeForValidation("aToUpdate", AData("1.1.1.1")),
    makeDeleteRRSetChangeForValidation("cnameToUpdate", CNAME),
    makeAddChangeForValidation("cnameToUpdate", CNAMEData(Fqdn("newcname.com.")), CNAME),
    makeDeleteRRSetChangeForValidation("txtToUpdate", TXT),
    makeAddChangeForValidation("txtToUpdate", TXTData("update"), TXT),
    makeDeleteRRSetChangeForValidation("mxToUpdate", MX),
    makeAddChangeForValidation("mxToUpdate", MXData(1, Fqdn("update.com.")), MX)
  )

  private val singleChangesOneDelete = List(
    makeSingleDeleteRRSetChange("DoesNotExistToDelete", A, okZone, Some(nonExistentRecordDeleteMessage))
  )

  private val changeForValidationOneDelete = List(
    makeDeleteRRSetChangeForValidation("DoesNotExistToDelete", A, Some(nonExistentRecordDeleteMessage))
  )

  private val singleChangesOneBad = List(
    makeSingleAddChange("one", AData("1.1.1.1")),
    makeSingleAddChange("two", AData("1.1.1.2")),
    makeSingleAddChange("bad", AData("1.1.1.1"))
  )

  private val changeForValidationOneBad = List(
    makeAddChangeForValidation("one", AData("1.1.1.1")),
    makeAddChangeForValidation("two", AData("1.1.1.2")),
    makeAddChangeForValidation("bad", AData("1.1.1.1"))
  )

  private val singleChangesOneUnsupported = List(
    makeSingleAddChange("one", AData("1.1.1.1")),
    makeSingleAddChange("two", AData("1.1.1.2")),
    makeSingleAddChange("wrongType", TXTData("Unsupported!"), UNKNOWN)
  )

  private val changeForValidationOneUnsupported = List(
    makeAddChangeForValidation("one", AData("1.1.1.1")),
    makeAddChangeForValidation("two", AData("1.1.1.2")),
    makeAddChangeForValidation("wrongType", TXTData("Unsupported!"), UNKNOWN)
  )

  private def existingZones = ExistingZones(Set(okZone, sharedZone))

  private val aToDelete = RecordSet(
    okZone.id,
    "aToDelete",
    A,
    123,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("1.1.1.1"))
  )
  private val cnameToDelete = RecordSet(
    okZone.id,
    "cnameToDelete",
    CNAME,
    123,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(CNAMEData(Fqdn("old.com.")))
  )
  private val aToUpdate = RecordSet(
    okZone.id,
    "aToUpdate",
    A,
    123,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(AData("1.1.1.1"))
  )
  private val cnameToUpdate = RecordSet(
    okZone.id,
    "cnameToUpdate",
    CNAME,
    123,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(CNAMEData(Fqdn("old.com.")))
  )
  private val txtToUpdate = RecordSet(
    okZone.id,
    "txtToUpdate",
    TXT,
    123,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(TXTData("old"))
  )
  private val txtToDelete = RecordSet(
    okZone.id,
    "txtToDelete",
    TXT,
    123,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(TXTData("test"))
  )
  private val mxToUpdate = RecordSet(
    okZone.id,
    "mxToUpdate",
    MX,
    123,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(MXData(1, Fqdn("old.com.")))
  )
  private val mxToDelete = RecordSet(
    okZone.id,
    "mxToDelete",
    MX,
    123,
    RecordSetStatus.Active,
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    List(MXData(1, Fqdn("delete.com.")))
  )
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
        mxToDelete
      )
    )

  private val batchChangeRepo = new InMemoryBatchChangeRepository
  private val underTest = new BatchChangeConverter(batchChangeRepo, TestMessageQueue)

  "sendBatchForProcessing" should {
    "successfully generate add RecordSetChange and map IDs for all adds" in {
      val batchChange =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          Instant.now.truncatedTo(ChronoUnit.MILLIS),
          addSingleChangesGood,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved
        )
      val result =
        underTest
          .sendBatchForProcessing(
            batchChange,
            existingZones,
            ChangeForValidationMap(addChangeForValidationGood.map(_.validNel), existingRecordSets),
            None
          )
          .value.unsafeRunSync().toOption.get

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
          Instant.now.truncatedTo(ChronoUnit.MILLIS),
          deleteSingleChangesGood,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved
        )
      val result =
        underTest
          .sendBatchForProcessing(
            batchChange,
            existingZones,
            ChangeForValidationMap(
              deleteRRSetChangeForValidationGood.map(_.validNel),
              existingRecordSets
            ),
            None
          )
          .value.unsafeRunSync().toOption.get

      val rsChanges = result.recordSetChanges

      // validate recordset change basics generated from batch
      rsChanges.length shouldBe 4
      validateRecordSetChange(aToDelete.name, rsChanges, batchChange, RecordSetChangeType.Delete)
      validateRecordSetChange(
        cnameToDelete.name,
        rsChanges,
        batchChange,
        RecordSetChangeType.Delete
      )
      validateRecordSetChange(txtToDelete.name, rsChanges, batchChange, RecordSetChangeType.Delete)
      validateRecordSetChange(mxToDelete.name, rsChanges, batchChange, RecordSetChangeType.Delete)

      // confirm the IDs in the generated change
      rsChanges.map(_.recordSet.id) should contain theSameElementsAs List(
        aToDelete.id,
        cnameToDelete.id,
        txtToDelete.id,
        mxToDelete.id
      )

      // validate statuses unchanged as returned
      result.batchChange shouldBe batchChange
    }

    "successfully generate update RecordSetChange and map IDs for mix of adds/deletes" in {
      val batchChange =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          Instant.now.truncatedTo(ChronoUnit.MILLIS),
          updateSingleChangesGood,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved
        )
      val result =
        underTest
          .sendBatchForProcessing(
            batchChange,
            existingZones,
            ChangeForValidationMap(
              updateChangeForValidationGood.map(_.validNel),
              existingRecordSets
            ),
            None
          )
          .value.unsafeRunSync().toOption.get

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
        mxToUpdate.id
      )

      // validate statuses unchanged as returned
      result.batchChange shouldBe batchChange
    }

    "successfully handle a combination of adds, updates, and deletes" in {
      val changes = addSingleChangesGood ++ deleteSingleChangesGood ++ updateSingleChangesGood
      val changeForValidation = addChangeForValidationGood ++ deleteRRSetChangeForValidationGood ++
        updateChangeForValidationGood
      val batchChange =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          Instant.now.truncatedTo(ChronoUnit.MILLIS),
          changes,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved
        )
      val result =
        underTest
          .sendBatchForProcessing(
            batchChange,
            existingZones,
            ChangeForValidationMap(changeForValidation.map(_.validNel), existingRecordSets),
            None
          )
          .value.unsafeRunSync().toOption.get

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
        batchChangeRepo.getBatchChange(batchChange.id).unsafeRunSync()

      savedBatch shouldBe Some(batchChange)
    }

    "successfully return for an empty batch" in {
      val batchChange =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          Instant.now.truncatedTo(ChronoUnit.MILLIS),
          List(),
          approvalStatus = BatchChangeApprovalStatus.AutoApproved
        )
      val result =
        underTest
          .sendBatchForProcessing(
            batchChange,
            existingZones,
            ChangeForValidationMap(List(), existingRecordSets),
            None
          )
          .value.unsafeRunSync().toOption.get


      result.batchChange shouldBe batchChange
    }

    "set status to failure for changes with queueing issues" in {
      val batchWithBadChange =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          Instant.now.truncatedTo(ChronoUnit.MILLIS),
          singleChangesOneBad,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved
        )
      val result =
        underTest
          .sendBatchForProcessing(
            batchWithBadChange,
            existingZones,
            ChangeForValidationMap(changeForValidationOneBad.map(_.validNel), existingRecordSets),
            None
          )
          .value.unsafeRunSync().toOption.get

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
        batchChangeRepo.getBatchChange(batchWithBadChange.id).unsafeRunSync()
      savedBatch shouldBe Some(returnedBatch)
    }

    "set status to pending when deleting a record that does not exist" in {
      val batchWithBadChange =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          Instant.now.truncatedTo(ChronoUnit.MILLIS),
          singleChangesOneDelete,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved
        )
      val result =
        underTest
          .sendBatchForProcessing(
            batchWithBadChange,
            existingZones,
            ChangeForValidationMap(changeForValidationOneDelete.map(_.validNel), existingRecordSets),
            None
          )
          .value.unsafeRunSync().toOption.get

      val returnedBatch = result.batchChange

      // validate completed status returned
      val receivedChange = returnedBatch.changes(0)
      receivedChange.status shouldBe SingleChangeStatus.Pending
      receivedChange.recordChangeId shouldBe None
      receivedChange.systemMessage shouldBe Some(nonExistentRecordDeleteMessage)
      returnedBatch.changes(0) shouldBe singleChangesOneDelete(0).copy(systemMessage = Some(nonExistentRecordDeleteMessage), status = SingleChangeStatus.Pending)

      // check the update has been made in the DB
      val savedBatch: Option[BatchChange] =
        batchChangeRepo.getBatchChange(batchWithBadChange.id).unsafeRunSync()
      savedBatch shouldBe Some(returnedBatch)
    }

    "return error if an unsupported record is received" in {
      val batchChangeUnsupported =
        BatchChange(
          okUser.id,
          okUser.userName,
          None,
          Instant.now.truncatedTo(ChronoUnit.MILLIS),
          singleChangesOneUnsupported,
          approvalStatus = BatchChangeApprovalStatus.AutoApproved
        )
      val result =
        underTest
          .sendBatchForProcessing(
            batchChangeUnsupported,
            existingZones,
            ChangeForValidationMap(
              changeForValidationOneUnsupported.map(_.validNel),
              existingRecordSets
            ),
            None
          )
          .value.unsafeRunSync().swap.toOption.get

      result shouldBe an[BatchConversionError]

      val notSaved: Option[BatchChange] =
        batchChangeRepo.getBatchChange(batchChangeUnsupported.id).unsafeRunSync()
      notSaved shouldBe None
    }
  }

  "generateAddChange" should {
    val singleAddChange = makeSingleAddChange("shared-rs", AData("1.2.3.4"), A, sharedZone)
    val ownerGroupId = Some("some-owner-group-id")

    "generate record set changes for shared zone without owner group ID if not provided" in {
      val result =
        underTest.generateRecordSetChange(
          Add,
          NonEmptyList.of(singleAddChange),
          sharedZone,
          singleAddChange.typ,
          Set(singleAddChange.recordData),
          okUser.id,
          None,
          None
        )
      result shouldBe defined
      result.foreach(_.recordSet.ownerGroupId shouldBe None)
    }

    "generate record set changes for shared zone with owner group ID if provided" in {
      val result =
        underTest.generateRecordSetChange(
          Add,
          NonEmptyList.of(singleAddChange),
          sharedZone,
          singleAddChange.typ,
          Set(singleAddChange.recordData),
          okUser.id,
          None,
          ownerGroupId
        )
      result shouldBe defined
      result.foreach(_.recordSet.ownerGroupId shouldBe ownerGroupId)
    }

    "generate record set changes for non-shared zone without owner group ID" in {
      val result =
        underTest.generateRecordSetChange(
          Add,
          NonEmptyList.fromListUnsafe(addSingleChangesGood),
          okZone,
          singleAddChange.typ,
          Set(singleAddChange.recordData),
          okUser.id,
          None,
          ownerGroupId
        )
      result shouldBe defined
      result.foreach(_.recordSet.ownerGroupId shouldBe None)
    }
  }

  "generateUpdateChange" should {
    val addChange = makeSingleAddChange("aaaa", AAAAData("2:3:4:5:6:7:8:9"), AAAA, sharedZone)
    val deleteChange = makeSingleDeleteRRSetChange("aaaa", AAAA, sharedZone)

    "not overwrite existing owner group ID for existing record set in shared zone" in {
      val result =
        underTest.generateRecordSetChange(
          Update,
          NonEmptyList.of(deleteChange, addChange),
          sharedZone,
          deleteChange.typ,
          Set(addChange.recordData),
          okUser.id,
          Some(sharedZoneRecord),
          Some("new-owner-group-id")
        )
      result shouldBe defined
      result.foreach(_.recordSet.ownerGroupId shouldBe sharedZoneRecord.ownerGroupId)
    }

    "use specified owner group ID if undefined for existing record set in shared zone" in {
      val ownerGroupId = Some("new-owner-group-id")
      val result =
        underTest.generateRecordSetChange(
          Update,
          NonEmptyList.of(deleteChange, addChange),
          sharedZone,
          addChange.typ,
          Set(addChange.recordData),
          okUser.id,
          Some(sharedZoneRecord.copy(ownerGroupId = None)),
          ownerGroupId
        )
      result shouldBe defined
      result.foreach(_.recordSet.ownerGroupId shouldBe ownerGroupId)
    }

    "generate record set without updating owner group ID for record set in unshared zone" in {
      val result =
        underTest.generateRecordSetChange(
          Update,
          NonEmptyList.of(
            deleteChange.copy(zoneId = Some(okZone.id), zoneName = Some(okZone.name)),
            addChange.copy(zoneId = Some(okZone.id), zoneName = Some(okZone.name))
          ),
          okZone,
          addChange.typ,
          Set(addChange.recordData),
          okUser.id,
          Some(sharedZoneRecord.copy(ownerGroupId = None, zoneId = okZone.id)),
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
                                       typ: RecordSetChangeType
                                     ) = {
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
                                             batchChange: BatchChange
                                           ) = {
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