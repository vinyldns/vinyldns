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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.DB
import vinyldns.core.domain.Encrypted
import vinyldns.core.domain.membership.{LockStatus, User, UserRepository}
import vinyldns.mysql.TestMySqlInstance

class MySqlUserRepositoryIntegrationSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with Inspectors
    with OptionValues {

  private var repo: UserRepository = _

  private val testUserIds = (for { i <- 0 to 100 } yield s"test-user-$i").toList.sorted
  private val users = testUserIds.map { id =>
    User(id = id, userName = "name" + id, accessKey = s"abc$id", secretKey = Encrypted("123"))
  }

  private val caseInsensitiveUser1 =
    User(id = "caseInsensitiveUser1", userName = "Name1", accessKey = "a1", secretKey = Encrypted("s1"))
  private val caseInsensitiveUser2 =
    User(id = "caseInsensitiveUser2", userName = "namE2", accessKey = "a2", secretKey = Encrypted("s2"))
  private val caseInsensitiveUser3 =
    User(id = "caseInsensitiveUser3", userName = "name3", accessKey = "a3", secretKey = Encrypted("s3"))

  override protected def beforeAll(): Unit = {
    repo = TestMySqlInstance.userRepository

    DB.localTx { s =>
      s.executeUpdate("DELETE FROM user")
    }

    for (user <- users) {
      repo.save(user).unsafeRunSync()
    }

    repo.save(caseInsensitiveUser1).unsafeRunSync()
    repo.save(caseInsensitiveUser2).unsafeRunSync()
    repo.save(caseInsensitiveUser3).unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    DB.localTx { s =>
      s.executeUpdate("DELETE FROM user")
    }
    super.afterAll()
  }

  "MySqlUserRepository.getAllUsers" should {
    "return all users" in {
      repo.getAllUsers.unsafeRunSync() should contain theSameElementsAs
        users ++ List(caseInsensitiveUser1, caseInsensitiveUser2, caseInsensitiveUser3)
    }
  }

  "MySqlUserRepository.save" should {
    "return user after save" in {
      repo.save(users.head).unsafeRunSync() shouldBe users.head
    }

    "save super user with super status" in {
      val superUser = User("superName", "superAccess", Encrypted("superSecret"), isSuper = true)
      repo.save(superUser).unsafeRunSync() shouldBe superUser
      val result = repo.getUser(superUser.id).unsafeRunSync()
      result shouldBe Some(superUser)
      result.get.isSuper shouldBe true
    }

    "save non-super user with non-super status" in {
      val nonSuperUser = User("nonSuperName", "nonSuperAccess", Encrypted("nonSuperSecret"))
      repo.save(nonSuperUser).unsafeRunSync() shouldBe nonSuperUser
      val result = repo.getUser(nonSuperUser.id).unsafeRunSync()
      result shouldBe Some(nonSuperUser)
      result.get.isSuper shouldBe false
    }

    "save locked user with locked status" in {
      val lockedUser =
        User("lockedName", "lockedAccess", Encrypted("lockedSecret"), lockStatus = LockStatus.Locked)
      repo.save(lockedUser).unsafeRunSync() shouldBe lockedUser
      val result = repo.getUser(lockedUser.id).unsafeRunSync()
      result shouldBe Some(lockedUser)
      result.get.lockStatus shouldBe LockStatus.Locked
    }

    "save unlocked user with unlocked status" in {
      val unlockedUser = User("unlockedName", "unlockedAccess", Encrypted("unlockedSecret"))
      repo.save(unlockedUser).unsafeRunSync() shouldBe unlockedUser
      val result = repo.getUser(unlockedUser.id).unsafeRunSync()
      result shouldBe Some(unlockedUser)
      result.get.lockStatus shouldBe LockStatus.Unlocked
    }

    "save support user with support status" in {
      val supportUser = User("lockedName", "lockedAccess", Encrypted("lockedSecret"), isSupport = true)
      repo.save(supportUser).unsafeRunSync() shouldBe supportUser
      val result = repo.getUser(supportUser.id).unsafeRunSync()
      result shouldBe Some(supportUser)
      result.get.isSupport shouldBe true
    }

    "save non-support user with non-support status" in {
      val nonSupportUser = User("unlockedName", "unlockedAccess", Encrypted("unlockedSecret"))
      repo.save(nonSupportUser).unsafeRunSync() shouldBe nonSupportUser
      val result = repo.getUser(nonSupportUser.id).unsafeRunSync()
      result shouldBe Some(nonSupportUser)
      result.get.isSupport shouldBe false
    }

    "save a list of users" in {
      val userList = (0 to 10).toList.map { i =>
        User(userName = s"batch-save-user-$i", "accessKey", Encrypted("secretKey"))
      }

      repo.save(userList).unsafeRunSync() shouldBe userList
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

    "be case insensitive" in {
      repo.getUserByName("name1").unsafeRunSync() shouldBe Some(caseInsensitiveUser1)
      repo.getUserByName("NAME1").unsafeRunSync() shouldBe Some(caseInsensitiveUser1)

      repo.getUserByName("name2").unsafeRunSync() shouldBe Some(caseInsensitiveUser2)
      repo.getUserByName("NAME2").unsafeRunSync() shouldBe Some(caseInsensitiveUser2)

      repo.getUserByName("name3").unsafeRunSync() shouldBe Some(caseInsensitiveUser3)
      repo.getUserByName("NAME3").unsafeRunSync() shouldBe Some(caseInsensitiveUser3)
    }
  }

  "MySqlUserRepository.getUserByIdOrName" should {
    "retrieve a user when userName exists" in {
      repo.getUser(users.head.userName).unsafeRunSync() shouldBe None
      repo.getUserByName(users.head.userName).unsafeRunSync() shouldBe Some(users.head)
      repo.getUserByIdOrName(users.head.userName).unsafeRunSync() shouldBe Some(users.head)
    }

    "retrieve a user when user ID exists" in {
      repo.getUser(users.head.id).unsafeRunSync() shouldBe Some(users.head)
      repo.getUserByName(users.head.id).unsafeRunSync() shouldBe None
      repo.getUserByIdOrName(users.head.id).unsafeRunSync() shouldBe Some(users.head)
    }

    "returns None when both user ID and userName do not exist" in {
      repo.getUser("no-existo").unsafeRunSync() shouldBe None
      repo.getUserByName("no-existo").unsafeRunSync() shouldBe None
      repo.getUserByIdOrName("no-existo").unsafeRunSync() shouldBe None
    }

    "be case insensitive" in {
      repo.getUserByIdOrName("name1").unsafeRunSync() shouldBe Some(caseInsensitiveUser1)
      repo.getUserByIdOrName("NAME1").unsafeRunSync() shouldBe Some(caseInsensitiveUser1)

      repo.getUserByIdOrName("name2").unsafeRunSync() shouldBe Some(caseInsensitiveUser2)
      repo.getUserByIdOrName("NAME2").unsafeRunSync() shouldBe Some(caseInsensitiveUser2)

      repo.getUserByIdOrName("name3").unsafeRunSync() shouldBe Some(caseInsensitiveUser3)
      repo.getUserByIdOrName("NAME3").unsafeRunSync() shouldBe Some(caseInsensitiveUser3)
    }
  }

  "MySqlUserRepository.getUsers" should {
    "omit all non existing users" in {
      val result = repo.getUsers(Set("no-existo", users.head.id), None, None).unsafeRunSync()
      result.users shouldBe List(users.head)
    }

    "return all users when no max item limit is provided" in {
      val result = repo.getUsers(testUserIds.toSet, None, None).unsafeRunSync()
      result.users should contain theSameElementsAs users
      result.lastEvaluatedId shouldBe None
    }

    "return all users when total user size is less than max item limit" in {
      val result = repo.getUsers(testUserIds.toSet, None, Some(102)).unsafeRunSync()
      result.users should contain theSameElementsAs users
      result.lastEvaluatedId shouldBe None
    }

    "return up to max item limit when total user size is larger than max item limit" in {
      val result = repo.getUsers(testUserIds.toSet, None, Some(25)).unsafeRunSync()
      val expected = users.take(25)
      result.users should contain theSameElementsAs expected
      result.lastEvaluatedId shouldBe Some(expected.last.id)
    }

    "return all items if total user size is equal to max item limit" in {
      val result = repo.getUsers(testUserIds.toSet, None, Some(101)).unsafeRunSync()
      result.users should contain theSameElementsAs users
      result.lastEvaluatedId shouldBe None
    }

    "return all items starting from start ID if provided" in {
      val result = repo.getUsers(testUserIds.toSet, Some(testUserIds(2)), None).unsafeRunSync()
      result.users.head.id shouldBe testUserIds(3)
    }

    "return empty list when given no ids" in {
      val result = repo.getUsers(Set[String](), None, None).unsafeRunSync()
      result.users should contain theSameElementsAs List()
    }
  }
}
