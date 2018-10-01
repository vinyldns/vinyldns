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
import cats.effect.IO
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.{ZoneChange, ZoneCommand}

import scala.concurrent.duration.FiniteDuration

// for ultimately acknowledging or re-queue of the message, this is queue implementation independent
trait MessageHandle
sealed abstract class CommandMessage[A <: ZoneCommand](val handle: MessageHandle, val command: A) {}
final case class RecordChangeMessage(h: MessageHandle, c: RecordSetChange) extends CommandMessage(h, c)
final case class ZoneChangeMessage(h: MessageHandle, c: ZoneChange) extends CommandMessage(h, c)

// main consumer
trait MessageQueue {

  // receives a single message, the command should already be deserialized
  def receive(): IO[CommandMessage[_]]

  // receives a batch of messages
  def receiveBatch(): IO[List[CommandMessage[_]]]

  // puts the message back on the queue with the intention of having it re-processed again
  def requeue(message: CommandMessage[_]): IO[Unit]

  // removes a message from the queue, indicating completion or the message should never be processed
  def remove(message: CommandMessage[_]): IO[Unit]

  // alters the visibility timeout for a message on the queue.
  def changeMessageTimeout(message: CommandMessage[_], duration: FiniteDuration): IO[Unit]

  // sends a zone command to the queue, the queue will need to serialize it (if necessary) first
  def send[A <: ZoneCommand](command: A): IO[Unit]

  // sends a batch of messages to the queue, the queue should serialize those and return List[IO[Message]]
  // where each IO could individually fail.  Note: retry semantics is not a requirement of the queue implementation
  def sendBatch(messages: List[ZoneCommand]): IO[Unit]
}
