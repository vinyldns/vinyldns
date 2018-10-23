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
import java.util.concurrent.TimeUnit.SECONDS

import cats.data._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.retry.PredefinedBackoffStrategies.ExponentialBackoffStrategy
import com.amazonaws.retry.RetryPolicy
import com.amazonaws.services.sqs.model._
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.amazonaws.{AmazonWebServiceRequest, AmazonWebServiceResult, ClientConfiguration}
import org.slf4j.LoggerFactory
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.{ZoneChange, ZoneCommand}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue._
import vinyldns.core.route.Monitored
import vinyldns.sqs.queue.SqsMessageType.{SqsRecordSetChangeMessage, SqsZoneChangeMessage}

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

class SqsMessageQueue(val queueUrl: String, val client: AmazonSQSAsync)
    extends MessageQueue
    with Monitored {

  import SqsMessageQueue._
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  // Helper for handling SQS requests and responses
  def sqsAsync[A <: AmazonWebServiceRequest, B <: AmazonWebServiceResult[_]](
      request: A,
      f: (A, AsyncHandler[A, B]) => java.util.concurrent.Future[B]): IO[B] =
    IO.async[B] { complete: (Either[Throwable, B] => Unit) =>
      val asyncHandler = new AsyncHandler[A, B] {
        def onError(exception: Exception): Unit = complete(Left(exception))

        def onSuccess(request: A, result: B): Unit = complete(Right(result))
      }

      f(request, asyncHandler)
    }

  /**
    * Receiving messages could fail expectedly if a message on the queue is not well formed
    *
    * For each message pulled off, attempt to parse the message.  If that fails, log loudly and remove
    * it from the message queue
    */
  def receive(count: MessageCount): IO[List[SqsMessage]] =
    monitor("queue.SQS.receive") {
      sqsAsync[ReceiveMessageRequest, ReceiveMessageResult](
        // Can return 1-10 messages.
        // (see: https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/
        // com/amazonaws/services/sqs/model/ReceiveMessageRequest.html)
        new ReceiveMessageRequest()
          .withMaxNumberOfMessages(math.min(10, count.value))
          .withMessageAttributeNames(".*")
          .withWaitTimeSeconds(1)
          .withQueueUrl(queueUrl),
        client.receiveMessageAsync
      ).flatMap(receiveResult => parseBatch(receiveResult.getMessages.asScala.toList))
    }

  def parseBatch(messages: List[Message]): IO[List[SqsMessage]] =
    // attempt to parse each message that arrives, failures will be removed and not returned
    messages
      .map(parse)
      .parSequence
      .map { lst: List[Either[Throwable, SqsMessage]] =>
        lst.collect {
          case Right(message) => message
        }
      }

  // If we cannot parse the message, remove it from the queue
  def parse(message: Message): IO[Either[Throwable, SqsMessage]] =
    // This is tricky, we need to attempt to parse the message.  If we cannot, delete it; otherwise return ok
    IO(SqsMessage.parseSqsMessage(message)).flatMap {
      case Left(e) =>
        logger.error(s"Failed handling message with id '${message.getMessageId}'", e)
        delete(message.getReceiptHandle).as(Left(e))
      case Right(ok) => IO.pure(Right(ok))
    }

  def delete(receiptHandle: String): IO[Unit] =
    sqsAsync[DeleteMessageRequest, DeleteMessageResult](
      new DeleteMessageRequest(queueUrl, receiptHandle),
      client.deleteMessageAsync).as(())

  def remove(message: CommandMessage): IO[Unit] =
    monitor("queue.SQS.remove") {
      IO(delete(message.id.value))
    }.as(())

  /* Explicitly make a message almost immediately available on the queue */
  def requeue(message: CommandMessage): IO[Unit] =
    monitor("queue.SQS.requeue") {
      changeMessageTimeout(message, new FiniteDuration(10, SECONDS))
    }

  def send[A <: ZoneCommand](command: A): IO[Unit] =
    monitor("queue.SQS.send")(
      sqsAsync[SendMessageRequest, SendMessageResult](
        toSendMessageRequest(command)
          .withQueueUrl(queueUrl),
        client.sendMessageAsync)).as(())

  def sendBatch[A <: ZoneCommand](cmds: NonEmptyList[A]): IO[SendBatchResult] =
    monitor("queue.SQS.sendBatch") {
      toSendMessageBatchRequest(cmds)
        .map { sendRequest =>
          sqsAsync[SendMessageBatchRequest, SendMessageBatchResult](
            sendRequest
              .withQueueUrl(queueUrl),
            client.sendMessageBatchAsync)
        }
        .parSequence
        .map { sendResult =>
          toSendBatchResult(sendResult, cmds)
        }
    }

  /* Change message visibility timeout. Valid values: 0 to 43200 seconds (ie. 12 hours) */
  def changeMessageTimeout(message: CommandMessage, duration: FiniteDuration): IO[Unit] =
    monitor("queue.SQS.changeMessageTimeout") {
      IO.fromEither(validateMessageTimeout(duration)).flatMap { validDuration =>
        sqsAsync[ChangeMessageVisibilityRequest, ChangeMessageVisibilityResult](
          new ChangeMessageVisibilityRequest()
            .withReceiptHandle(message.id.value)
            .withVisibilityTimeout(validDuration.toSeconds.toInt)
            .withQueueUrl(queueUrl),
          client.changeMessageVisibilityAsync
        )
      }
    }.as(())
}

