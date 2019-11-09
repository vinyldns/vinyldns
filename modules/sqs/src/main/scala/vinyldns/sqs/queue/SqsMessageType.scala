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
import cats.implicits._
import com.amazonaws.services.sqs.model.{Message, MessageAttributeValue}
import vinyldns.core.domain.batch.BatchChangeCommand
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.{ZoneChange, ZoneCommand}

sealed abstract class SqsMessageType(val name: String) {
  val messageAttribute: (String, MessageAttributeValue) =
    "message-type" -> new MessageAttributeValue()
      .withStringValue(name)
      .withDataType("String")
}

object SqsMessageType {
  case object SqsRecordSetChangeMessage extends SqsMessageType("SqsRecordSetChangeMessage")
  case object SqsZoneChangeMessage extends SqsMessageType("SqsZoneChangeMessage")
  case object SqsBatchChangeMessage extends SqsMessageType("SqsBatchChangeMessage")
  sealed abstract class SqsMessageTypeError(message: String) extends Throwable(message)

  final case class InvalidMessageTypeValue(value: String)
      extends SqsMessageTypeError(s"Invalid message-type value on sqs message '$value'")
  final case object MessageTypeNotFound
      extends SqsMessageTypeError(s"Unable to find message-type attribute on SQS message")

  def fromCommand[A <: ZoneCommand](cmd: A): SqsMessageType = cmd match {
    case _: RecordSetChange => SqsRecordSetChangeMessage
    case _: ZoneChange => SqsZoneChangeMessage
    case _: BatchChangeCommand => SqsBatchChangeMessage
  }

  def fromString(messageType: String): Either[InvalidMessageTypeValue, SqsMessageType] =
    messageType match {
      case SqsRecordSetChangeMessage.name => Right(SqsRecordSetChangeMessage)
      case SqsZoneChangeMessage.name => Right(SqsZoneChangeMessage)
      case SqsBatchChangeMessage.name => Right(SqsBatchChangeMessage)
      case invalid => Left(InvalidMessageTypeValue(invalid))
    }

  def fromMessage(sqsMessage: Message): Either[Throwable, SqsMessageType] =
    // getMessageAttributes guarantees a map, but it could be empty
    // the message-type maybe present, but doesn't have a string value
    // the message-type could have a string value, but not a valid value
    for {
      messageTypeAttr <- Either.fromOption(
        Option(sqsMessage.getMessageAttributes.get("message-type"))
          .flatMap(mt => Option(mt.getStringValue)),
        MessageTypeNotFound
      )
      messageType <- fromString(messageTypeAttr)
    } yield messageType
}
