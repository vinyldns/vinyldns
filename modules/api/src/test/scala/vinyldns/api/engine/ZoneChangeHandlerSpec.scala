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
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.DB
import vinyldns.core.TestZoneData.zoneChangePending
import vinyldns.core.domain.record.{RecordSetCacheRepository, RecordSetRepository}
import vinyldns.core.domain.zone.ZoneRepository.DuplicateZoneError
import vinyldns.core.domain.zone._
import vinyldns.core.domain.Encrypted
import vinyldns.mysql.TransactionProvider


class ZoneChangeHandlerSpec extends AnyWordSpec with Matchers with MockitoSugar {

  trait Fixture {
    val mockZoneRepo = mock[ZoneRepository]
    val mockChangeRepo = mock[ZoneChangeRepository]
    val mockRecordSetRepo = mock[RecordSetRepository]
    val mockRecordSetDataRepo = mock[RecordSetCacheRepository]
    val db = mock[DB]
    val transactions = new TransactionProvider {
      override def executeWithinTransaction[A](execution: DB => IO[A]): IO[A] = execution(db)
    }
    doReturn(IO.unit).when(mockRecordSetRepo)
      .deleteRecordSetsInZone(any[DB], anyString(), anyString())
    doReturn(IO.unit).when(mockRecordSetDataRepo)
      .deleteRecordSetDataInZone(any[DB], anyString(), anyString())

    val change = zoneChangePending
    val test = ZoneChangeHandler(mockZoneRepo, mockChangeRepo, mockRecordSetRepo, mockRecordSetDataRepo, transactions)
  }

  "ZoneChangeHandler" should {
    "save the zone change and zone" in new Fixture {
      doReturn(IO.pure(Some(change.zone))).when(mockZoneRepo).getZone(change.zone.id)
      doReturn(IO.pure(Right(change.zone))).when(mockZoneRepo).save(change.zone)
      doReturn(IO.pure(change)).when(mockChangeRepo).save(any[ZoneChange])

      test(change).unsafeRunSync()

      val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
      verify(mockChangeRepo).save(changeCaptor.capture())

      val savedChange = changeCaptor.getValue
      savedChange.status shouldBe ZoneChangeStatus.Synced
    }
  }

  "save the zone change as failed if the zone does not save" in new Fixture {
    doReturn(IO.pure(Some(change.zone))).when(mockZoneRepo).getZone(change.zone.id)
    doReturn(IO.pure(Left(DuplicateZoneError("message")))).when(mockZoneRepo).save(change.zone)
    doReturn(IO.pure(change)).when(mockChangeRepo).save(any[ZoneChange])

    test(change).unsafeRunSync()

    val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
    verify(mockChangeRepo).save(changeCaptor.capture())

    val savedChange = changeCaptor.getValue
    savedChange.status shouldBe ZoneChangeStatus.Failed
    savedChange.systemMessage shouldBe Some("Zone with name \"message\" already exists.")
  }

  "save a delete zone change as synced if recordset delete succeeds" in new Fixture {
    val deleteChange = change.copy(changeType = ZoneChangeType.Delete)

    doReturn(IO.pure(Some(deleteChange.zone))).when(mockZoneRepo).getZone(deleteChange.zone.id)
    doReturn(IO.pure(Right(deleteChange.zone))).when(mockZoneRepo).save(any[Zone])
    doReturn(IO.pure(deleteChange)).when(mockChangeRepo).save(any[ZoneChange])

    test(deleteChange).unsafeRunSync()

    val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
    verify(mockChangeRepo).save(changeCaptor.capture())

    val savedChange = changeCaptor.getValue
    savedChange.status shouldBe ZoneChangeStatus.Synced
    savedChange.zone.status shouldBe ZoneStatus.Deleted
    verify(mockRecordSetRepo).deleteRecordSetsInZone(db, change.zone.id, change.zone.name)
    verify(mockRecordSetDataRepo).deleteRecordSetDataInZone(db, change.zone.id, change.zone.name)
  }

  "propagate recordset cleanup failures without deleting the zone or reporting success" in new Fixture {
    val deleteChange = change.copy(changeType = ZoneChangeType.Delete)

    doReturn(IO.pure(Some(deleteChange.zone))).when(mockZoneRepo).getZone(deleteChange.zone.id)
    doReturn(IO.pure(Right(deleteChange.zone))).when(mockZoneRepo).save(any[Zone])
    val failure = new RuntimeException("cleanup failed")
    doReturn(IO.raiseError[Unit](failure)).when(mockRecordSetRepo)
      .deleteRecordSetsInZone(db, deleteChange.zone.id, deleteChange.zone.name)
    doReturn(IO.pure(deleteChange)).when(mockChangeRepo).save(any[ZoneChange])

    test(deleteChange).attempt.unsafeRunSync() shouldBe Left(failure)
    verify(mockZoneRepo, never()).save(any[Zone])
    verify(mockChangeRepo, never()).save(any[ZoneChange])
  }

