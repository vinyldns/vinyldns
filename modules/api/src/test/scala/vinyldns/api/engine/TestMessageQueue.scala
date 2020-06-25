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

package vinyldns.api.engine
import cats.data.NonEmptyList
import cats.effect.IO
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.ZoneCommand
import vinyldns.core.health.HealthCheck.HealthCheck
import vinyldns.core.queue.{CommandMessage, MessageCount, MessageQueue, SendBatchResult}
import vinyldns.core.health.HealthCheck._

import scala.concurrent.duration.FiniteDuration

object TestMessageQueue extends MessageQueue {
  override def receive(count: MessageCount): IO[List[CommandMessage]] = IO(List())

  override def requeue(message: CommandMessage): IO[Unit] = IO.unit

  override def remove(message: CommandMessage): IO[Unit] = IO.unit

  override def changeMessageTimeout(message: CommandMessage, duration: FiniteDuration): IO[Unit] =
    IO.unit

  override def sendBatch[A <: ZoneCommand](messages: NonEmptyList[A]): IO[SendBatchResult[A]] = {
    val partition = messages.toList.partition {
      case bad: RecordSetChange if bad.recordSet.name == "bad" => false
      case _ => true
    }
    IO {
      SendBatchResult(partition._1, partition._2.map((new RuntimeException("BOO"), _)))
    }
  }

  override def send[A <: ZoneCommand](command: A): IO[Unit] = IO.unit

  override def healthCheck(): HealthCheck = IO.unit.attempt.asHealthCheck(TestMessageQueue.getClass)
}
