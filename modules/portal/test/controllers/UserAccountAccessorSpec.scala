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

package controllers

import cats.effect.IO
import java.time.temporal.ChronoUnit
import java.time.Instant
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeEach
import vinyldns.core.domain.membership._

class UserAccountAccessorSpec extends Specification with Mockito with BeforeEach {

  private val user = User(
    "fbaggins",
    "key",
    "secret",
    Some("Frodo"),
    Some("Baggins"),
    Some("fbaggins@hobbitmail.me"),
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    "frodo-uuid"
  )

  private val userLog = UserChange(
    "frodo-uuid",
    user,
    "fbaggins",
    Instant.now.truncatedTo(ChronoUnit.MILLIS),
    None,
    UserChangeType.Create
  ).toOption.get

  private val mockRepo = mock[UserRepository]
  private val mockChangeRepo = mock[UserChangeRepository]
  private val underTest = new UserAccountAccessor(mockRepo, mockChangeRepo)

  protected def before: Any =
    org.mockito.Mockito.reset(mockRepo, mockChangeRepo)

  "UserAccountAccessor" should {
    "return the user when storing a user that does not exist already" in {
      mockRepo.save(any[User]).returns(IO.pure(user))
      mockChangeRepo.save(any[UserChange]).returns(IO.pure(userLog))
      underTest.create(user).unsafeRunSync() must beEqualTo(user)
      there.was(one(mockChangeRepo).save(any[UserChange]))
    }

    "return the new user when storing a user that already exists in the store" in {
      val newUser = user.copy(accessKey = "new-key", secretKey = "new-secret")
      mockRepo.save(any[User]).returns(IO.pure(newUser))
      mockChangeRepo.save(any[UserChange]).returns(IO.pure(userLog))
      underTest.update(newUser, user).unsafeRunSync() must beEqualTo(newUser)
      there.was(one(mockChangeRepo).save(any[UserChange]))
    }

    "return the user when retrieving a user that exists by name" in {
      mockRepo.getUserByName(user.userName).returns(IO.pure(Some(user)))
      mockRepo.getUser(user.userName).returns(IO.pure(None))
      mockRepo.getUserByIdOrName(user.userName).returns(IO.pure(Some(user)))
      underTest.get("fbaggins").unsafeRunSync() must beSome(user)
    }

    "return the user when retrieving a user that exists by user ID" in {
      mockRepo.getUserByName(user.id).returns(IO.pure(None))
      mockRepo.getUser(user.id).returns(IO.pure(Some(user)))
      mockRepo.getUserByIdOrName(user.userName).returns(IO.pure(Some(user)))
      underTest.get(user.id).unsafeRunSync() must beSome(user)
    }

    "return None when the user to be retrieved does not exist" in {
      mockRepo.getUserByName(any[String]).returns(IO.pure(None))
      mockRepo.getUser(any[String]).returns(IO.pure(None))
      mockRepo.getUserByIdOrName(any[String]).returns(IO.pure(None))
      underTest.get("fbaggins").unsafeRunSync() must beNone
    }

    "return the user by access key" in {
      mockRepo.getUserByAccessKey(user.id).returns(IO.pure(Some(user)))
      underTest.getUserByKey(user.id).unsafeRunSync() must beSome(user)
    }

    "return all users" in {
      val userList = List(user, user.copy(id = "user2", userName = "user2"))
      mockRepo.getAllUsers.returns(IO.pure(userList))
      underTest.getAllUsers.unsafeRunSync() must beEqualTo(userList)
    }

    "lock specified users" in {
      val lockedUser = user.copy(lockStatus = LockStatus.Locked)
      val lockedUserChange = UserChange.UpdateUser(
        user.copy(lockStatus = LockStatus.Locked),
        "system",
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        user
      )
      mockRepo.save(List(lockedUser)).returns(IO(List(lockedUser)))
      mockChangeRepo.save(any[UserChange]).returns(IO(lockedUserChange))
      underTest.lockUsers(List(user)).unsafeRunSync() must beEqualTo(List(lockedUser))
    }
  }
}
