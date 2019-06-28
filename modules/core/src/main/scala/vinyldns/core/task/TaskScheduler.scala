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

package vinyldns.core.task
import cats.effect._
import cats.implicits._
import fs2._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

// Interface for all Tasks that need to be run
trait Task {
  // The name of the task, should be unique / constant and should not change
  def name: String

  // The amount of time this task is running before it can be reclaimed / considered failed
  def timeout: FiniteDuration

  // How often to attempt to run the task
  def runEvery: FiniteDuration

  // Runs the task
  def run(): IO[Unit]
}

object TaskScheduler {
  private val logger = LoggerFactory.getLogger("TaskScheduler")

  // Starts a task on a schedule, returns a Fiber that can be used to cancel the task
  def schedule(task: Task, taskRepository: TaskRepository)(
      implicit t: Timer[IO],
      cs: ContextShift[IO]): Stream[IO, Unit] = {

    def claimTask(): IO[Option[Task]] = IO.suspend {
      taskRepository.claimTask(task.name, task.timeout).map {
        case true =>
          logger.info(s"""Successfully found and claimed task; taskName="${task.name}" """)
          Some(task)
        case false =>
          logger.info(s"""No task claimed; taskName="${task.name}" """)
          None
      }
    }

    def releaseTask(maybeTask: Option[Task]): IO[Unit] = IO.suspend {
      maybeTask
        .map(
          t =>
            taskRepository
              .releaseTask(t.name)
              .as(logger.info(s"""Released task; taskName="${task.name}" """)))
        .getOrElse(IO.unit)
    }

    def runTask(maybeTask: Option[Task]): IO[Unit] = maybeTask.map(_.run()).getOrElse(IO.unit)

    // Acquires a task, runs it, and makes sure it is cleaned up, swallows the error via a log
    def runOnceSafely(task: Task): IO[Unit] =
      claimTask().bracket(runTask)(releaseTask).handleError { error =>
        logger.error(s"""Unexpected error is task; taskName="${task.name}" """, error)
      }

    // Awake on the interval provided
    Stream.awakeEvery[IO](task.runEvery).evalMap(_ => runOnceSafely(task))
  }
}
