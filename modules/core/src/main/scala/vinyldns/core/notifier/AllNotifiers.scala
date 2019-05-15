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

package vinyldns.core.notifier

import cats.effect.{ContextShift, IO}
import cats.implicits._
import scala.concurrent.ExecutionContext
import org.slf4j.LoggerFactory

final case class AllNotifiers(notifiers: List[Notifier]) extends Notifier {

  private val logger = LoggerFactory.getLogger("AllNotifiers")
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  def notify(notification: Notification[_]): IO[Unit] =
    for {
      _ <- notifiers.parTraverse(notify(_, notification))
    } yield ()

  def notify(notifier: Notifier, notification: Notification[_]): IO[Unit] =
    notifier.notify(notification).handleErrorWith { e =>
      IO { logger.error(s"Notifier ${notifier.getClass.getSimpleName} failed.", e) }
    }
}
