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

import cats.data.NonEmptyList
import com.amazonaws.services.sqs.model._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.proto.VinylDNSProto

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class SqsMessageQueueSpec extends WordSpec with Matchers with MockitoSugar with SqsConversions {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  "parseMessageType" should {
    "return the appropriate message type" in {
      parseMessageType("SqsRecordSetChangeMessage") shouldBe SqsRecordSetChangeMessage
      parseMessageType("SqsZoneChangeMessage") shouldBe SqsZoneChangeMessage
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
  }

  "fromMessage" should {
    "build the command for zone change correctly" in {
      val bytes = toPB(zoneChangePending).toByteArray
      val messageBody = Base64.getEncoder.encodeToString(bytes)
      val msg = new Message()
        .withBody(messageBody)
        .withMessageAttributes(
          Map(
            "message-type" -> new MessageAttributeValue()
              .withStringValue(SqsZoneChangeMessage.name)
              .withDataType("String")
          ).asJava)

      fromMessage(msg) shouldBe zoneChangePending
    }

    "build the command for record set change correctly" in {
      val bytes = toPB(pendingCreateAAAA).toByteArray
      val messageBody = Base64.getEncoder.encodeToString(bytes)
      val msg = new Message()
        .withBody(messageBody)
        .withMessageAttributes(
          Map(
            "message-type" -> new MessageAttributeValue()
              .withStringValue(SqsRecordSetChangeMessage.name)
              .withDataType("String")
          ).asJava)

      fromMessage(msg) shouldBe pendingCreateAAAA
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
      val result = toSendBatchResult(batchResult, cmds)

      result.successes should contain theSameElementsAs successes
      result.failures.map(_._2) should contain theSameElementsAs failures
    }
  }
}
