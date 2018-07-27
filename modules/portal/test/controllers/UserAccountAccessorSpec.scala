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

import models.UserAccount
import org.joda.time.DateTime
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.util.{Failure, Success}

class UserAccountAccessorSpec extends Specification with Mockito {
  "User Account Accessor" should {
    "Return the user when storing a user that does not exist already" in {
      val mockStore = mock[UserAccountStore]
      val user = new UserAccount(
        "uuid",
        "fbaggins",
        Some("Frodo"),
        Some("Baggins"),
        Some("fbaggins@hobbitmail.me"),
        DateTime.now(),
        "key",
        "secret")
      mockStore.storeUser(any[UserAccount]).returns(Success(user))

      val underTest = new UserAccountAccessor(mockStore)
      underTest.put(user) must beASuccessfulTry(user)
    }

    "Return the new user when storing a user that already exists in the store" in {
      val mockStore = mock[UserAccountStore]
      val oldUser = new UserAccount(
        "uuid",
        "fbaggins",
        Some("Frodo"),
        Some("Baggins"),
        Some("fbaggins@hobbitmail.me"),
        DateTime.now(),
        "key",
        "secret")
      val newUser = new UserAccount(
        "uuid",
        "fbaggins",
        Some("Frodo"),
        Some("Baggins"),
        Some("fbaggins@hobbitmail.me"),
        DateTime.now(),
        "new-key",
        "new-secret")
      mockStore.storeUser(any[UserAccount]).returns(Success(newUser))

      val underTest = new UserAccountAccessor(mockStore)
      underTest.put(newUser) must beASuccessfulTry(newUser)
    }

    "Return the failure when something goes wrong while storing a user" in {
      val mockStore = mock[UserAccountStore]
      val ex = new IllegalArgumentException("foobar")
      mockStore.storeUser(any[UserAccount]).returns(Failure(ex))
      val user = new UserAccount(
        "uuid",
        "fbaggins",
        Some("Frodo"),
        Some("Baggins"),
        Some("fbaggins@hobbitmail.me"),
        DateTime.now(),
        "key",
        "secret")

      val underTest = new UserAccountAccessor(mockStore)
      underTest.put(user) must beAFailedTry(ex)
    }

    "Return the user when retrieving a user that exists by name" in {
      val mockStore = mock[UserAccountStore]
      val user = new UserAccount(
        "uuid",
        "fbaggins",
        Some("Frodo"),
        Some("Baggins"),
        Some("fbaggins@hobbitmail.me"),
        DateTime.now(),
        "key",
        "secret")
      mockStore.getUserByName(user.username).returns(Success(Some(user)))
      mockStore.getUserById(user.username).returns(Success(None))

      val underTest = new UserAccountAccessor(mockStore)
      underTest.get("fbaggins") must beASuccessfulTry[Option[UserAccount]](Some(user))
    }

    "Return the user when retrieving a user that exists by user id" in {
      val mockStore = mock[UserAccountStore]
      val user = new UserAccount(
        "uuid",
        "fbaggins",
        Some("Frodo"),
        Some("Baggins"),
        Some("fbaggins@hobbitmail.me"),
        DateTime.now(),
        "key",
        "secret")
      mockStore.getUserByName(user.userId).returns(Success(None))
      mockStore.getUserById(user.userId).returns(Success(Some(user)))

      val underTest = new UserAccountAccessor(mockStore)
      underTest.get("uuid") must beASuccessfulTry[Option[UserAccount]](Some(user))
    }

    "Return None when the user to be retrieved does not exist" in {
      val mockStore = mock[UserAccountStore]
      mockStore.getUserByName(any[String]).returns(Success(None))
      mockStore.getUserById(any[String]).returns(Success(None))

      val underTest = new UserAccountAccessor(mockStore)
      underTest.get("fbaggins") must beASuccessfulTry[Option[UserAccount]](None)
    }

    "Return the failure when the user cannot be looked up via user name" in {
      val mockStore = mock[UserAccountStore]
      val user = new UserAccount(
        "uuid",
        "fbaggins",
        Some("Frodo"),
        Some("Baggins"),
        Some("fbaggins@hobbitmail.me"),
        DateTime.now(),
        "key",
        "secret")
      val ex = new IllegalArgumentException("foobar")
      mockStore.getUserByName(user.username).returns(Failure(ex))
      mockStore.getUserById(user.username).returns(Success(None))

      val underTest = new UserAccountAccessor(mockStore)
      underTest.get("fbaggins") must beAFailedTry(ex)
    }

    "Return the failure when the user cannot be looked up via user id" in {
      val mockStore = mock[UserAccountStore]
      val user = new UserAccount(
        "uuid",
        "fbaggins",
        Some("Frodo"),
        Some("Baggins"),
        Some("fbaggins@hobbitmail.me"),
        DateTime.now(),
        "key",
        "secret")
      val ex = new IllegalArgumentException("foobar")
      mockStore.getUserByName(user.userId).returns(Success(None))
      mockStore.getUserById(user.userId).returns(Failure(ex))

      val underTest = new UserAccountAccessor(mockStore)
      underTest.get("uuid") must beAFailedTry(ex)
    }
  }
}
