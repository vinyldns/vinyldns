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
import org.slf4j.LoggerFactory
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.{ZoneChange, ZoneCommand}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.proto.VinylDNSProto

import scala.collection.JavaConverters._
import scala.util.Try

object SqsConverters extends ProtobufConversions {

  private val logger = LoggerFactory.getLogger("vinyldns.sqs.queue.SqsConverters")

  sealed abstract class SqsMessageType(val name: String)
  case object SqsRecordSetChangeMessage extends SqsMessageType("SqsRecordSetChangeMessage")
  case object SqsZoneChangeMessage extends SqsMessageType("SqsZoneChangeMessage")

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

  def toSendMessageRequest[A <: ZoneCommand](
      messages: NonEmptyList[A],
      idLookup: List[(String, A)]): SendMessageBatchRequest = {
    val messageTypeBytesTuple = messages.map {
      case recordSetChange: RecordSetChange =>
        (SqsRecordSetChangeMessage.name, toPB(recordSetChange).toByteArray)
      case zoneChange: ZoneChange => (SqsZoneChangeMessage.name, toPB(zoneChange).toByteArray)
    }

    val messageBatchRequestEntryList = messageTypeBytesTuple.zipWithIndex
      .map {
        case ((messageTypeName, messageBytes), index) =>
          new SendMessageBatchRequestEntry()
            .withMessageBody(Base64.getEncoder.encodeToString(messageBytes))
            .withId(idLookup(index)._1)
            .withMessageAttributes(
              Map(
                "message-type" -> new MessageAttributeValue()
                  .withStringValue(messageTypeName)
                  .withDataType("String")
              ).asJava)
      }
      .toList
      .asJava

    new SendMessageBatchRequest()
      .withEntries(messageBatchRequestEntryList)
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

  def fromMessage[A <: ZoneCommand](
      sendMessageBatchResultEntry: SendMessageBatchResultEntry,
      idLookup: Map[String, A]): ZoneCommand = {
    logger.info(
      s"Received message with attributes ${sendMessageBatchResultEntry.getMD5OfMessageAttributes}")

    val zoneCommand = idLookup.getOrElse(sendMessageBatchResultEntry.getId, None)
    zoneCommand match {
      case recordSetChange: RecordSetChange => recordSetChange
      case zoneChange: ZoneChange => zoneChange
      case None => throw new Exception("Could not find send batch zone command")
    }
  }

  def fromMessage[A <: ZoneCommand](
      batchResultErrorEntry: BatchResultErrorEntry,
      idLookup: Map[String, A]): (Exception, ZoneCommand) = {
    logger.info(
      s"Received message with attributes ${batchResultErrorEntry.getMessage}, " +
        s"${batchResultErrorEntry.getCode}")

    val messageBytes = Base64.getDecoder.decode(batchResultErrorEntry.getMessage)
    val zoneCommand = idLookup.getOrElse(batchResultErrorEntry.getId, None)
    zoneCommand match {
      case recordSetChange: RecordSetChange =>
        (new Exception(Base64.getDecoder.decode(messageBytes).toString), recordSetChange)
      case zoneChange: ZoneChange =>
        (new Exception(Base64.getDecoder.decode(messageBytes).toString), zoneChange)
      case None => throw new Exception("Could not find send batch error zone command")
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
}
