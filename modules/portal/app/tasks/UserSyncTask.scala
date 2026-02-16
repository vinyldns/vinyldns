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
import controllers.{UserAccountAccessor, UserSyncProvider}
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.domain.membership.LockStatus
import vinyldns.core.task.Task

import scala.concurrent.duration._

class UserSyncTask(
    userAccountAccessor: UserAccountAccessor,
    syncProvider: UserSyncProvider,
    val runEvery: FiniteDuration = 24.hours,
    val timeout: FiniteDuration = 24.hours,
    val checkInterval: FiniteDuration = 1.minute
) extends Task {
  val name: String = "user_sync"
  private val logger: Logger = LoggerFactory.getLogger("UserSyncTask")

  // TODO: Remove dryRun flag after testing
  val dryRun: Boolean = true

  def run(): IO[Unit] = {
    logger.error("Initiating user sync" + (if (dryRun) " (DRY RUN)" else ""))
    for {
      allUsers <- userAccountAccessor.getAllUsers
      activeUsers = allUsers.filter(u => u.lockStatus != LockStatus.Locked && !u.isTest)
      _ <- IO(logger.error(s"""activeUsersCount="${activeUsers.size}"; users="${activeUsers.map(_.userName)}""""))
      staleUsers <- syncProvider.getStaleUsers(activeUsers)
      _ <- IO(logger.error(s"""staleUsersCount="${staleUsers.size}"; staleUsers="${staleUsers.map(_.userName)}""""))
      _ <- IO {
        val activeSet = activeUsers.map(_.userName).toSet
        val staleSet = staleUsers.map(_.userName).toSet
        val okUsers = activeSet -- staleSet
        logger.error(s"""activeInDirectoryCount="${okUsers.size}"; activeInDirectory="${okUsers}"""")
      }
      lockedUsers <- if (dryRun) {
        IO(logger.error(s"""DRY RUN - skipping lock for: ${staleUsers.map(_.userName)}""")).as(List.empty)
      } else {
        userAccountAccessor.lockUsers(staleUsers)
      }
      _ <- IO(logger.error(s"""usersLocked="${lockedUsers
        .map(_.userName)}"; userLockCount="${lockedUsers.size}" """))
    } yield ()
  }
}
