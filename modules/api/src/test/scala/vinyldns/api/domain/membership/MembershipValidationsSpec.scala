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
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.ResultHelpers
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.api.domain.zone.NotAuthorizedError
import vinyldns.core.domain.Encrypted
import vinyldns.core.domain.membership.User

class MembershipValidationsSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with ResultHelpers
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
        canEditGroup(okGroup, okAuth) should be(right)
      }
      "return true when the user is a super user" in {
        canEditGroup(okGroup, superUserAuth) should be(right)
      }
      "return an error when the user is a support admin only" in {
        val user = User("some", "new", Encrypted("user"), isSupport = true)
        val supportAuth = AuthPrincipal(user, Seq())
        val error = leftValue(canEditGroup(okGroup, supportAuth))
        error shouldBe an[NotAuthorizedError]
      }
      "return an error when the user has no access and is not super" in {
        val user = User("some", "new", Encrypted("user"))
        val nonSuperAuth = AuthPrincipal(user, Seq())
        val error = leftValue(canEditGroup(okGroup, nonSuperAuth))
        error shouldBe an[NotAuthorizedError]
      }
    }

    "canSeeGroup" should {
      "return true when the user is in the group" in {
        canSeeGroup(okGroup.id, okAuth) should be(right)
      }
      "return true when the user is a super user" in {
        canSeeGroup(okGroup.id, superUserAuth) should be(right)
      }
      "return true when the user is a support admin" in {
        val user = User("some", "new", Encrypted("user"), isSupport = true)
        val supportAuth = AuthPrincipal(user, Seq())
        canSeeGroup(okGroup.id, supportAuth) should be(right)
      }
      "return true even when a user is not a member of the group or super" in {
        val user = User("some", "new", Encrypted("user"))
        val nonSuperAuth = AuthPrincipal(user, Seq())
        canSeeGroup(okGroup.id, nonSuperAuth) should be(right)
      }
    }

    "canSeeGroupChange" should {
      "return true when the user is in the group" in {
        canSeeGroup(okGroup.id, okAuth) should be(right)
      }
      "return true when the user is a super user" in {
        canSeeGroup(okGroup.id, superUserAuth) should be(right)
      }
      "return true when the user is a support admin" in {
        val user = User("some", "new", Encrypted("user"), isSupport = true)
        val supportAuth = AuthPrincipal(user, Seq())
        canSeeGroup(okGroup.id, supportAuth) should be(right)
      }
    }

    "User toString" should {
      "not display access and secret keys" in {
        val userString = s"""User: [id="ok"; userName="ok"; firstName="Some(ok)"; lastName="Some(ok)"; email="Some(test@test.com)"; created="${okUser.created}"; isSuper="false"; isSupport="false"; isTest="false"; lockStatus="Unlocked"; ]"""
        okUser.toString shouldBe userString
      }
    }

    "isGroupChangePresent" should {
      "return true when there is a group change present for the requested group change id" in {
        isGroupChangePresent(Some(okGroupChange)) should be(right)
      }
      "return an error when there is a group change present for the requested group change id" in {
        val error = leftValue(isGroupChangePresent(None))
        error shouldBe an[InvalidGroupRequestError]
      }

    }
  }
}
