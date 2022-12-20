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
import org.slf4j.LoggerFactory
import vinyldns.core.route.Monitored
import java.io.{PrintWriter, StringWriter}

final case class AllNotifiers(notifiers: List[Notifier])(implicit val cs: ContextShift[IO])
    extends Monitored {

  private val logger = LoggerFactory.getLogger("AllNotifiers")

  def notify(notification: Notification[_]): IO[Unit] =
    for {
      _ <- notifiers.parTraverse(notify(_, notification))
    } yield ()

  def notify(notifier: Notifier, notification: Notification[_]): IO[Unit] =
    monitor(notifier.getClass.getSimpleName) {
      notifier.notify(notification).handleErrorWith { e =>
        val errorMessage = new StringWriter
        e.printStackTrace(new PrintWriter(errorMessage))
        IO {
          logger.error(s"Notifier ${notifier.getClass.getSimpleName} failed. Error: ${errorMessage.toString.replaceAll("\n",";").replaceAll("\t"," ")}")
        }
      }
    }
}
