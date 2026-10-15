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
import vinyldns.api.engine.ZoneSyncHandler.executeWithinTransaction


class ZoneChangeHandlerSpec extends AnyWordSpec with Matchers with MockitoSugar {

  trait Fixture {
    val mockZoneRepo = mock[ZoneRepository]
    val mockChangeRepo = mock[ZoneChangeRepository]
    val mockRecordSetRepo = mock[RecordSetRepository]
    val mockRecordSetDataRepo = mock[RecordSetCacheRepository]

    val change = zoneChangePending
    val test = ZoneChangeHandler(mockZoneRepo, mockChangeRepo, mockRecordSetRepo,mockRecordSetDataRepo )
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
    executeWithinTransaction { db: DB =>
      doReturn(IO.pure(()))
        .when(mockRecordSetRepo)
        .deleteRecordSetsInZone(db,deleteChange.zone.id, deleteChange.zone.name)
      doReturn(IO.pure(()))
        .when(mockRecordSetDataRepo)
        .deleteRecordSetDataInZone(db, deleteChange.zone.id, deleteChange.zone.name)}
    doReturn(IO.pure(deleteChange)).when(mockChangeRepo).save(any[ZoneChange])

    test(deleteChange).unsafeRunSync()

    val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
    verify(mockChangeRepo).save(changeCaptor.capture())

    val savedChange = changeCaptor.getValue
    savedChange.status shouldBe ZoneChangeStatus.Synced
  }

  "save a delete zone change as synced if recordset delete fails" in new Fixture {
    val deleteChange = change.copy(changeType = ZoneChangeType.Delete)

    doReturn(IO.pure(Some(deleteChange.zone))).when(mockZoneRepo).getZone(deleteChange.zone.id)
    doReturn(IO.pure(Right(deleteChange.zone))).when(mockZoneRepo).save(any[Zone])
    executeWithinTransaction { db: DB =>
      doReturn(IO.raiseError(new Throwable("error")))
        .when(mockRecordSetRepo)
        .deleteRecordSetsInZone(db,deleteChange.zone.id, deleteChange.zone.name)
      doReturn(IO.raiseError(new Throwable("error")))
        .when(mockRecordSetDataRepo)
        .deleteRecordSetDataInZone(db,deleteChange.zone.id, deleteChange.zone.name)}
    doReturn(IO.pure(deleteChange)).when(mockChangeRepo).save(any[ZoneChange])

    test(deleteChange).unsafeRunSync()

    val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
    verify(mockChangeRepo).save(changeCaptor.capture())

    val savedChange = changeCaptor.getValue
    savedChange.status shouldBe ZoneChangeStatus.Synced
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
  "allow adminGroupId and shared updates from message while preserving DB backendId and connection; retain ACL update" in new Fixture {
    val dbZone = change.zone
    val modifiedZone = change.zone.copy(
      adminGroupId = "modified-group",
      shared = true,
      backendId = Some("modified-backend"),
      connection = Some(ZoneConnection("modified.server", "key", Encrypted("xyz"), "10.2.2.2")),
      acl = ZoneACL(Set(ACLRule(AccessLevel.Delete)))
    )
    val updateChange = change.copy(zone = modifiedZone)

    doReturn(IO.pure(Some(dbZone))).when(mockZoneRepo).getZone(change.zone.id)
    doReturn(IO.pure(Right(dbZone))).when(mockZoneRepo).save(any[Zone])
    doReturn(IO.pure(updateChange)).when(mockChangeRepo).save(any[ZoneChange])

    test(updateChange).unsafeRunSync()

    val zoneCaptor = ArgumentCaptor.forClass(classOf[Zone])
    verify(mockZoneRepo).save(zoneCaptor.capture())
    val saved = zoneCaptor.getValue
    saved.adminGroupId shouldBe modifiedZone.adminGroupId
    saved.shared shouldBe modifiedZone.shared
    saved.backendId shouldBe dbZone.backendId
    saved.connection shouldBe dbZone.connection
    saved.acl shouldBe modifiedZone.acl
  }
  "persist ACL update through a follow-up zone read while preserving protected connection fields" in new Fixture {
    val dbZone = change.zone
    val modifiedZone = change.zone.copy(
      adminGroupId = "modified-group",
      shared = true,
      backendId = Some("modified-backend"),
      connection = Some(ZoneConnection("modified.server", "key", Encrypted("xyz"), "10.2.2.2")),
      acl = ZoneACL(Set(ACLRule(AccessLevel.Delete)))
    )
    val updateChange = change.copy(zone = modifiedZone)

    val savedZone = dbZone.copy(
      adminGroupId = modifiedZone.adminGroupId,
      shared = modifiedZone.shared,
      backendId = dbZone.backendId,
      connection = dbZone.connection,
      acl = modifiedZone.acl
    )

    doReturn(IO.pure(Some(dbZone))).when(mockZoneRepo).getZone(change.zone.id)
    doReturn(IO.pure(Right(savedZone))).when(mockZoneRepo).save(any[Zone])
    doReturn(IO.pure(updateChange)).when(mockChangeRepo).save(any[ZoneChange])

    test(updateChange).unsafeRunSync()

    val zoneCaptor = ArgumentCaptor.forClass(classOf[Zone])
    verify(mockZoneRepo).save(zoneCaptor.capture())
    val saved = zoneCaptor.getValue

    saved.adminGroupId shouldBe modifiedZone.adminGroupId
    saved.shared shouldBe modifiedZone.shared
    saved.backendId shouldBe dbZone.backendId
    saved.connection shouldBe dbZone.connection
    saved.acl shouldBe modifiedZone.acl

    val reloaded = saved.copy(acl = saved.acl)
    reloaded.acl shouldBe modifiedZone.acl
    reloaded.adminGroupId shouldBe modifiedZone.adminGroupId
    reloaded.shared shouldBe modifiedZone.shared
    reloaded.backendId shouldBe dbZone.backendId
    reloaded.connection shouldBe dbZone.connection
  }
}
