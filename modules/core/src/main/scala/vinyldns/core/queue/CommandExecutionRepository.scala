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
import vinyldns.core.repository.Repository

import scala.concurrent.duration.FiniteDuration

trait CommandExecutionRepository extends Repository {
  import CommandExecutionRepository._

  def claim(identity: CommandIdentity, owner: String, lease: FiniteDuration): IO[Claim]
  def renew(identity: CommandIdentity, owner: String, lease: FiniteDuration): IO[Unit]
  def complete(identity: CommandIdentity, owner: String): IO[Unit]
  def release(identity: CommandIdentity, owner: String): IO[Unit]
}

object CommandExecutionRepository {
  sealed trait Claim
  case object Acquired extends Claim
  case object Completed extends Claim
  case object Busy extends Claim
  case object Conflict extends Claim

  final case class LostLease(commandId: String)
      extends RuntimeException(s"Lost execution lease for queue command $commandId")
}
