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

package tasks

import cats.effect.{IO, Timer}
import cats.implicits._
import controllers.{Authenticator, Settings, UserAccountAccessor}
import fs2.Stream
import javax.inject.{Inject, Singleton}
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.task._

import scala.concurrent.duration.FiniteDuration

final case class NoTaskToClaim(taskName: String) extends Throwable {
  def message: String = s"No available task [$taskName] to claim."
}

@Singleton
class TaskHandler @Inject()(taskRepository: TaskRepository) {
  private val logger: Logger = LoggerFactory.getLogger("TaskHandler")
  private implicit val t: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)

  def fetchAndClaimTask(taskName: String, pollingInterval: FiniteDuration): IO[Unit] =
    taskRepository.claimTask(taskName, pollingInterval).flatMap {
      case true =>
        IO(logger.info(s"Successfully found and claimed task [$taskName]."))
      case false =>
        val claimError = NoTaskToClaim(taskName)
        IO.raiseError(claimError)
    }

  def runSyncTask(
      taskName: String,
      pollingInterval: FiniteDuration,
      userAccountAccessor: UserAccountAccessor,
      authenticator: Authenticator): IO[Unit] = {
    val syncRun = for {
      _ <- fetchAndClaimTask(taskName, pollingInterval)
      _ <- IO(logger.info(s"Fetched and claimed task [$taskName]."))
      _ <- UserSyncTask.syncUsers(userAccountAccessor, authenticator).handleError(_ => ())
      _ <- taskRepository.releaseTask(taskName)
      _ <- IO(logger.info(s"Released task [$taskName]."))
    } yield ()

    syncRun.handleErrorWith { err =>
      IO(logger.info(s"Encountered task error for [$taskName]", err))
    }
  }

  def startTaskStream(
      settings: Settings,
      userAccountAccessor: UserAccountAccessor,
      authenticator: Authenticator): Stream[IO, Unit] = {
    import UserSyncTask._
    import settings._

    Stream
      .awakeEvery[IO](ldapSyncPollingInterval)
      .evalMap[IO, Unit](
        _ =>
          runSyncTask(
            SYNC_USER_TASK_NAME,
            ldapSyncPollingInterval,
            userAccountAccessor,
            authenticator))
  }

  def run(
      settings: Settings,
      userAccountAccessor: UserAccountAccessor,
      authenticator: Authenticator): IO[Unit] =
    startTaskStream(settings, userAccountAccessor, authenticator).compile.drain
}
