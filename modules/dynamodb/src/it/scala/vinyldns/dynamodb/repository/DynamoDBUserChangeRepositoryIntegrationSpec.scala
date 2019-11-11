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

package vinyldns.dynamodb.repository
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import vinyldns.core.crypto.NoOpCrypto
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.{User, UserChange}

class DynamoDBUserChangeRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private val USER_CHANGE_TABLE = "user-changes"

  private val tableConfig = DynamoDBRepositorySettings(s"$USER_CHANGE_TABLE", 30, 30)

  private val testUser = User(
    id = "test-user",
    userName = "testUser",
    firstName = Some("Test"),
    lastName = Some("User"),
    email = Some("test@user.com"),
    created = DateTime.now,
    isSuper = false,
    accessKey = "test",
    secretKey = "user"
  )

  private val repo: DynamoDBUserChangeRepository =
    DynamoDBUserChangeRepository(
      tableConfig,
      dynamoIntegrationConfig,
      new NoOpCrypto(ConfigFactory.load())
    ).unsafeRunSync()

  def setup(): Unit = ()

  def tearDown(): Unit = {
    val request = new DeleteTableRequest().withTableName(USER_CHANGE_TABLE)
    val deleteTables = repo.dynamoDBHelper.deleteTable(request)
    deleteTables.unsafeRunSync()
  }

  "DynamoDBUserChangeRepository" should {
    "save a user change" in {
      val auth = AuthPrincipal(testUser, Seq.empty)
      val c = UserChange.CreateUser(testUser, auth.userId, DateTime.now, "foo")

      val t = for {
        _ <- repo.save(c)
        retrieved <- repo.get(c.id)
      } yield retrieved

      t.unsafeRunSync() shouldBe Some(c)
    }

    "save a change for a modified user" in {
      val auth = AuthPrincipal(testUser, Seq.empty)
      val updated = testUser.copy(userName = testUser.userName + "-updated")
      val c = UserChange.UpdateUser(updated, auth.userId, DateTime.now, testUser, "foo")

      val t = for {
        _ <- repo.save(c)
        retrieved <- repo.get(c.id)
      } yield retrieved

      t.unsafeRunSync() shouldBe Some(c)
    }
  }
}
