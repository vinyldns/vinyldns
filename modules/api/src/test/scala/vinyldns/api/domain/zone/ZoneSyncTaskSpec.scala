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

package vinyldns.api.domain.zone

import cats.effect.IO
import cats.scalatest.ValidatedMatchers
import com.typesafe.config.{Config, ConfigFactory}
import org.mockito.Mockito.doReturn
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.zone.{ZoneRepository, ZoneStatus}
import vinyldns.core.queue.{MessageQueue, MessageQueueConfig, MessageQueueLoader}
import pureconfig._
import pureconfig.generic.auto._

class ZoneSyncTaskSpec extends AnyWordSpec with Matchers with ValidatedMatchers {

  private val mockZoneRepo = mock[ZoneRepository]
  private val okZoneWithSchedule = okZone.copy(recurrenceSchedule = Some("0/1 * * ? * *"), scheduleRequestor = Some("okUser"))
  private val xyzZoneWithSchedule = xyzZone.copy(recurrenceSchedule = Some("0/1 * * ? * *"), scheduleRequestor = Some("xyzUser"))
  private val sqsQueueConfig: Config =
    ConfigFactory.parseString(
      s"""
         |    class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
         |    polling-interval = 250.millis
         |    messages-per-poll = 10
         |    max-retries = 100
         |
         |    settings {
         |      access-key = "test"
         |      secret-key = "test"
         |      signing-region = "us-east-1"
         |      service-endpoint = "http://localhost:19003/"
         |      queue-name = "sqs-override-name"
         |    }
         |    """.stripMargin
    )
  private val messageConfig: MessageQueueConfig =
    ConfigSource.fromConfig(sqsQueueConfig).loadOrThrow[MessageQueueConfig]
  private val queue: MessageQueue = MessageQueueLoader.load(messageConfig).unsafeRunSync()

  private val underTest = new ZoneSyncTask(
    mockZoneRepo,
    queue
  )


  "getZoneWithSchedule" should {
    "get zones which are scheduled for zone sync" in {
      val zones = List(okZoneWithSchedule, abcZone, xyzZoneWithSchedule)
      val result = underTest.getZonesWithSchedule(zones)

      result shouldBe List(okZoneWithSchedule.id, xyzZoneWithSchedule.id)
    }

    "get empty list when no zone is scheduled for zone sync" in {
      val zones = List(xyzZone, abcZone)
      val result = underTest.getZonesWithSchedule(zones)

      result shouldBe List.empty
    }
  }

  "getZoneChanges" should {
    "return zone changes for zones that have zone sync scheduled" in {
      doReturn(IO.pure(Set(okZoneWithSchedule, xyzZoneWithSchedule))).when(mockZoneRepo).getZones(Set(okZoneWithSchedule.id, xyzZoneWithSchedule.id))
      val result = underTest.getZoneChanges(mockZoneRepo, List(okZoneWithSchedule.id, xyzZoneWithSchedule.id)).unsafeRunSync()

      result.map(zoneChange => zoneChange.zone.name) shouldBe Set(okZoneWithSchedule.name, xyzZoneWithSchedule.name)
      result.map(zoneChange => zoneChange.zone.recurrenceSchedule) shouldBe Set(okZoneWithSchedule.recurrenceSchedule, xyzZoneWithSchedule.recurrenceSchedule)
      result.map(zoneChange => zoneChange.zone.scheduleRequestor) shouldBe Set(okZoneWithSchedule.scheduleRequestor, xyzZoneWithSchedule.scheduleRequestor)
      result.map(zoneChange => zoneChange.zone.status) shouldBe Set(okZoneWithSchedule.copy(status = ZoneStatus.Syncing).status, xyzZoneWithSchedule.copy(status = ZoneStatus.Syncing).status)
    }
  }
}
