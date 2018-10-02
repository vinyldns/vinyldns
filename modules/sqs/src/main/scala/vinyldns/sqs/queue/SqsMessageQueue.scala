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

import cats.data.NonEmptyList
import cats.effect.IO
import com.amazonaws.services.sqs.model._
import vinyldns.core.domain.zone.ZoneCommand
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue._

import scala.concurrent.duration.FiniteDuration
import scala.collection.JavaConverters._
import vinyldns.sqs.queue.SqsConverters._

final case class ReceiptHandle(value: String) extends MessageHandle

class SqsMessageQueue(liveSqsConnection: LiveSqsConnection)
    extends MessageQueue
    with ProtobufConversions {
  import liveSqsConnection._

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
        CommandMessage(ReceiptHandle(m.getReceiptHandle), fromMessage(m))))
    }

  def remove(message: CommandMessage): IO[Unit] =
    monitored("sqs.removeMessage")(
      sqsAsync[DeleteMessageRequest, DeleteMessageResult](
        new DeleteMessageRequest(queueUrl, message.handle.asInstanceOf[ReceiptHandle].value),
        client.deleteMessageAsync)).map(_ => ())

  // AWS SQS has no explicit requeue mechanism; use changeMessageTimeout instead
  def requeue(message: CommandMessage): IO[Unit] = IO.unit

  def send[A <: ZoneCommand](command: A): IO[Unit] =
    monitored("sqs.sendMessage")(
      sqsAsync[SendMessageRequest, SendMessageResult](
        toSendMessageRequest(command)
          .withQueueUrl(queueUrl),
        client.sendMessageAsync)).map(_ => ())

  def send[A <: ZoneCommand](messages: NonEmptyList[A]): IO[SendBatchResult] =
    IO.pure(SendBatchResult(List(), List()))
  /*
  def send[A <: ZoneCommand](messages: NonEmptyList[A]): IO[SendBatchResult] =
    monitored("sqs.sendMessageBatch")(
      sqsAsync[SendMessageBatchRequest, SendMessageBatchResult](
        toSendMessageRequest(messages)
          .withQueueUrl(queueUrl),
        client.sendMessageBatchAsync))
      .map{
      batchResult =>
        val successes = batchResult.getSuccessful.asScala.map(fromMessage).toList
        val failures = batchResult.getFailed.asScala.map(fromMessage).toList
        SendBatchResult(successes, failures)
      }
   */

  def changeMessageTimeout(message: CommandMessage, duration: FiniteDuration): IO[Unit] =
    monitored("sqs.changeMessageTimeout")(
      sqsAsync[ChangeMessageVisibilityRequest, ChangeMessageVisibilityResult](
        new ChangeMessageVisibilityRequest()
          .withReceiptHandle(message.handle.asInstanceOf[ReceiptHandle].value)
          .withVisibilityTimeout(1600) // 1800 seconds == 30 minutes
          .withQueueUrl(queueUrl),
        client.changeMessageVisibilityAsync
      )).map(_ => ())
}
