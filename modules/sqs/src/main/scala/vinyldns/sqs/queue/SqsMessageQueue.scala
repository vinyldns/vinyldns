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

import cats.data._
import cats.effect.IO
import cats.implicits._
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
import vinyldns.core.route.Monitored
import vinyldns.sqs.queue.SqsMessageType.{SqsRecordSetChangeMessage, SqsZoneChangeMessage}

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

class SqsMessageQueue(val queueUrl: String, val client: AmazonSQSAsync)
    extends MessageQueue
    with Monitored {

  import SqsMessageQueue._

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
    monitor("queue.SQS.receiveMessageBatch") {
      sqsAsync[ReceiveMessageRequest, ReceiveMessageResult](
        new ReceiveMessageRequest()
          .withMaxNumberOfMessages(count.value)
          .withMessageAttributeNames(".*")
          .withWaitTimeSeconds(1)
          .withQueueUrl(queueUrl),
        client.receiveMessageAsync
      ).flatMap(batchResult => parseBatch(batchResult.getMessages.asScala.toList))
    }

  def parseBatch(messages: List[Message]): IO[List[SqsMessage]] =
    // attempt to parse each message that arrives, failures will be removed and not returned
    messages
      .map(parse)
      .sequence
      .map { lst: List[Either[Throwable, SqsMessage]] =>
        lst.collect {
          case Right(message) => message
        }
      }

  // If we cannot parse the message, remove it from the queue
  def parse(message: Message): IO[Either[Throwable, SqsMessage]] =
    // This is tricky, we need to attempt to parse the message.  If we cannot, delete it; otherwise return ok
    IO.fromEither(SqsMessage.parseSqsMessage(message)).attempt.flatMap {
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
    monitor("queue.SQS.removeMessage") {
      IO.fromEither(SqsMessage.cast(message))
        .flatMap { sqsMsg =>
          delete(sqsMsg.receiptHandle)
        }
    }.as(())

  // AWS SQS has no explicit requeue mechanism; need to delete and re-add while specifying
  // message visibility. AWS natively applies an exponential back-off retry mechanism
  // (see: https://docs.aws.amazon.com/general/latest/gr/api-retries.html).
  def requeue(message: CommandMessage): IO[Unit] = IO.unit

  def send[A <: ZoneCommand](command: A): IO[Unit] =
    monitor("queue.SQS.sendMessage")(
      sqsAsync[SendMessageRequest, SendMessageResult](
        toSendMessageRequest(command)
          .withQueueUrl(queueUrl),
        client.sendMessageAsync)).as(())

  def sendBatch[A <: ZoneCommand](cmds: NonEmptyList[A]): IO[SendBatchResult] =
    monitor("sqs.sendMessageBatch")(
      sqsAsync[SendMessageBatchRequest, SendMessageBatchResult](
        toSendMessageBatchRequest(cmds)
          .withQueueUrl(queueUrl),
        client.sendMessageBatchAsync))
      .map { batchResult =>
        toSendBatchResult(batchResult, cmds)
      }

  def changeMessageTimeout(message: CommandMessage, duration: FiniteDuration): IO[Unit] =
    monitor("sqs.changeMessageTimeout") {
      IO.fromEither(SqsMessage.cast(message))
        .flatMap { sqsMsg =>
          sqsAsync[ChangeMessageVisibilityRequest, ChangeMessageVisibilityResult](
            new ChangeMessageVisibilityRequest()
              .withReceiptHandle(sqsMsg.receiptHandle)
              .withVisibilityTimeout(duration.toSeconds.toInt)
              .withQueueUrl(queueUrl),
            client.changeMessageVisibilityAsync
          )
        }
        .as(())
    }
}

object SqsMessageQueue extends ProtobufConversions {
  private val logger = LoggerFactory.getLogger("vinyldns.sqs.queue.SqsMessageQueue")

  def apply(config: Config = ConfigFactory.load().getConfig("sqs")): SqsMessageQueue = {
    val accessKey = config.getString("access-key")
    val secretKey = config.getString("secret-key")
    val serviceEndpoint = config.getString("service-endpoint")
    val signingRegion = config.getString("signing-region")
    val queueUrl = config.getString("queue-url")

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
        .withEndpointConfiguration(new EndpointConfiguration(serviceEndpoint, signingRegion))
        .withCredentials(new AWSStaticCredentialsProvider(
          new BasicAWSCredentials(accessKey, secretKey)))
        .build()

    new SqsMessageQueue(queueUrl, client)
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
      commands: NonEmptyList[A]): SendMessageBatchRequest = {
    // convert each message into an entry
    val entries = commands
      .map(cmd => (cmd, messageData(cmd)))
      .map {
        case (cmd, msgBody) =>
          new SendMessageBatchRequestEntry()
            .withMessageBody(msgBody)
            .withId(cmd.id)
            .withMessageAttributes(Map(SqsMessageType.fromCommand(cmd).messageAttribute).asJava)
      }
      .toList
      .asJava

    new SendMessageBatchRequest()
      .withEntries(entries)
  }

  def toSendBatchResult[A <: ZoneCommand](
      batchResult: SendMessageBatchResult,
      cmds: NonEmptyList[A]): SendBatchResult = {
    val successfulIds = batchResult.getSuccessful.asScala.map(_.getId)
    val successes = cmds.toList.filter(cmd => successfulIds.contains(cmd.id))

    val failed = batchResult.getFailed.asScala.toList
    val failureIds = failed.map(_.getId)
    val failureMessages = failed.map(_.getMessage)
    val failures =
      cmds.toList.filter(cmd => failureIds.contains(cmd.id)).zip(failureMessages).map {
        case (cmd, msg) => (new Exception(msg), cmd)
      }
    SendBatchResult(successes, failures)
  }
}
