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
import cats.effect.concurrent.Ref
import vinyldns.core.queue.CommandExecutionRepository._

import scala.concurrent.duration.FiniteDuration

class TestCommandExecutionRepository(now: () => Long = () => System.currentTimeMillis())
    extends CommandExecutionRepository {
  private case class State(digest: String, owner: Option[String], until: Long, completed: Boolean)
  private val states = Ref.of[IO, Map[String, State]](Map.empty).unsafeRunSync()

  def claim(identity: CommandIdentity, owner: String, lease: FiniteDuration): IO[Claim] =
    IO(now()).flatMap { time =>
      states.modify { current =>
        current.get(identity.key) match {
          case Some(state) if state.digest != identity.digest => (current, Conflict)
          case Some(state) if state.completed => (current, Completed)
          case Some(state) if state.owner.nonEmpty && state.until > time => (current, Busy)
          case _ =>
            (
              current.updated(
                identity.key,
                State(identity.digest, Some(owner), time + lease.toMillis, false)
              ),
              Acquired
            )
        }
      }
    }

  private def update(identity: CommandIdentity, owner: String)(f: State => State): IO[Unit] =
    IO(now()).flatMap { time =>
      states
        .modify { current =>
          current.get(identity.key) match {
            case Some(state)
                if state.owner.contains(owner) && !state.completed && state.until > time =>
              (current.updated(identity.key, f(state)), Right(()))
            case _ => (current, Left(LostLease(identity.commandId)))
          }
        }
        .flatMap(IO.fromEither)
    }

  def renew(identity: CommandIdentity, owner: String, lease: FiniteDuration): IO[Unit] =
    update(identity, owner)(_.copy(until = now() + lease.toMillis))

  def complete(identity: CommandIdentity, owner: String): IO[Unit] =
    update(identity, owner)(_.copy(owner = None, completed = true))

  def release(identity: CommandIdentity, owner: String): IO[Unit] =
    states.update { current =>
      current.get(identity.key) match {
        case Some(state) if state.owner.contains(owner) && !state.completed =>
          current.updated(identity.key, state.copy(owner = None, until = 0))
        case _ => current
      }
    }
}
