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

import cats.effect.{ContextShift, IO}
import controllers.{Authenticator, Settings, UserAccountAccessor}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import vinyldns.core.domain.membership.{LockStatus, User}
import vinyldns.core.task.TaskRepository

import scala.concurrent.duration._

class UserSyncTaskHandlerSpec extends Specification with Mockito {
  implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.Implicits.global)

  private val notAuthUser: User = User("not-authorized", "accessKey", "secretKey")
  private val lockedNotAuthUser: User = notAuthUser.copy(lockStatus = LockStatus.Locked)
  private val goodTaskName = "good_task"
  private val badTaskName = "bad_task"

  val mockAuthenticator: Authenticator = {
    val mockObject = mock[Authenticator]
    mockObject.getUsersNotInLdap(List(notAuthUser)).returns(IO(List(notAuthUser)))
    mockObject
  }

  val mockUserAccountAccessor: UserAccountAccessor = {
    val mockObject = mock[UserAccountAccessor]
    mockObject.getAllUsers.returns(IO(List(notAuthUser)))
    mockObject
      .lockUsers(List(notAuthUser))
      .returns(IO(List(lockedNotAuthUser)))
    mockObject
  }

  val mockSettings: Settings = {
    val mockObject = mock[Settings]
    mockObject.ldapSyncPollingInterval.returns(50.millis)
    mockObject
  }

  val mockTaskRepository: TaskRepository = {
    val mockObject = mock[TaskRepository]
    mockObject.claimTask(goodTaskName, mockSettings.ldapSyncPollingInterval).returns(IO.pure(true))
    mockObject
      .claimTask(badTaskName, mockSettings.ldapSyncPollingInterval)
      .returns(IO.pure(false))
    mockObject
      .releaseTask(goodTaskName)
      .returns(IO.unit)
    mockObject
  }
  val taskHandler = new TaskHandler(mockTaskRepository)

  "fetchAndClaimTask" should {
    "fetch and claim the task if it is unclaimed" in {
      taskHandler
        .fetchAndClaimTask(goodTaskName, mockSettings.ldapSyncPollingInterval)
        .unsafeRunSync() must beEqualTo(())
    }
    "return a NoTaskToClaim error is there are issues" in {
      taskHandler
        .fetchAndClaimTask(badTaskName, mockSettings.ldapSyncPollingInterval)
        .unsafeRunSync() must throwA[NoTaskToClaim]
    }
  }

  "doTask" should {
    "process entire flow if fetchAndClaimTask succeeds" in {
      taskHandler
        .runSyncTask(
          goodTaskName,
          mockSettings.ldapSyncPollingInterval,
          mockUserAccountAccessor,
          mockAuthenticator)
        .unsafeRunSync() must beEqualTo(())
    }
    "stop process flow if task cannot be claimed" in {
      taskHandler
        .runSyncTask(
          badTaskName,
          mockSettings.ldapSyncPollingInterval,
          mockUserAccountAccessor,
          mockAuthenticator)
        .unsafeRunSync() must throwA[NoTaskToClaim]
    }
  }
}
