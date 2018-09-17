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

package vinyldns.api.domain.membership

import cats.scalatest.EitherMatchers
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpecLike}
import vinyldns.api.{GroupTestData, ResultHelpers}
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.api.domain.zone.NotAuthorizedError
import vinyldns.core.domain.membership.User

class MembershipValidationsSpec
    extends WordSpecLike
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with ResultHelpers
    with GroupTestData
    with EitherMatchers {

  import vinyldns.api.domain.membership.MembershipValidations._

  "MembershipValidations" should {
    "hasMembersAndAdmins" should {
      "return true when a group has at least one member and one admin" in {
        hasMembersAndAdmins(okGroup) should be(right)
      }

      "return an error when a group has no members" in {
        val badGroup = okGroup.copy(memberIds = Set())
        val error = leftValue(hasMembersAndAdmins(badGroup))
        error shouldBe an[InvalidGroupError]
      }

      "return an error when a group has no admins" in {
        val badGroup = okGroup.copy(adminUserIds = Set())
        val error = leftValue(hasMembersAndAdmins(badGroup))
        error shouldBe an[InvalidGroupError]
      }
    }

    "isAdmin" should {
      "return true when the user is in admin group" in {
        isGroupAdmin(okGroup, okUserAuth) should be(right)
      }
      "return true when the user is a super user" in {
        val user = User("some", "new", "user", isSuper = true)
        val superAuth = AuthPrincipal(user, Seq())
        isGroupAdmin(okGroup, superAuth) should be(right)
      }
      "return an error when the user has no access and is not super" in {
        val user = User("some", "new", "user")
        val nonSuperAuth = AuthPrincipal(user, Seq())
        val error = leftValue(isGroupAdmin(okGroup, nonSuperAuth))
        error shouldBe an[NotAuthorizedError]
      }
    }

    "canSeeGroup" should {
      "return true when the user is in the group" in {
        canSeeGroup(okGroup.id, okUserAuth) should be(right)
      }
      "return true when the user is a super user" in {
        val user = User("some", "new", "user", isSuper = true)
        val superAuth = AuthPrincipal(user, Seq())
        canSeeGroup(okGroup.id, superAuth) should be(right)
      }
      "return an error when the user has no access and is not super" in {
        val user = User("some", "new", "user")
        val nonSuperAuth = AuthPrincipal(user, Seq())
        val error = leftValue(canSeeGroup(okGroup.id, nonSuperAuth))
        error shouldBe an[NotAuthorizedError]
      }

    }
  }
}