  "fail a zone change when the zone is not found in the repository" in new Fixture {
    doReturn(IO.pure(None)).when(mockZoneRepo).getZone(change.zone.id)
    doReturn(IO.pure(change)).when(mockChangeRepo).save(any[ZoneChange])

    test(change).unsafeRunSync()

    val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
    verify(mockChangeRepo).save(changeCaptor.capture())
    changeCaptor.getValue.status shouldBe ZoneChangeStatus.Failed
    changeCaptor.getValue.systemMessage.get should include("not found in repository")
  }
  "use the DB zone id and name for Delete scoping, not message-supplied values" in new Fixture {
    // Zone scoping for deletion uses authoritative zone configuration
    val dbZone = change.zone
    val modifiedZone = change.zone.copy(name = "modified.zone.")
    val deleteChange = change.copy(changeType = ZoneChangeType.Delete, zone = modifiedZone)

    doReturn(IO.pure(Some(dbZone))).when(mockZoneRepo).getZone(change.zone.id)
    doReturn(IO.pure(Right(dbZone))).when(mockZoneRepo).save(any[Zone])
    doReturn(IO.pure(deleteChange)).when(mockChangeRepo).save(any[ZoneChange])

    test(deleteChange).unsafeRunSync()

    // zone save must use the deleted DB zone (not the attacker-supplied name)
    val zoneCaptor = ArgumentCaptor.forClass(classOf[Zone])
    verify(mockZoneRepo).save(zoneCaptor.capture())
    zoneCaptor.getValue.status shouldBe ZoneStatus.Deleted
    zoneCaptor.getValue.id shouldBe dbZone.id
    zoneCaptor.getValue.name shouldBe dbZone.name
    val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
    verify(mockChangeRepo).save(changeCaptor.capture())
    changeCaptor.getValue.status shouldBe ZoneChangeStatus.Synced
  }
  "fail an Update zone change when the zone is not found in the repository" in new Fixture {
    val updateChange = change.copy(changeType = ZoneChangeType.Update)
    doReturn(IO.pure(None)).when(mockZoneRepo).getZone(updateChange.zone.id)
    doReturn(IO.pure(updateChange)).when(mockChangeRepo).save(any[ZoneChange])

    test(updateChange).unsafeRunSync()

    val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
    verify(mockChangeRepo).save(changeCaptor.capture())
    changeCaptor.getValue.status shouldBe ZoneChangeStatus.Failed
    // zone save must NOT be called when zone does not exist
    verify(mockZoneRepo, never()).save(any[Zone])
  }
  "preserve DB ownership, ACL and sharing while persisting message backend and connection updates" in new Fixture {
    val dbZone = change.zone
    val modifiedZone = change.zone.copy(
      adminGroupId = "modified-group",
      shared = true,
      backendId = Some("modified-backend"),
      connection = Some(ZoneConnection("modified.server", "key", Encrypted("xyz"), "10.2.2.2")),
      transferConnection =
        Some(ZoneConnection("transfer.server", "transfer-key", Encrypted("abc"), "10.2.2.3")),
      acl = ZoneACL(Set(ACLRule(AccessLevel.Delete)))
    )
    val updateChange = change.copy(changeType = ZoneChangeType.Update, zone = modifiedZone)

    doReturn(IO.pure(Some(dbZone))).when(mockZoneRepo).getZone(change.zone.id)
    doReturn(IO.pure(Right(dbZone))).when(mockZoneRepo).save(any[Zone])
    doReturn(IO.pure(updateChange)).when(mockChangeRepo).save(any[ZoneChange])

    test(updateChange).unsafeRunSync()

    val zoneCaptor = ArgumentCaptor.forClass(classOf[Zone])
    verify(mockZoneRepo).save(zoneCaptor.capture())
    val saved = zoneCaptor.getValue
    saved.adminGroupId shouldBe dbZone.adminGroupId
    saved.shared shouldBe dbZone.shared
    saved.acl shouldBe dbZone.acl
    saved.backendId shouldBe modifiedZone.backendId
    saved.connection shouldBe modifiedZone.connection
    saved.transferConnection shouldBe modifiedZone.transferConnection
  }
  "remove explicit connections when switching to a configured backend" in new Fixture {
    val dbZone = change.zone.copy(transferConnection = change.zone.connection)
    val modifiedZone = dbZone.copy(
      backendId = Some("configured-backend"),
      connection = None,
      transferConnection = None
    )
    val updateChange = change.copy(changeType = ZoneChangeType.Update, zone = modifiedZone)

    doReturn(IO.pure(Some(dbZone))).when(mockZoneRepo).getZone(change.zone.id)
    doReturn(IO.pure(Right(modifiedZone))).when(mockZoneRepo).save(any[Zone])
    doReturn(IO.pure(updateChange)).when(mockChangeRepo).save(any[ZoneChange])

    test(updateChange).unsafeRunSync()

    verify(mockZoneRepo).save(modifiedZone)
    verify(mockChangeRepo).save(updateChange.copy(status = ZoneChangeStatus.Synced))
  }
  "remove the backend selection when switching to explicit connections" in new Fixture {
    val dbZone = change.zone.copy(backendId = Some("configured-backend"), connection = None)
    val modifiedZone = dbZone.copy(
      backendId = None,
      connection = change.zone.connection,
      transferConnection = change.zone.connection
    )
    val updateChange = change.copy(changeType = ZoneChangeType.Update, zone = modifiedZone)

    doReturn(IO.pure(Some(dbZone))).when(mockZoneRepo).getZone(change.zone.id)
    doReturn(IO.pure(Right(modifiedZone))).when(mockZoneRepo).save(any[Zone])
    doReturn(IO.pure(updateChange)).when(mockChangeRepo).save(any[ZoneChange])

    test(updateChange).unsafeRunSync()

    verify(mockZoneRepo).save(modifiedZone)
    verify(mockChangeRepo).save(updateChange.copy(status = ZoneChangeStatus.Synced))
  }
  "finish a delete retry when the zone has already been removed" in new Fixture {
    val deletion = change.copy(changeType = ZoneChangeType.Delete, zone = change.zone.copy(status = ZoneStatus.Deleted))
    doReturn(IO.pure(None)).when(mockZoneRepo).getZone(change.zone.id)
    doReturn(IO.pure(deletion)).when(mockChangeRepo).save(any[ZoneChange])
    test(deletion).unsafeRunSync()
    verify(mockChangeRepo).save(deletion.copy(status = ZoneChangeStatus.Synced))
    verify(mockZoneRepo, never()).save(any[Zone])
  }
}
