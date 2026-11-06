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

import java.util.UUID

import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.duration._

final class CommandExecutionGuard(
    repository: CommandExecutionRepository,
    lease: FiniteDuration = 1.minute
)(implicit timer: Timer[IO], cs: ContextShift[IO]) {
  import CommandExecutionRepository._
  import CommandExecutionGuard._

  require(lease >= 3.seconds, "Queue execution lease must be at least three seconds")

  def execute[A](identity: CommandIdentity)(action: IO[A])(
      terminal: A => Boolean
  ): IO[Result[A]] =
    IO(UUID.randomUUID().toString).flatMap { owner =>
      repository.claim(identity, owner, lease).flatMap {
        case Completed => IO.pure(AlreadyCompleted)
        case Busy => IO.pure(InProgress)
        case Conflict => IO.pure(IdentityConflict)
        case Acquired =>
          def heartbeat: IO[Unit] =
            timer.sleep(lease / 3) *> repository.renew(identity, owner, lease) *> IO
              .suspend(heartbeat)

          val work = action.flatMap { result =>
            val persist =
              if (terminal(result)) repository.complete(identity, owner)
              else repository.release(identity, owner)
            persist.as[Result[A]](Executed(result))
          }
          // Persist completion before acknowledging the transport; receipt handles are not replay IDs.
          IO.race(heartbeat, work)
            .flatMap {
              case Right(result) => IO.pure(result)
              case Left(_) => IO.raiseError[Result[A]](LostLease(identity.commandId))
            }
            .guarantee(repository.release(identity, owner))
      }
    }
}

object CommandExecutionGuard {
  sealed trait Result[+A]
  final case class Executed[A](value: A) extends Result[A]
  case object AlreadyCompleted extends Result[Nothing]
  case object InProgress extends Result[Nothing]
  case object IdentityConflict extends Result[Nothing]
}
