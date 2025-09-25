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

import cats.effect.IO
import controllers.{Authenticator, UserAccountAccessor}
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.domain.membership.LockStatus
import vinyldns.core.task.Task

import scala.concurrent.duration._

class UserSyncTask(
    userAccountAccessor: UserAccountAccessor,
    authenticator: Authenticator,
    val runEvery: FiniteDuration = 24.hours,
    val timeout: FiniteDuration = 24.hours,
    val checkInterval: FiniteDuration = 1.minute
) extends Task {
  val name: String = "user_sync"
  private val logger: Logger = LoggerFactory.getLogger("UserSyncTask")

  def run(): IO[Unit] = {
    logger.info("Initiating user sync")
    for {
      allUsers <- userAccountAccessor.getAllUsers
      activeUsers = allUsers.filter(u => u.lockStatus != LockStatus.Locked && !u.isTest)
      nonActiveUsers <- authenticator.getUsersNotInLdap(activeUsers)
      lockedUsers <- userAccountAccessor.lockUsers(nonActiveUsers)
      _ <- IO(logger.info(s"""usersLocked="${lockedUsers
        .map(_.userName)}"; userLockCount="${lockedUsers.size}" """))
    } yield ()
  }
}
