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

  // Runs the task
  def run(): IO[Unit]
}

object TaskScheduler {
  private val logger = LoggerFactory.getLogger("TaskScheduler")

  // Starts a task on a schedule, returns a Fiber that can be used to cancel the task
  def schedule(task: Task, runEvery: FiniteDuration, taskRepository: TaskRepository)(
    implicit t: Timer[IO],
    cs: ContextShift[IO]): IO[Fiber[IO, Unit]] = {

    def claimTask(): IO[Option[Task]] =
      taskRepository.claimTask(task.name, runEvery).map {
        case true =>
          logger.info(s"""Successfully found and claimed task; taskName="${task.name}" """)
          Some(task)
        case false =>
          logger.info(s"""No task claimed; taskName="${task.name}" """)
          None
      }

    def releaseTask(maybeTask: Option[Task]): IO[Unit] =
      maybeTask
        .map(
          t =>
            taskRepository
              .releaseTask(t.name)
              .as(logger.info(s"""Released task; taskName="${task.name}" """)))
        .getOrElse(IO.unit)

    def scheduledTaskStream(): Stream[IO, Unit] =
    // Awake on the interval provided
      Stream
        .awakeEvery[IO](runEvery)
        .flatMap { _ =>
          // We bracket to make sure that no matter what happens, we ALWAYS release the task
          Stream.bracket[IO, Option[Task]](claimTask())(releaseTask).evalMap { maybeTask =>
            // If the Option[Task] is Some, then we will run the task provided; otherwise we do nothing
            maybeTask.map(_.run()).getOrElse(IO.unit)
          }
        }
        .handleErrorWith { error =>
          // Make sure that we restart our stream on failure
          logger.error(s"""Unexpected error is task; taskName="${task.name}" """, error)
          scheduledTaskStream()
        }

    // Return the Fiber so the caller can eventually cancel it
    scheduledTaskStream().compile.drain.start
  }
}
