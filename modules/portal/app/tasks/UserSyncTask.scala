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

object UserSyncTask {
  val SYNC_USER_TASK_NAME = "user_sync"
  private val log: Logger = LoggerFactory.getLogger("Task")

  def syncUsers(
      userAccountAccessor: UserAccountAccessor,
      authenticator: Authenticator): IO[Unit] = {
    log.info("Initiating user sync")
    for {
      allUsers <- userAccountAccessor.getAllUsers
      activeUsers = allUsers.filter(u => u.lockStatus != LockStatus.Locked && !u.isTest)
      nonActiveUsers <- authenticator.getUsersNotInLdap(activeUsers)
      lockedUsers <- userAccountAccessor.lockUsers(nonActiveUsers)
      _ <- IO(
        log.info(
          s"Users locked: ${lockedUsers.map(_.userName)}. Total locked: ${lockedUsers.size}"))
    } yield ()
  }
}