object SqsMessageQueue extends ProtobufConversions {
  private val logger = LoggerFactory.getLogger("vinyldns.sqs.queue.SqsMessageQueue")

  sealed abstract class SqsMessageQueueError(message: String) extends Throwable(message)
  final case class InvalidMessageTimeout(duration: Long)
      extends SqsMessageQueueError(
        s"Invalid duration: $duration seconds. Duration must be between " +
          s"$MINIMUM_VISIBILITY_TIMEOUT-$MAXIMUM_VISIBILITY_TIMEOUT seconds.")

  // AWS limits as specified at:
  // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-limits.html
  // $COVERAGE-OFF$
  final val MINIMUM_VISIBILITY_TIMEOUT = 0
  final val MAXIMUM_VISIBILITY_TIMEOUT = 43200
  final val MAXIMUM_BATCH_SIZE = 262144
  // $COVERAGE-ON$

  def apply(
      sqsMessageQueueSettings: SqsMessageQueueSettings,
      queueName: String): SqsMessageQueue = {
    val client =
      AmazonSQSAsyncClientBuilder
        .standard()
        .withClientConfiguration(
          new ClientConfiguration()
            .withRetryPolicy(new RetryPolicy(
              RetryPolicy.RetryCondition.NO_RETRY_CONDITION,
              new ExponentialBackoffStrategy(2, 64), // Base delay and max back-off delay of 64
              100, // Max error retry count (set to dead-letter count); default is 3
              true
            )))
        .withEndpointConfiguration(new EndpointConfiguration(
          sqsMessageQueueSettings.serviceEndpoint,
          sqsMessageQueueSettings.signingRegion))
        .withCredentials(
          new AWSStaticCredentialsProvider(
            new BasicAWSCredentials(
              sqsMessageQueueSettings.accessKey,
              sqsMessageQueueSettings.secretKey)))
        .build()

    // Create queue if it doesn't exist
    val queueUrl = try {
      client.getQueueUrl(queueName).getQueueUrl
    } catch {
      case _: QueueDoesNotExistException => client.createQueue(queueName).getQueueUrl
    }

    new SqsMessageQueue(queueUrl, client)
  }

  def validateMessageTimeout(
      duration: FiniteDuration): Either[InvalidMessageTimeout, FiniteDuration] =
    duration.toSeconds match {
      case valid if valid >= MINIMUM_VISIBILITY_TIMEOUT && valid <= MAXIMUM_VISIBILITY_TIMEOUT =>
        Right(duration)
      case invalid => Left(InvalidMessageTimeout(invalid))
    }

  // Helper function to serialize message body
  def messageData[A <: ZoneCommand](cmd: A): String = cmd match {
    case rsc: RecordSetChange => Base64.getEncoder.encodeToString(toPB(rsc).toByteArray)
    case zc: ZoneChange => Base64.getEncoder.encodeToString(toPB(zc).toByteArray)
  }

  def toSendMessageRequest(zoneCommand: ZoneCommand): SendMessageRequest = {
    val messageTypeBytesTuple = zoneCommand match {
      case rsc: RecordSetChange => (SqsRecordSetChangeMessage, toPB(rsc).toByteArray)
      case zc: ZoneChange => (SqsZoneChangeMessage, toPB(zc).toByteArray)
    }

    messageTypeBytesTuple match {
      case (messageType, messageBytes) =>
        new SendMessageRequest()
          .withMessageBody(Base64.getEncoder.encodeToString(messageBytes))
          .withMessageAttributes(Map(messageType.messageAttribute).asJava)
    }
  }

  def toSendMessageBatchRequest[A <: ZoneCommand](
      commands: NonEmptyList[A]): List[SendMessageBatchRequest] = {
    // convert each message into an entry
    val entries = commands.map { cmd =>
      new SendMessageBatchRequestEntry()
        .withMessageBody(messageData(cmd))
        .withId(cmd.id)
        .withMessageAttributes(Map(SqsMessageType.fromCommand(cmd).messageAttribute).asJava)
    }.toList

    // Group entries into batches
    val maxMessageSize = entries.map(_.getMessageBody.getBytes().length).max
    entries
      .grouped((MAXIMUM_BATCH_SIZE.toDouble / maxMessageSize.toDouble).toInt)
      .map { groupedEntries =>
        new SendMessageBatchRequest().withEntries(groupedEntries.asJava)
      }
      .toList
  }

  def toSendBatchResult[A <: ZoneCommand](
      sendResultList: List[SendMessageBatchResult],
      cmds: NonEmptyList[A]): SendBatchResult = {
    val successfulIds = sendResultList.flatMap(_.getSuccessful.asScala.map(_.getId))
    val successes = cmds.filter(cmd => successfulIds.contains(cmd.id))

    val failed = sendResultList
      .flatMap(_.getFailed.asScala.map { f =>
        (f.getId, f.getMessage)
      })
      .toMap

    val failures = cmds
      .map(cmd => (failed.get(cmd.id), cmd))
      .collect {
        case (Some(msg), cmd) => (new Exception(msg), cmd)
      }

    SendBatchResult(successes, failures)
  }
}
