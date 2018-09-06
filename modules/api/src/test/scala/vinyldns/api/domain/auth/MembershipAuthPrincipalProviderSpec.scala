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

package vinyldns.api.domain.auth

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import vinyldns.api.{GroupTestData, ResultHelpers}
import vinyldns.core.domain.membership.{MembershipRepository, UserRepository}
import cats.effect._
import vinyldns.core.domain.auth.AuthPrincipal

class MembershipAuthPrincipalProviderSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with ResultHelpers
    with GroupTestData {

  "MembershipAuthPrincipalProvider" should {
    "return the AuthPrincipal" in {
      val mockUserRepo = mock[UserRepository]
      val mockMembershipRepo = mock[MembershipRepository]
      val underTest = new MembershipAuthPrincipalProvider(mockUserRepo, mockMembershipRepo)

      val user = okUser
      val accessKey = user.accessKey

      doReturn(IO.pure(Option(okUser)))
        .when(mockUserRepo)
        .getUserByAccessKey(any[String])

      doReturn(IO.pure(Set(okGroup.id, dummyGroup.id)))
        .when(mockMembershipRepo)
        .getGroupsForUser(any[String])

      val result = await[Option[AuthPrincipal]](underTest.getAuthPrincipal(accessKey))
      result.map { authPrincipal =>
        authPrincipal.signedInUser shouldBe okUser
        authPrincipal.memberGroupIds should contain theSameElementsAs Seq(okGroup.id, dummyGroup.id)
      }
    }
    "return None if there is no such user" in {
      val mockUserRepo = mock[UserRepository]
      val mockMembershipRepo = mock[MembershipRepository]
      val underTest = new MembershipAuthPrincipalProvider(mockUserRepo, mockMembershipRepo)

      doReturn(IO.pure(None))
        .when(mockUserRepo)
        .getUserByAccessKey(any[String])

      val result = await[Option[AuthPrincipal]](underTest.getAuthPrincipal("None"))
      result shouldBe None
    }
    "return an empty list of groups if there are no matching groups" in {
      val user = okUser
      val accessKey = user.accessKey
      val mockUserRepo = mock[UserRepository]
      val mockMembershipRepo = mock[MembershipRepository]
      val underTest = new MembershipAuthPrincipalProvider(mockUserRepo, mockMembershipRepo)

      doReturn(IO.pure(Option(okUser)))
        .when(mockUserRepo)
        .getUserByAccessKey(any[String])

      doReturn(IO.pure(Set()))
        .when(mockMembershipRepo)
        .getGroupsForUser(any[String])

      val result = await[Option[AuthPrincipal]](underTest.getAuthPrincipal(accessKey))
      result.map { authPrincipal =>
        authPrincipal.signedInUser shouldBe okUser
        authPrincipal.memberGroupIds shouldBe Seq()
      }
    }
  }
}
