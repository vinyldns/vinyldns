package vinyldns.sqs.queue
import com.amazonaws.services.sqs.model.{Message, MessageAttributeValue}
import vinyldns.core.domain.{RecordSetChange, ZoneChange, ZoneCommand}

sealed abstract class SqsMessageType(val name: String) {
  val messageAttribute: (String, MessageAttributeValue) =
    "message-type" -> new MessageAttributeValue()
      .withStringValue(name)
      .withDataType("String")
}
object SqsMessageType {
  case object SqsRecordSetChangeMessage extends SqsMessageType("SqsRecordSetChangeMessage")
  case object SqsZoneChangeMessage extends SqsMessageType("SqsZoneChangeMessage")
  sealed abstract class SqsMessageTypeError(message: String) extends Exception(message)

  final case class InvalidMessageTypeValue(value: String)
    extends SqsMessageTypeError(s"Invalid message type value on sqs message '$value'")
  final case object MessageTypeNotFound
    extends SqsMessageTypeError(s"Unable to find message-type attribute on SQS message")

  def fromCommand[A <: ZoneCommand](cmd: A): SqsMessageType = cmd match {
    case _: RecordSetChange => SqsRecordSetChangeMessage
    case _: ZoneChange => SqsZoneChangeMessage
  }

  def fromString(messageType: String): Either[InvalidMessageTypeValue, SqsMessageType] = messageType match {
    case SqsRecordSetChangeMessage.name => Right(SqsRecordSetChangeMessage)
    case SqsZoneChangeMessage.name => Right(SqsZoneChangeMessage)
    case invalid => Left(InvalidMessageTypeValue(invalid))
  }

  def fromMessage(sqsMessage: Message): Either[SqsMessageTypeError, SqsMessageType] = {
    // getMessageAttributes guarantees a map, but it could be empty
    // the message-type maybe present, but doesn't have a string value
    // the message-type could have a string value, but not a valid value
    for {
      messageTypeAttr <- Option(sqsMessage.getMessageAttributes.get("message-type"))
        .flatMap(attr => Option(attr.getStringValue))
        .map(Right(_)).getOrElse(Left(MessageTypeNotFound))
      messageType <- fromString(messageTypeAttr)
    } yield messageType
  }
}
