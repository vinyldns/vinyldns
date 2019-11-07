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

package vinyldns.sqs.queue

import java.util.Base64
import java.util.concurrent.TimeUnit.{HOURS, SECONDS}

import cats.data.NonEmptyList
import com.amazonaws.services.sqs.model._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.batch.BatchChangeCommand
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.proto.VinylDNSProto
import vinyldns.sqs.queue.SqsMessageType._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class SqsMessageQueueSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with ProtobufConversions {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  import SqsMessageQueue._

  implicit val largeRsChange: RecordSetChange =
    makeTestAddChange(rsOk, okZone).copy(singleBatchChangeIds = List("a" * 20000))

  "parseMessageType" should {
    "return the appropriate message type" in {
      fromString("SqsRecordSetChangeMessage") shouldBe Right(SqsRecordSetChangeMessage)
      fromString("SqsZoneChangeMessage") shouldBe Right(SqsZoneChangeMessage)
      fromString("SqsBatchChangeMessage") shouldBe Right(SqsBatchChangeMessage)
    }
  }

  "toSendMessageBatchRequest" should {
    "create a single batch request if total message payload is under 256KB" in {
      val messageSize = messageData(largeRsChange).getBytes().length
      val requestMaxChanges = MAXIMUM_BATCH_SIZE / messageSize
      val recordSetChanges = for (_ <- 1 until requestMaxChanges)
        yield
          makeTestAddChange(rsOk, okZone).copy(
            singleBatchChangeIds = largeRsChange.singleBatchChangeIds)
      val commands = NonEmptyList.fromListUnsafe(recordSetChanges.toList)

      toSendMessageBatchRequest(commands).length shouldBe 1
    }

    "create multiple batch requests if total message payload is over 256KB" in {
      val messageSize = messageData(largeRsChange).getBytes().length
      val requestMaxChanges = MAXIMUM_BATCH_SIZE / messageSize
      val requestTotalChanges = requestMaxChanges * 4 // Create four batches
      val recordSetChanges = for (_ <- 1 until requestTotalChanges)
        yield
          makeTestAddChange(rsOk, okZone).copy(
            singleBatchChangeIds = largeRsChange.singleBatchChangeIds)
      val commands = NonEmptyList.fromListUnsafe(recordSetChanges.toList)
      toSendMessageBatchRequest(commands).length shouldBe 4
    }

    "send a single batch if batch contains fewer than ten items and total payload size is less than 256KB" in {
      val requestTotalChanges = 7
      val recordSetChanges = for (_ <- 1 to requestTotalChanges)
        yield makeTestAddChange(rsOk, okZone)
      val commands = NonEmptyList.fromListUnsafe(recordSetChanges.toList)

      val result = toSendMessageBatchRequest(commands)

      result.length shouldBe 1
      result.head.getEntries.size shouldBe requestTotalChanges
    }

    "partition batches with more than ten items into groups of ten" in {
      val recordSetChanges = for (_ <- 1 to 100)
        yield makeTestAddChange(rsOk, okZone)
      val commands = NonEmptyList.fromListUnsafe(recordSetChanges.toList)

      val result = toSendMessageBatchRequest(commands)
      result.length shouldBe 10
      result.foreach(_.getEntries.size() shouldBe SqsMessageQueue.MAXIMUM_BATCH_ENTRY_COUNT)
    }

    "allow up to ten items in a batch" in {
      val rsChange = makeTestAddChange(rsOk, okZone).copy(singleBatchChangeIds = List("a" * 1800))
      val messageSize = messageData(rsChange).getBytes().length

      val requestMaxChanges = MAXIMUM_BATCH_SIZE / messageSize
      val requestTotalChanges = requestMaxChanges * 2
      val recordSetChanges = for (_ <- 1 until requestTotalChanges)
        yield
          makeTestAddChange(rsOk, okZone).copy(singleBatchChangeIds = rsChange.singleBatchChangeIds)
      val commands = NonEmptyList.fromListUnsafe(recordSetChanges.toList)

      val result = toSendMessageBatchRequest(commands)
      result.head.getEntries.size() shouldBe 10
      result.foreach(_.getEntries.size() should be <= SqsMessageQueue.MAXIMUM_BATCH_ENTRY_COUNT)
    }
  }

  "toSendMessageRequest" should {
    "build the message correctly for record set changes" in {
      val result = toSendMessageRequest(pendingCreateAAAA)

      result.getMessageAttributes
        .get("message-type")
        .getStringValue shouldBe SqsRecordSetChangeMessage.name

      val messageBytes = Base64.getDecoder.decode(result.getMessageBody)
      val decoded = fromPB(VinylDNSProto.RecordSetChange.parseFrom(messageBytes))

      decoded shouldBe pendingCreateAAAA
    }

    "build the message correctly for zone changes" in {
      val result = toSendMessageRequest(zoneChangePending)

      result.getMessageAttributes
        .get("message-type")
        .getStringValue shouldBe SqsZoneChangeMessage.name

      val messageBytes = Base64.getDecoder.decode(result.getMessageBody)
      val decoded = fromPB(VinylDNSProto.ZoneChange.parseFrom(messageBytes))

      decoded shouldBe zoneChangePending
    }

    "build the message correctly for batch changes" in {
      val batchChangeCommand = BatchChangeCommand("some-id")
      val result = toSendMessageRequest(batchChangeCommand)

      result.getMessageAttributes
        .get("message-type")
        .getStringValue shouldBe SqsBatchChangeMessage.name

      val messageBytes = Base64.getDecoder.decode(result.getMessageBody)
      val decoded = BatchChangeCommand(new String(messageBytes))

      decoded shouldBe batchChangeCommand
    }
  }

  "toSendBatchResult" should {
    "build a SendBatchResult with successes and failures" in {
      val successes = List(pendingCreateAAAA, makeTestPendingZoneChange(okZone))
      val failures = List(zoneChangePending, pendingCreateCNAME)
      val batchResult = new SendMessageBatchResult()
        .withSuccessful(successes.map { cmd =>
          new SendMessageBatchResultEntry()
            .withId(cmd.id)
        }.asJava)
        .withFailed(failures.map { cmd =>
          new BatchResultErrorEntry()
            .withId(cmd.id)
            .withMessage("error message")
        }.asJava)
      val cmds = NonEmptyList.fromListUnsafe(successes ++ failures)
      val result = toSendBatchResult(List(batchResult), cmds)

      result.successes should contain theSameElementsAs successes
      result.failures.map(_._2) should contain theSameElementsAs failures
    }
  }

  "validateMessageTimeout" should {
    "throw an InvalidMessageTimeout error when duration is negative" in {
      val duration = new FiniteDuration(-1, SECONDS)
      validateMessageTimeout(duration) shouldBe Left(InvalidMessageTimeout(duration.toSeconds))
    }

    "throw an InvalidMessageTimeout error when duration is greater than max (43200)" in {
      val duration = new FiniteDuration(13, HOURS)
      validateMessageTimeout(duration) shouldBe Left(InvalidMessageTimeout(duration.toSeconds))
    }

    "return a FiniteDuration when duration is valid" in {
      val duration = new FiniteDuration(7, HOURS)
      validateMessageTimeout(duration) shouldBe Right(duration)
    }
  }
}
