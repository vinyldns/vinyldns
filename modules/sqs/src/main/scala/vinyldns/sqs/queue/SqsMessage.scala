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
import java.nio.charset.StandardCharsets.UTF_8

import cats.implicits._
import com.amazonaws.services.sqs.model.Message
import vinyldns.core.domain.batch.BatchChangeCommand
import vinyldns.core.domain.zone.ZoneCommand
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue.{CommandIdentity, CommandMessage, MessageId, QueueMessageSigning}
import vinyldns.proto.VinylDNSProto
import vinyldns.sqs.queue.SqsMessageType.{
  SqsBatchChangeMessage,
  SqsRecordSetChangeMessage,
  SqsZoneChangeMessage
}

final case class SqsMessage(
    id: MessageId,
    command: ZoneCommand,
    override val authenticatedIdentity: Option[CommandIdentity] = None
) extends CommandMessage
object SqsMessage extends ProtobufConversions {
  sealed abstract class SqsMessageError(message: String) extends Throwable(message)
  final case class InvalidSqsMessageContents(messageId: String)
      extends SqsMessageError(s"Unable to parse SQS Message with ID '$messageId'")
  final case class EmptySqsMessageContents(messageId: String)
      extends SqsMessageError(s"No message body found for SQS message with ID '$messageId'")
  final case class InvalidMessageType(className: String)
      extends SqsMessageError(s"Invalid command message type '$className'")

  def parseSqsMessage(
      message: Message,
      signing: QueueMessageSigning
  ): Either[Throwable, SqsMessage] =
    for {
      messageType <- SqsMessageType.fromMessage(message)
      messageBytes <- Either.fromOption(
        Option(message.getBody),
        EmptySqsMessageContents(message.getMessageId)
      )
      contents <- Either.catchNonFatal(Base64.getDecoder.decode(messageBytes))
      verified <- signing.verify(messageType.code, contents)
      cmd <- Either
        .catchNonFatal {
          messageType match {
            case SqsRecordSetChangeMessage =>
              fromPB(VinylDNSProto.RecordSetChange.parseFrom(verified.bytes))
            case SqsZoneChangeMessage => fromPB(VinylDNSProto.ZoneChange.parseFrom(verified.bytes))
            case SqsBatchChangeMessage =>
              BatchChangeCommand(new String(verified.bytes, UTF_8))
          }
        }
      _ <- Either.cond(
        cmd.id == verified.identity.commandId,
        (),
        QueueMessageSigning.InvalidSignature("Payload ID does not match signed command ID")
      )
    } yield SqsMessage(MessageId(message.getReceiptHandle), cmd, Some(verified.identity))
}
