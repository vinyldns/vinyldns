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

package vinyldns.api.engine.sqs

import java.util.Base64

import cats.effect.IO
import com.amazonaws.services.sqs.model.{
  Message,
  MessageAttributeValue,
  SendMessageRequest,
  SendMessageResult
}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.VinylDNSTestData
import vinyldns.proto.VinylDNSProto

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class SqsConvertersSpec extends WordSpec with Matchers with VinylDNSTestData with MockitoSugar {

  import vinyldns.api.engine.sqs.SqsConverters._

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

  "sendCommand" should {
    "forward the command as a message to sqs" in {
      val mockConn = mock[SqsConnection]
      val mockResponse = mock[SendMessageResult]

      doReturn(IO.pure(mockResponse)).when(mockConn).sendMessage(any[SendMessageRequest])
      sendCommand(zoneChangePending, mockConn).unsafeRunSync()

      verify(mockConn).sendMessage(any[SendMessageRequest])
    }
  }
}
