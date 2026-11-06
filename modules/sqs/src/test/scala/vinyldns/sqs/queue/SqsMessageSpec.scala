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

import com.amazonaws.services.sqs.model.{Message, MessageAttributeValue}
import org.scalatest.EitherValues
import vinyldns.core.TestRecordSetData.pendingCreateAAAA
import vinyldns.core.TestZoneData.zoneChangePending
import vinyldns.core.domain.batch.BatchChangeCommand
import vinyldns.core.domain.zone.{ZoneChangeType, ZoneCommand}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue.TestQueueSigning.signing
import vinyldns.sqs.queue.SqsMessageType.{
  SqsBatchChangeMessage,
  SqsRecordSetChangeMessage,
  SqsZoneChangeMessage
}

import scala.collection.JavaConverters._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SqsMessageSpec extends AnyWordSpec with Matchers with EitherValues with ProtobufConversions {
  import SqsMessage._

  "parseSqsMessage" should {
    "authenticate every zone operation in single and batch sends" in {
      val commands: List[ZoneCommand] =
        List(ZoneChangeType.Create, ZoneChangeType.Update, ZoneChangeType.Delete, ZoneChangeType.Sync, ZoneChangeType.AutomatedSync)
          .map(typ => zoneChangePending.copy(changeType = typ, id = typ.toString))
      val requests = SqsMessageQueue.toSendMessageBatchRequest(cats.data.NonEmptyList.fromListUnsafe(commands), signing)
      val entries = requests.flatMap(_.getEntries.asScala)
      entries.zip(commands).foreach { case (entry, command) =>
        val message = new Message().withBody(entry.getMessageBody).withMessageAttributes(entry.getMessageAttributes)
        parseSqsMessage(message, signing).right.value.command shouldBe command
      }
    }

    "reject unsigned and altered messages without trusting SQS message IDs or attributes" in {
      val request = SqsMessageQueue.toSendMessageRequest(pendingCreateAAAA, signing)
      val message = new Message().withBody(request.getMessageBody).withMessageAttributes(request.getMessageAttributes)
      val raw = toPB(pendingCreateAAAA).toByteArray
      parseSqsMessage(message.clone().withBody(Base64.getEncoder.encodeToString(raw)), signing).isLeft shouldBe true
      val changed = Base64.getDecoder.decode(request.getMessageBody)
      changed(changed.length - 33) = (changed(changed.length - 33) ^ 1).toByte
      parseSqsMessage(message.clone().withBody(Base64.getEncoder.encodeToString(changed)), signing).isLeft shouldBe true
      val wrongType = Map(SqsZoneChangeMessage.messageAttribute).asJava
      parseSqsMessage(message.clone().withMessageAttributes(wrongType), signing).isLeft shouldBe true
    }

    "reject disagreement between the signed ID and protobuf ID" in {
      val bytes = signing.sign(1, "different-id", toPB(pendingCreateAAAA).toByteArray)
      val message = new Message().withBody(Base64.getEncoder.encodeToString(bytes))
        .withMessageAttributes(Map(SqsRecordSetChangeMessage.messageAttribute).asJava)
      parseSqsMessage(message, signing).isLeft shouldBe true
    }

    "build the command for zone change correctly" in {
      val bytes = signing.sign(2, zoneChangePending.id, toPB(zoneChangePending).toByteArray)
      val messageBody = Base64.getEncoder.encodeToString(bytes)
      val msg = new Message()
        .withBody(messageBody)
        .withMessageAttributes(
          Map(
            "message-type" -> new MessageAttributeValue()
              .withStringValue(SqsZoneChangeMessage.name)
              .withDataType("String")
          ).asJava
        )

      parseSqsMessage(msg, signing).right.value.command shouldBe zoneChangePending
    }

    "build the command for record set change correctly" in {
      val bytes = signing.sign(1, pendingCreateAAAA.id, toPB(pendingCreateAAAA).toByteArray)
      val messageBody = Base64.getEncoder.encodeToString(bytes)
      val msg = new Message()
        .withBody(messageBody)
        .withMessageAttributes(
          Map(
            "message-type" -> new MessageAttributeValue()
              .withStringValue(SqsRecordSetChangeMessage.name)
              .withDataType("String")
          ).asJava
        )

      parseSqsMessage(msg, signing).right.value.command shouldBe pendingCreateAAAA
    }

    "build the command for batch change command correctly" in {
      val batchChangeCommand = BatchChangeCommand("some-id")
      val bytes = signing.sign(3, batchChangeCommand.id, batchChangeCommand.id.getBytes)
      val messageBody = Base64.getEncoder.encodeToString(bytes)
      val msg = new Message()
        .withBody(messageBody)
        .withMessageAttributes(
          Map(
            "message-type" -> new MessageAttributeValue()
              .withStringValue(SqsBatchChangeMessage.name)
              .withDataType("String")
          ).asJava
        )

      parseSqsMessage(msg, signing).right.value.command shouldBe batchChangeCommand
    }

    "return EmptySqsMessageContents when processing an empty message" in {
      val message = new Message()
        .withMessageId("test-id")
        .withMessageAttributes(
          Map(
            "message-type" -> new MessageAttributeValue()
              .withStringValue(SqsZoneChangeMessage.name)
              .withDataType("String")
          ).asJava
        )

      parseSqsMessage(message, signing) shouldBe Left(EmptySqsMessageContents(message.getMessageId))
    }
  }
}
