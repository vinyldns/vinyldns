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
import cats.effect.IO
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.retry.PredefinedBackoffStrategies.ExponentialBackoffStrategy
import com.amazonaws.retry.RetryPolicy
import com.amazonaws.services.sqs.model._
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.amazonaws.{AmazonWebServiceRequest, AmazonWebServiceResult, ClientConfiguration}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory
import vinyldns.core.domain.{RecordSetChange, ZoneChange, ZoneCommand}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue._
import vinyldns.core.route.Monitor
import vinyldns.proto.VinylDNSProto

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

// Unique identifier corresponding to action of receiving the message, not the message itself. Only this info
// is required for actions like removal and changing message timeout in AWS SQS
final case class SqsMessageHandle(receiptHandle: String) extends MessageHandle

case class SqsMessageQueue(queueUrl: String, client: AmazonSQSAsync)
    extends MessageQueue
    with ProtobufConversions {

  import SqsMessageQueue._

  // Helper function for monitoring instrumentation
  private def monitored[A](name: String)(f: => IO[A]): IO[A] = {
    val monitor = Monitor(name)
    val startTime = System.currentTimeMillis

    def timeAndRecord: Boolean => Unit = monitor.capture(monitor.duration(startTime), _)

    def failed(): Unit = timeAndRecord(false)
    def succeeded(): Unit = timeAndRecord(true)

    f.attempt.flatMap {
      case Left(error) =>
        failed()
        IO.raiseError(error)
      case Right(ok) =>
        succeeded()
        IO.pure(ok)
    }
  }

  // Helper for handling SQS requests and responses
  private def sqsAsync[A <: AmazonWebServiceRequest, B <: AmazonWebServiceResult[_]](
      request: A,
      f: (A, AsyncHandler[A, B]) => java.util.concurrent.Future[B]): IO[B] =
    IO.async[B] { complete: (Either[Throwable, B] => Unit) =>
      val asyncHandler = new AsyncHandler[A, B] {
        def onError(exception: Exception): Unit = complete(Left(exception))

        def onSuccess(request: A, result: B): Unit = complete(Right(result))
      }

      f(request, asyncHandler)
    }

  def receive(count: MessageCount): IO[List[CommandMessage]] =
    monitored("sqs.receiveMessageBatch") {
      sqsAsync[ReceiveMessageRequest, ReceiveMessageResult](
        new ReceiveMessageRequest()
          .withMaxNumberOfMessages(count.value)
          .withMessageAttributeNames(".*")
          .withWaitTimeSeconds(1)
          .withQueueUrl(queueUrl),
        client.receiveMessageAsync
      ).map(_.getMessages.asScala.toList.map(m =>
        CommandMessage(SqsMessageHandle(m.getReceiptHandle), fromMessage(m))))
    }

  def remove(message: CommandMessage): IO[Unit] =
    monitored("sqs.removeMessage")(
      sqsAsync[DeleteMessageRequest, DeleteMessageResult](
        new DeleteMessageRequest(
          queueUrl,
          message.handle.asInstanceOf[SqsMessageHandle].receiptHandle),
        client.deleteMessageAsync)).map(_ => ())

  // AWS SQS has no explicit requeue mechanism; need to delete and re-add while specifying
  // message visibility. AWS natively applies an exponential back-off retry mechanism
  // (see: https://docs.aws.amazon.com/general/latest/gr/api-retries.html).
  def requeue(message: CommandMessage): IO[Unit] = IO.unit

  def send[A <: ZoneCommand](command: A): IO[Unit] =
    monitored("sqs.sendMessage")(
      sqsAsync[SendMessageRequest, SendMessageResult](
        toSendMessageRequest(command)
          .withQueueUrl(queueUrl),
        client.sendMessageAsync)).map(_ => ())

  def send[A <: ZoneCommand](cmds: NonEmptyList[A]): IO[SendBatchResult] =
    monitored("sqs.sendMessageBatch")(
      sqsAsync[SendMessageBatchRequest, SendMessageBatchResult](
        toSendMessageBatchRequest(cmds)
          .withQueueUrl(queueUrl),
        client.sendMessageBatchAsync))
      .map { batchResult =>
        val successfulIds = batchResult.getSuccessful.asScala.map(_.getId)
        val successes = cmds.toList.filter(cmd => successfulIds.contains(cmd.id))

        val failureIds = batchResult.getFailed.asScala.map(_.getId)
        val failureMessages = batchResult.getFailed.asScala.map(_.getMessage)
        val failures =
          cmds.toList.filter(cmd => failureIds.contains(cmd.id)).zip(failureMessages).map {
            case (cmd, msg) => (new Exception(msg), cmd)
          }
        SendBatchResult(successes, failures)
      }

  def changeMessageTimeout(message: CommandMessage, duration: FiniteDuration): IO[Unit] =
    monitored("sqs.changeMessageTimeout")(
      sqsAsync[ChangeMessageVisibilityRequest, ChangeMessageVisibilityResult](
        new ChangeMessageVisibilityRequest()
          .withReceiptHandle(message.handle.asInstanceOf[SqsMessageHandle].receiptHandle)
          .withVisibilityTimeout(duration.toSeconds.toInt)
          .withQueueUrl(queueUrl),
        client.changeMessageVisibilityAsync
      )).map(_ => ())
}

