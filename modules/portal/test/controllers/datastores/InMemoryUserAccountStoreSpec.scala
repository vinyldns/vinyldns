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

package controllers.datastores

import models.UserAccount
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class InMemoryUserAccountStoreSpec extends Specification with Mockito {
  "InMemoryUserAccountStore" should {
    "Store a new user when everything is ok" in {
      val underTest = new InMemoryUserAccountStore()
      val user = mock[UserAccount]

      val result = underTest.storeUser(user)
      result must beASuccessfulTry(user)
    }

    "Store a user over an existing user returning the new user" in {
      val underTest = new InMemoryUserAccountStore()
      val oldUser = mock[UserAccount]
      oldUser.userId.returns("user")
      oldUser.username.returns("old")
      val newUser = mock[UserAccount]
      newUser.userId.returns("user")
      newUser.username.returns("new")

      underTest.storeUser(oldUser)
      val result = underTest.storeUser(newUser)
      result must beASuccessfulTry(newUser)
      result.get.username must beEqualTo("new")
    }

    "Retrieve a given user based on user-id" in {
      val user = UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val underTest = new InMemoryUserAccountStore()
      underTest.storeUser(user)

      val result = underTest.getUserById(user.userId)
      result must beASuccessfulTry[Option[UserAccount]](Some(user))
    }

    "Retrieve a given user based on username" in {
      val user = UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val underTest = new InMemoryUserAccountStore()
      underTest.storeUser(user)

      val result = underTest.getUserByName(user.username)
      result must beASuccessfulTry[Option[UserAccount]](Some(user))
    }

    "Return a successful none if the user is not found by id" in {
      val user = UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val underTest = new InMemoryUserAccountStore()

      val result = underTest.getUserById(user.userId)
      result must beASuccessfulTry[Option[UserAccount]](None)
    }

    "Return a successful none if the user is not found by name" in {
      val user = UserAccount("fbaggins", Some("Frodo"), Some("Baggins"), Some("fb@hobbitmail.me"))
      val underTest = new InMemoryUserAccountStore()

      val result = underTest.getUserByName(user.username)
      result must beASuccessfulTry[Option[UserAccount]](None)
    }
  }
}
