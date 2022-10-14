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
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import vinyldns.core.domain.Encrypted
import vinyldns.core.domain.membership._

class UserSyncTaskSpec extends Specification with Mockito {
  val notAuthUser: User = User("not-authorized", "accessKey", Encrypted("secretKey"))
  val lockedNotAuthUser: User = notAuthUser.copy(lockStatus = LockStatus.Locked)
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

  "SyncUserTask" should {
    "successfully lock unauthorized, non-test users" in {
      new UserSyncTask(mockUserAccountAccessor, mockAuthenticator)
        .run()
        .unsafeRunSync() must beEqualTo(())

      there.was(one(mockUserAccountAccessor).lockUsers(List(notAuthUser)))
    }

    "successfully process if no users are found" in {
      val mockAuth: Authenticator = mock[Authenticator]
      mockAuth.getUsersNotInLdap(List(notAuthUser)).returns(IO(Nil))

      val mockUsers = mock[UserAccountAccessor]
      mockUsers
        .lockUsers(Nil)
        .returns(IO(Nil))

      mockUsers.getAllUsers.returns(IO(List(notAuthUser)))

      new UserSyncTask(mockUsers, mockAuth)
        .run()
        .unsafeRunSync() must beEqualTo(())
    }
  }
}
