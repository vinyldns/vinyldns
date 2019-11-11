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

package vinyldns.core.queue
import cats.data.NonEmptyList
import cats.effect.IO
import vinyldns.core.domain.zone.ZoneCommand
import vinyldns.core.health.HealthCheck.HealthCheck

import scala.concurrent.duration.FiniteDuration

// $COVERAGE-OFF$
/* An identifier for a message, used typically to requeue and remove the message */
final case class MessageId(value: String)
trait CommandMessage {
  def command: ZoneCommand
  def id: MessageId
}

// need to encode the possibility of one or more commands failing to send
final case class SendBatchResult[A <: ZoneCommand](
    successes: List[A],
    failures: List[(Exception, A)]
)

// Using types here to ensure we cannot pass in a negative or 0 count
final case class MessageCount private (value: Int)
object MessageCount {
  final case class NonPositiveMessageCountError(cnt: Int) extends Throwable
  def apply(cnt: Int): Either[NonPositiveMessageCountError, MessageCount] =
    if (cnt <= 0) Left(NonPositiveMessageCountError(cnt))
    else Right(new MessageCount(cnt))
}

// main message queue to be implemented
trait MessageQueue {

  // receives a batch of messages.  In SQS, we require number of messages, message attributes, and timeout.
  // the latter of those are likely not applicable for all message queues, but a count certainly is
  def receive(count: MessageCount): IO[List[CommandMessage]]

  // puts the message back on the queue with the intention of having it re-processed again
  def requeue(message: CommandMessage): IO[Unit]

  // removes a message from the queue, indicating completion or the message should never be processed
  def remove(message: CommandMessage): IO[Unit]

  // updates the amount of time this message will remain invisible for until it can be retried
  def changeMessageTimeout(message: CommandMessage, duration: FiniteDuration): IO[Unit]

  // we need to track which messages failed and report that back to the caller
  def sendBatch[A <: ZoneCommand](messages: NonEmptyList[A]): IO[SendBatchResult[A]]

  // sends a single message, exceptions will be raised via IO
  def send[A <: ZoneCommand](command: A): IO[Unit]

  def healthCheck(): HealthCheck

}
// $COVERAGE-ON$
