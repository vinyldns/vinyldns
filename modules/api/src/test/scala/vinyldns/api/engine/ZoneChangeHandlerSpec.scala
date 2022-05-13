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

    doReturn(IO.pure(Right(deleteChange.zone))).when(mockZoneRepo).save(deleteChange.zone)
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

    doReturn(IO.pure(Right(deleteChange.zone))).when(mockZoneRepo).save(deleteChange.zone)
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
}
