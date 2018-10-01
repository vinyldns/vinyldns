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

import scala.concurrent.duration.FiniteDuration

// for ultimately acknowledging or re-queue of the message, this is queue implementation independent
trait MessageHandle
final case class CommandMessage(handle: MessageHandle, command: ZoneCommand)

sealed trait SendResult
final case class SendOk(zc: ZoneCommand) extends SendResult
final case class SendError(e: Throwable, zc: ZoneCommand) extends SendResult

sealed trait DeleteResult

// main consumer
trait MessageQueue {

  // receives up to maxMessageCount messages, the command should already be deserialized
  def receive(maxMessageCount: Int): IO[List[CommandMessage]]

  // puts the message back on the queue with the intention of having it re-processed again
  def requeue(message: CommandMessage): IO[Unit]

  // removes messages from the queue, indicating completion or the message should never be processed
  def remove(message: NonEmptyList[CommandMessage]): IO[Unit]

  // alters the visibility timeout for a message on the queue.
  def changeMessageTimeout(message: CommandMessage, duration: FiniteDuration): IO[Unit]

  // sends a batch of messages to the queue, the queue should serialize those and return List[IO[Message]]
  // where each IO could individually fail.  Note: retry semantics is not a requirement of the queue implementation
  def send(messages: NonEmptyList[ZoneCommand]): IO[NonEmptyList[Either[SendError, SendOk]]]

}