object SqsMessageQueue extends ProtobufConversions {
  private val logger = LoggerFactory.getLogger("vinyldns.sqs.queue.SqsMessageQueue")

  sealed abstract class SqsMessageType(val name: String)
  case object SqsRecordSetChangeMessage extends SqsMessageType("SqsRecordSetChangeMessage")
  case object SqsZoneChangeMessage extends SqsMessageType("SqsZoneChangeMessage")

  implicit class AmazonWebServiceRequestImprovements[A <: AmazonWebServiceRequest](
      baseMessage: SendMessageRequest) {
    def withMessageType(messageTypeName: String): SendMessageRequest =
      baseMessage
        .withMessageAttributes(
          Map(
            "message-type" -> new MessageAttributeValue()
              .withStringValue(messageTypeName)
              .withDataType("String")
          ).asJava
        )
  }

  implicit class SendMessageBatchRequestEntryImprovements(
      baseRequestEntry: SendMessageBatchRequestEntry) {
    def withMessageType(messageTypeName: String): SendMessageBatchRequestEntry =
      baseRequestEntry
        .withMessageAttributes(
          Map(
            "message-type" -> new MessageAttributeValue()
              .withStringValue(messageTypeName)
              .withDataType("String")
          ).asJava
        )
  }

  def apply(config: Config = ConfigFactory.load().getConfig("sqs")): SqsMessageQueue = {
    val accessKey = config.getString("access-key")
    val secretKey = config.getString("secret-key")
    val serviceEndpoint = config.getString("service-endpoint")
    val signingRegion = config.getString("signing-region")
    val queueUrl = config.getString("queue-url")

    val client =
      AmazonSQSAsyncClientBuilder
        .standard()
        .withClientConfiguration(new ClientConfiguration()
        .withRetryPolicy(new RetryPolicy(
          RetryPolicy.RetryCondition.NO_RETRY_CONDITION,
          new ExponentialBackoffStrategy(0, 64), // Base delay and max back-off delay of 64
          100, // Max error retry count (set to dead-letter count); default is 3
          true
        )))
        .withEndpointConfiguration(new EndpointConfiguration(serviceEndpoint, signingRegion))
        .withCredentials(
          new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
        .build()

    new SqsMessageQueue(queueUrl, client)
  }

  // Helper function to serialize message body
  def messageData[A <: ZoneCommand](cmd: A): String = cmd match {
    case rsc: RecordSetChange => Base64.getEncoder.encodeToString(toPB(rsc).toByteArray)
    case zc: ZoneChange => Base64.getEncoder.encodeToString(toPB(zc).toByteArray)
  }

  // Helper function to generate message type attribute
  def messageType[A <: ZoneCommand](cmd: A): String = cmd match {
    case _: RecordSetChange => SqsRecordSetChangeMessage.name
    case _: ZoneChange => SqsZoneChangeMessage.name
  }

  def parseMessageType(messageType: String): SqsMessageType = messageType match {
    case SqsRecordSetChangeMessage.name => SqsRecordSetChangeMessage
    case SqsZoneChangeMessage.name => SqsZoneChangeMessage
  }

  def toSendMessageRequest(zoneCommand: ZoneCommand): SendMessageRequest = {
    val messageTypeBytesTuple = zoneCommand match {
      case rsc: RecordSetChange => (SqsRecordSetChangeMessage.name, toPB(rsc).toByteArray)
      case zc: ZoneChange => (SqsZoneChangeMessage.name, toPB(zc).toByteArray)
    }

    messageTypeBytesTuple match {
      case (messageType, messageBytes) =>
        new SendMessageRequest()
          .withMessageBody(Base64.getEncoder.encodeToString(messageBytes))
          .withMessageType(messageType)
    }
  }

  def toSendMessageBatchRequest[A <: ZoneCommand](
      commands: NonEmptyList[A]): SendMessageBatchRequest = {
    // convert each message into an entry
    val entries = commands
      .map(cmd => (cmd, messageData(cmd)))
      .map {
        case (cmd, msgBody) =>
          new SendMessageBatchRequestEntry()
            .withMessageBody(msgBody)
            .withId(cmd.id)
            .withMessageType(messageType(cmd))
      }
      .toList
      .asJava

    new SendMessageBatchRequest()
      .withEntries(entries)
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
}
