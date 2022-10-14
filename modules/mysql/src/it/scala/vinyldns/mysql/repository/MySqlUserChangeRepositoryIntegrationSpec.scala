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

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc.DB
import vinyldns.core.domain.Encrypted
import vinyldns.core.domain.membership.UserChange.{CreateUser, UpdateUser}
import vinyldns.core.domain.membership.{User, UserChangeRepository}
import vinyldns.mysql.TestMySqlInstance

class MySqlUserChangeRepositoryIntegrationSpec
    extends AnyWordSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers {

  private val repo: UserChangeRepository = TestMySqlInstance.userChangeRepository
  private val user: User = User("user-id", "access-key", Encrypted("secret-key"))
  private val createUser = CreateUser(user, "creator-id", user.created)
  private val updateUser =
    UpdateUser(user.copy(userName = "new-username"), "creator-id", user.created, user)

  def clear(): Unit =
    DB.localTx { implicit s =>
      s.executeUpdate("DELETE FROM user_change")
      ()
    }

  override protected def beforeEach(): Unit = clear()

  override protected def afterAll(): Unit = clear()

  "MySqlUserChangeRepo.get" should {
    "get a user change" in {
      repo.save(createUser).unsafeRunSync() shouldBe createUser
      repo.get(createUser.id).unsafeRunSync() shouldBe Some(createUser)
    }

    "return None if user change doesn't exist" in {
      repo.get("does-not-exist").unsafeRunSync() shouldBe None
    }
  }

  "MySqlUserChangeRepo.save" should {
    "successfully save a CreateUser" in {
      repo.save(createUser).unsafeRunSync() shouldBe createUser
    }

    "successfully save an UpdateUser" in {
      repo.save(updateUser).unsafeRunSync() shouldBe updateUser
    }

    "on duplicate key update a user change" in {
      val overwriteCreateUser = createUser.copy(madeByUserId = "overwrite-creator")
      repo.save(createUser).unsafeRunSync() shouldBe createUser
      repo.save(overwriteCreateUser).unsafeRunSync() shouldBe overwriteCreateUser

      repo.get(createUser.id).unsafeRunSync() shouldBe Some(overwriteCreateUser)
    }
  }
}
