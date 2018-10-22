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

package vinyldns.mysql.repository

import org.scalatest._
import scalikejdbc.DB
import vinyldns.core.domain.membership.{LockStatus, User, UserRepository}

class MySqlUserRepositoryIntegrationSpec
  extends WordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with Inspectors
    with OptionValues {

  private var repo: UserRepository = _

  private val testUserIds = (for {i <- 0 to 100} yield s"test-user-$i").toList.sorted
  private val users = testUserIds.map { id =>
    User(id = id, userName = "name" + id, accessKey = s"abc$id", secretKey = "123")
  }

  override protected def beforeAll(): Unit = {
    repo = TestMySqlInstance.userRepository

    DB.localTx { s =>
      s.executeUpdate("DELETE FROM user")
    }

    for (user <- users) {
      repo.save(user).unsafeRunSync()
    }
  }

  override protected def afterAll(): Unit = {
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM user")
    }
    super.afterAll()
  }

  "MySqlUserRepository.save" should {
    "return user after save" in {
      repo.save(users.head).unsafeRunSync() shouldBe users.head
    }

    "save super user with super status" in {
      val superUser = User("superName", "superAccess", "superSecret", isSuper = true)
      repo.save(superUser).unsafeRunSync() shouldBe superUser
      val result = repo.getUser(superUser.id).unsafeRunSync()
      result shouldBe Some(superUser)
      result.get.isSuper shouldBe true
    }

    "save non-super user with non-super status" in {
      val nonSuperUser = User("nonSuperName", "nonSuperAccess", "nonSuperSecret")
      repo.save(nonSuperUser).unsafeRunSync() shouldBe nonSuperUser
      val result = repo.getUser(nonSuperUser.id).unsafeRunSync()
      result shouldBe Some(nonSuperUser)
      result.get.isSuper shouldBe false
    }

    "save locked user with locked status" in {
      val lockedUser = User("lockedName", "lockedAccess", "lockedSecret", lockStatus = LockStatus.Locked)
      repo.save(lockedUser).unsafeRunSync() shouldBe lockedUser
      val result = repo.getUser(lockedUser.id).unsafeRunSync()
      result shouldBe Some(lockedUser)
      result.get.lockStatus shouldBe LockStatus.Locked
    }

    "save unlocked user with unlocked status" in {
      val unlockedUser = User("unlockedName", "unlockedAccess", "unlockedSecret")
      repo.save(unlockedUser).unsafeRunSync() shouldBe unlockedUser
      val result = repo.getUser(unlockedUser.id).unsafeRunSync()
      result shouldBe Some(unlockedUser)
      result.get.lockStatus shouldBe LockStatus.Unlocked
    }
  }

  "MySqlUserRepository.getUser" should {
    "retrieve a user" in {
      repo.getUser(users.head.id).unsafeRunSync() shouldBe Some(users.head)
    }

    "returns None when user does not exist" in {
      repo.getUser("no-existo").unsafeRunSync() shouldBe None
    }
  }

  "MySqlUserRepository.getUserByAccessKey" should {
    "retrieve a user" in {
      repo.getUserByAccessKey(users.head.accessKey).unsafeRunSync() shouldBe Some(users.head)
    }

    "returns None when user does not exist" in {
      repo.getUserByAccessKey("no-existo").unsafeRunSync() shouldBe None
    }
  }

  "MySqlUserRepository.getUserByName" should {
    "retrieve a user" in {
      repo.getUserByName(users.head.userName).unsafeRunSync() shouldBe Some(users.head)
    }

    "returns None when user does not exist" in {
      repo.getUserByName("no-existo").unsafeRunSync() shouldBe None
    }
  }

  "MySqlUserRepository.getUsers" should {
    "omits all non existing users" in {
      val result = repo.getUsers(Set("no-existo", users.head.id), None, None).unsafeRunSync()
      result.users shouldBe List(users.head)
    }

    "returns all users" in {
      val result = repo.getUsers(testUserIds.toSet, None, None).unsafeRunSync()
      result.users should contain theSameElementsAs users
    }

    "returns empty list when given no ids" in {
      val result = repo.getUsers(Set[String](), None, None).unsafeRunSync()
      result.users should contain theSameElementsAs List()
    }
  }
}
