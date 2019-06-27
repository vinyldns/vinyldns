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

import cats.data.EitherT
import cats.effect.{IO, Resource, Timer}
import cats.implicits._
import controllers.{Authenticator, Settings, UserAccountAccessor}
import javax.inject.{Inject, Singleton}
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.task._

import scala.concurrent.duration.FiniteDuration

final case class NoTaskToClaim(taskName: String) extends Throwable {
  def message: String = s"No available task [$taskName] to claim."
}

trait Task {
  def name: String
  def run(): IO[Unit]
}

@Singleton
class TaskHandler @Inject()(taskRepository: TaskRepository) {
  private val logger: Logger = LoggerFactory.getLogger("TaskHandler")
  private implicit val t: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)

  def claimTask(task: Task, pollingInterval: FiniteDuration): IO[Either[NoTaskToClaim, Unit]] =
    taskRepository.claimTask(task.name, pollingInterval).flatMap {
      case true =>
        IO(logger.info(s"Successfully found and claimed task [${task.name}].").asRight)
      case false =>
        IO(Left(NoTaskToClaim(task.name)))
    }

  def runSyncTask(
      task: Task,
      pollingInterval: FiniteDuration,
      userAccountAccessor: UserAccountAccessor,
      authenticator: Authenticator): IO[Unit] = {

    // TODO: We have hard coded this flow to the sync task, so the task name cannot be anything, it must also
    // be hard-coded.  Imagine if someone passed in a different task name!  We are combining a generic process
    // with a specific hard-coded version here.  Probably shouldn't have stringly typed taskName
    val res = Resource.make { claimTask(task, pollingInterval) } {
      case Right(_) =>
        taskRepository
          .releaseTask(task.name)
          .as(logger.info(s"Released task [${task.name}]"))
      case Left(NoTaskToClaim(_)) => IO.unit
    }



    val y = Resource.liftF(UserSyncTask.syncUsers(userAccountAccessor, authenticator))
    val syncRun = for {
      _ <- EitherT.liftF(claimTask(taskName, pollingInterval))
      _ <- EitherT.right(IO(logger.info(s"Fetched and claimed task [$taskName].")))
      _ <- UserSyncTask
        .syncUsers(userAccountAccessor, authenticator)
        .handleErrorWith(e => IO(logger.error("Encountered error syncing task", e)))
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
