package vinyldns.sqs.queue
import java.util.Base64

import cats.implicits._
import com.amazonaws.services.sqs.model.Message
import vinyldns.core.domain.ZoneCommand
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue.CommandMessage
import vinyldns.proto.VinylDNSProto
import vinyldns.sqs.queue.SqsMessageType.{SqsRecordSetChangeMessage, SqsZoneChangeMessage}

final case class SqsMessage(receiptHandle: String, command: ZoneCommand) extends CommandMessage
object SqsMessage extends ProtobufConversions {
  sealed abstract class SqsMessageError(message: String) extends Exception(message)
  final case class InvalidSqsMessageContents(messageId: String)
    extends SqsMessageError(s"Unable to parse SQS Message with id '$messageId'")
  final case class EmptySqsMessageContents(messageId: String)
    extends SqsMessageError(s"No message body found for SQS message with id '$messageId'")

  final case class InvalidMessageType(className: String)
    extends Exception(s"Invalid command message type $className")

  def cast(msg: CommandMessage): Either[InvalidMessageType, SqsMessage] = msg match {
    case ok: SqsMessage => Right(ok)
    case invalid => Left(InvalidMessageType(invalid.getClass.getName))
  }

  def parseSqsMessage(message: Message): Either[Exception, SqsMessage] = {
    for {
      messageType <- SqsMessageType.fromMessage(message)
      messageBytes <- Option(message.getBody)
        .map(Right(_)).getOrElse(Left(EmptySqsMessageContents(message.getMessageId)))
      contents <- Either.catchNonFatal(Base64.getDecoder.decode(messageBytes))
      cmd <- Either.catchNonFatal {
        messageType match {
          case SqsRecordSetChangeMessage =>
            fromPB(VinylDNSProto.RecordSetChange.parseFrom(contents))
          case SqsZoneChangeMessage => fromPB(VinylDNSProto.ZoneChange.parseFrom(contents))
        }
      }
    } yield SqsMessage(message.getReceiptHandle, cmd)
  }
}
