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

package models

import java.util.UUID

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class UserAccountSpec extends Specification with Mockito {
  "UserAccount" should {
    "Create a UserAccount from username, first name, last name and email" in {
      val username = "fbaggins"
      val fname = Some("Frodo")
      val lname = Some("Baggins")
      val email = Some("fb@hobbitmail.me")

      val result = UserAccount(username, fname, lname, email)

      result must beAnInstanceOf[UserAccount]
      UUID.fromString(result.userId) must beAnInstanceOf[UUID]
      result.username must beEqualTo(username)
      result.firstName must beEqualTo(fname)
      result.lastName must beEqualTo(lname)
      result.email must beEqualTo(email)
      result.accessKey.length must beEqualTo(20)
      result.accessSecret.length must beEqualTo(20)
    }

    "Copy an existing UserAccount with different accessKey and accessSecret" in {
      val username = "fbaggins"
      val fname = Some("Frodo")
      val lname = Some("Baggins")
      val email = Some("fb@hobbitmail.me")

      val result = UserAccount(username, fname, lname, email)
      val newResult = UserAccount.regenerateCredentials(result)

      newResult must beAnInstanceOf[UserAccount]
      UUID.fromString(newResult.userId) must beAnInstanceOf[UUID]
      newResult.username must beEqualTo(username)
      newResult.firstName must beEqualTo(fname)
      newResult.lastName must beEqualTo(lname)
      newResult.email must beEqualTo(email)
      newResult.accessKey.length must beEqualTo(20)
      newResult.accessSecret.length must beEqualTo(20)
      newResult.accessKey mustNotEqual result.accessKey
      newResult.accessSecret mustNotEqual result.accessSecret
    }
  }
}
