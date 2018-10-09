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
import com.amazonaws.services.sqs.model._
import org.slf4j.LoggerFactory
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.{ZoneChange, ZoneCommand}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.proto.VinylDNSProto

import scala.collection.JavaConverters._
import scala.util.Try

object SqsConverters extends ProtobufConversions {

  private val logger = LoggerFactory.getLogger("vinyldns.api.sqs.SqsConverters")

  sealed trait SqsMessageType {
    def name: String
  }

  case object SqsRecordSetChangeMessage extends SqsMessageType {
    val name = "SqsRecordSetChangeMessage"
  }

  case object SqsZoneChangeMessage extends SqsMessageType {
    val name = "SqsZoneChangeMessage"
  }

  def parseMessageType(messageType: String): SqsMessageType = messageType match {
    case SqsRecordSetChangeMessage.name => SqsRecordSetChangeMessage
    case SqsZoneChangeMessage.name => SqsZoneChangeMessage
  }

  def toSendMessageRequest(cmd: ZoneCommand): SendMessageRequest = cmd match {
    case rsc: RecordSetChange => toSendMessageRequest(rsc)
    case zc: ZoneChange => toSendMessageRequest(zc)
  }

  def toSendMessageRequest(recordSetChange: RecordSetChange): SendMessageRequest = {
    val bytes = toPB(recordSetChange).toByteArray
    val messageBody = Base64.getEncoder.encodeToString(bytes)
    new SendMessageRequest()
      .withMessageBody(messageBody)
      .withMessageAttributes(
        Map(
          "message-type" -> new MessageAttributeValue()
            .withStringValue(SqsRecordSetChangeMessage.name)
            .withDataType("String")
        ).asJava)
  }

  def toSendMessageRequest(zoneChange: ZoneChange): SendMessageRequest = {
    val bytes = toPB(zoneChange).toByteArray
    val messageBody = Base64.getEncoder.encodeToString(bytes)
    new SendMessageRequest()
      .withMessageBody(messageBody)
      .withMessageAttributes(
        Map(
          "message-type" -> new MessageAttributeValue()
            .withStringValue(SqsZoneChangeMessage.name)
            .withDataType("String")
        ).asJava)
  }

  def fromMessage(message: Message): ZoneCommand = {
    logger.info(
      s"Received message with attributes ${message.getMessageAttributes.asScala}, ${message.getAttributes.asScala}")

    val messageType = message.getMessageAttributes.asScala("message-type").getStringValue

    val messageBytes = Base64.getDecoder.decode(message.getBody)
    parseMessageType(messageType) match {
      case SqsRecordSetChangeMessage =>
        fromPB(VinylDNSProto.RecordSetChange.parseFrom(messageBytes))
      case SqsZoneChangeMessage => fromPB(VinylDNSProto.ZoneChange.parseFrom(messageBytes))
    }
  }

  def toRecordSetChange(message: Message): Try[RecordSetChange] = {
    logger.info(
      s"Received message with attributes ${message.getMessageAttributes.asScala}, ${message.getAttributes.asScala}")
    Try {
      val messageBytes = Base64.getDecoder.decode(message.getBody)
      fromPB(VinylDNSProto.RecordSetChange.parseFrom(messageBytes))
    }
  }

  def sendCommand(cmd: ZoneCommand, sqsConnection: SqsConnection): IO[ZoneCommand] =
    sqsConnection.sendMessage(toSendMessageRequest(cmd)).map(_ => cmd)
}
