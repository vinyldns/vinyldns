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
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.VinylDNSTestData
import vinyldns.core.domain.zone.{ZoneChange, ZoneChangeRepository, ZoneChangeStatus, ZoneRepository}

import scala.concurrent.ExecutionContext

class ZoneChangeHandlerSpec extends WordSpec with Matchers with MockitoSugar with VinylDNSTestData {

  implicit val ec = ExecutionContext.global

  "ZoneChangeHandler" should {
    "save the zone change and zone" in {
      val mockZoneRepo = mock[ZoneRepository]
      val mockChangeRepo = mock[ZoneChangeRepository]
      val change = zoneChangePending

      doReturn(IO.pure(change.zone)).when(mockZoneRepo).save(change.zone)
      doReturn(IO.pure(change)).when(mockChangeRepo).save(any[ZoneChange])

      val test = ZoneChangeHandler(mockZoneRepo, mockChangeRepo)
      test(change).unsafeRunSync()

      val changeCaptor = ArgumentCaptor.forClass(classOf[ZoneChange])
      verify(mockChangeRepo).save(changeCaptor.capture())

      val savedChange = changeCaptor.getValue
      savedChange.status shouldBe ZoneChangeStatus.Synced
    }
  }
}
