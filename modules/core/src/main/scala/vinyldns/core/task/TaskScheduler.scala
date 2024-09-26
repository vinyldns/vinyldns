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
import vinyldns.core.route.Monitored
import java.io.{PrintWriter, StringWriter}
import scala.concurrent.duration.FiniteDuration

// Interface for all Tasks that need to be run
trait Task {
  // The name of the task, should be unique / constant and should not change
  def name: String

  // The amount of time this task is running before it can be reclaimed / considered failed
  def timeout: FiniteDuration

  // How often the task should actually be executed
  def runEvery: FiniteDuration

  // How often to check the store to see if the task should be run
  // this should be a short duration, a few seconds
  def checkInterval: FiniteDuration

  // Runs the task
  def run(): IO[Unit]
}

object TaskScheduler extends Monitored {
  private val logger = LoggerFactory.getLogger("TaskScheduler")

  /**
    * Schedules a task to be run.  Will insert the task into the TaskRepository if it is not
    * already present.  The result is a Stream that the caller has to manage the lifecycle for.
    *
    * @example
    *          val scheduledStream = TaskScheduler.schedule(...)
    *          val handle = scheduledStream.compile.drain.start.unsafeRunSync()
    *          ...
    *          // once everything is done you can cancel it via the handle
    *          handle.cancel.unsafeRunSync()
    * @return a Stream that when run will awake on the interval defined on the Task provided, and
    *         run the task.run()
    */
  def schedule(
      task: Task,
      taskRepository: TaskRepository
  )(implicit t: Timer[IO], cs: ContextShift[IO]): Stream[IO, Unit] = {

    def claimTask(): IO[Option[Task]] =
      taskRepository.claimTask(task.name, task.timeout, task.runEvery).map {
        case true =>
          logger.debug(s"""Successfully found and claimed task; taskName="${task.name}" """)
          Some(task)
        case false =>
          logger.debug(s"""No task claimed; taskName="${task.name}" """)
          None
      }

    // Note: IO.suspend is needed due to bug in cats effect 1.0.0 #421
    def releaseTask(maybeTask: Option[Task]): IO[Unit] = IO.suspend {
      maybeTask
        .map(
          t =>
            taskRepository
              .releaseTask(t.name)
              .as(logger.debug(s"""Released task; taskName="${task.name}" """))
        )
        .getOrElse(IO.unit)
    }

    def runTask(maybeTask: Option[Task]): IO[Unit] = maybeTask.map(_.run()).getOrElse(IO.unit)

    // Acquires a task, runs it, and makes sure it is cleaned up, swallows the error via a log
    def runOnceSafely(task: Task): IO[Unit] =
      monitor(s"task.${task.name}") {
        claimTask().bracket(runTask)(releaseTask).handleError { error =>
          val errorMessage = new StringWriter
          error.printStackTrace(new PrintWriter(errorMessage))
          logger.error(s"""Unexpected error running task; taskName="${task.name}". Error: ${errorMessage.toString.replaceAll("\n",";").replaceAll("\t"," ")} """)
        }
      }

    // We must first schedule the task in the repository and then create our stream
    // After it exists, we run it once when we first start the stream
    // Semantics of repo.scheduleTask are idempotent, if the task already exists we are ok
    // Then we run our scheduled task with fixedDelay, because we want a constant delay in between
    // tasks (for example if the task runs every 1 hour, and the task itself takes 20 seconds,
    // the delay until the next run will 1 hour after the task finished)...awakeEvery uses fixedRate which differs
    // because it tries its best to run exactly every 1 hour in the previous example, regardless of how long
    // the task took to execute
    Stream
      .eval(taskRepository.saveTask(task.name).flatMap(_ => runOnceSafely(task)))
      .flatMap { _ =>
        Stream
          .fixedDelay[IO](task.checkInterval)
          .evalMap(_ => runOnceSafely(task))
      }
  }
}
