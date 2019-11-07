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
import org.scalatest.{EitherValues, Matchers, WordSpec}
import vinyldns.core.TestRecordSetData.pendingCreateAAAA
import vinyldns.core.TestZoneData.zoneChangePending
import vinyldns.core.domain.batch.BatchChangeCommand
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.sqs.queue.SqsMessageType.{
  SqsBatchChangeMessage,
  SqsRecordSetChangeMessage,
  SqsZoneChangeMessage
}

import scala.collection.JavaConverters._

class SqsMessageSpec extends WordSpec with Matchers with EitherValues with ProtobufConversions {
  import SqsMessage._

  "parseSqsMessage" should {
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

      parseSqsMessage(msg).right.value.command shouldBe zoneChangePending
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

      parseSqsMessage(msg).right.value.command shouldBe pendingCreateAAAA
    }

    "build the command for batch change command correctly" in {
      val batchChangeCommand = BatchChangeCommand("some-id")
      val bytes = batchChangeCommand.id.getBytes
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

      parseSqsMessage(msg).right.value.command shouldBe batchChangeCommand
    }

    "return EmptySqsMessageContents when processing an empty message" in {
      val message = new Message()
        .withMessageId("test-id")
        .withMessageAttributes(
          Map(
            "message-type" -> new MessageAttributeValue()
              .withStringValue(SqsZoneChangeMessage.name)
              .withDataType("String")
          ).asJava)

      parseSqsMessage(message) shouldBe Left(EmptySqsMessageContents(message.getMessageId))
    }
  }
}
