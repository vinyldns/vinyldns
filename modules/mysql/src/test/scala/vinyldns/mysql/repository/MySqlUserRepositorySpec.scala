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

import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.crypto.NoOpCrypto

class MySqlUserRepositorySpec extends WordSpec with Matchers {

  val repo = new MySqlUserRepository(new NoOpCrypto())

  "buildGetUsersQuery" should {
    "return correct query with multiple elements in list" in {
      val userIds = Set("id1", "id2", "id3")
      val expectedQuery = repo.BASE_GET_USERS + "('id1', 'id2', 'id3')"
      val actualQuery = repo.buildGetUsersQuery(userIds)
      actualQuery shouldBe expectedQuery
    }

    "return correct query with one element in list" in {
      val userIds = Set("id1")
      val expectedQuery = repo.BASE_GET_USERS + "('id1')"
      val actualQuery = repo.buildGetUsersQuery(userIds)
      actualQuery shouldBe expectedQuery
    }
  }
}
