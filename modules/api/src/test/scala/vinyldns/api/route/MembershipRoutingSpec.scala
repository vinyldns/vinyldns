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

package vinyldns.api.route

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest

import java.time.Instant
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import vinyldns.api.Interfaces._
import vinyldns.api.config.LimitsConfig
import vinyldns.api.domain.membership.{UserResponseInfo, _}
import vinyldns.api.domain.zone.NotAuthorizedError
import vinyldns.api.route.MembershipJsonProtocol.{CreateGroupInput, UpdateGroupInput}
import vinyldns.core.TestMembershipData._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership.LockStatus.LockStatus
import vinyldns.core.domain.membership.{Group, LockStatus}

class MembershipRoutingSpec
    extends AnyWordSpec
    with ScalatestRouteTest
    with VinylDNSJsonProtocol
    with VinylDNSRouteTestHelper
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach {

  val membershipService: MembershipService = mock[MembershipService]
  val testLimitConfig: LimitsConfig =
    LimitsConfig(100,100,1000,1500,100,100,100)
  val okAuthRoute: Route =
    new MembershipRoute(membershipService,testLimitConfig, new TestVinylDNSAuthenticator(okAuth)).getRoutes
  val superUserRoute: Route =
    new MembershipRoute(membershipService,testLimitConfig, new TestVinylDNSAuthenticator(superUserAuth)).getRoutes
  var membershipRoute: Route = _

  override protected def beforeEach(): Unit = {
    reset(membershipService)
    membershipRoute = okAuthRoute
  }

  private def js[A](info: A): String = compact(render(Extraction.decompose(info)))

  // marshalling and unmarshalling cuts off some 0s. By setting the times to epoch,
  // this is avoided since were working with 0s there anyway
  val baseTime: Instant = Instant.ofEpochMilli(0)
  val okUserInfo: UserInfo = UserInfo(okUser).copy(created = Some(baseTime))
  val okUserId: UserId = UserId(okUser.id)
  val dummyUserInfo: UserInfo = UserInfo(dummyUser).copy(created = Some(baseTime))
  val okGroupInfo: GroupInfo = GroupInfo(okGroup).copy(created = baseTime)
  val okMemberInfo: MemberInfo = MemberInfo(okUser, okGroup).copy(created = Some(baseTime))
  val dummyMemberInfo: MemberInfo = MemberInfo(dummyUser, okGroup).copy(created = Some(baseTime))
  val listOfMembersInfo: List[MemberInfo] = listOfDummyUsers.map(MemberInfo(_, okGroup))
  val okGroupChangeInfo: GroupChangeInfo = GroupChangeInfo(okGroupChange)
  val okGroupChangeUpdateInfo: GroupChangeInfo = GroupChangeInfo(okGroupChangeUpdate)
  val okGroupChangeDeleteInfo: GroupChangeInfo = GroupChangeInfo(okGroupChangeDelete)
  val okUserResponseInfo: UserResponseInfo = UserResponseInfo(okUser, okGroup)
  val dummyUserResponseInfo: UserResponseInfo = UserResponseInfo(listOfDummyUsers.head,dummyGroup)

  "POST groups" should {
    "return a 200 response when successful" in {
      val goodRequest = CreateGroupInput(
        "good",
        "test@test.com",
        Some("describe me"),
        Set(okUserId),
        Set(okUserId)
      )
      val expected = GroupInfo(okGroup)

      doReturn(result(okGroup)).when(membershipService).createGroup(any[Group], any[AuthPrincipal])

      Post("/groups").withEntity(HttpEntity(ContentTypes.`application/json`, js(goodRequest))) ~> Route
        .seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.OK

        val result = responseAs[GroupInfo]

        result.name shouldBe expected.name
        result.email shouldBe expected.email
        result.description shouldBe expected.description
        result.members should contain theSameElementsAs expected.members
        result.admins should contain theSameElementsAs expected.admins
      }
    }

    "return a 409 response when group already exists" in {
      val duplicateRequest = CreateGroupInput(
        "duplicate",
        "test@test.com",
        Some("describe me"),
        Set(okUserId),
        Set(okUserId)
      )
      doReturn(result(GroupAlreadyExistsError("fail")))
        .when(membershipService)
        .createGroup(any[Group], any[AuthPrincipal])

      Post("/groups").withEntity(HttpEntity(ContentTypes.`application/json`, js(duplicateRequest))) ~> Route
        .seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }

    "return a 400 response if the group doesn't have members or admins" in {
      val badRequest = CreateGroupInput("bad", "test@test.com", Some("describe me"), Set(), Set())
      doReturn(result(InvalidGroupError("fail")))
        .when(membershipService)
        .createGroup(any[Group], any[AuthPrincipal])

      Post("/groups").withEntity(HttpEntity(ContentTypes.`application/json`, js(badRequest))) ~> Route
        .seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "return a 400 response if the group request is malformed" in {
      val missingFields: JValue =
        ("invalidField" -> "randomValue") ~~
          ("description" -> "missing values")
      val malformed = compact(render(missingFields))

      Post("/groups").withEntity(HttpEntity(ContentTypes.`application/json`, malformed)) ~> Route
        .seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get.toSet shouldBe Set(
          "Missing Group.name",
          "Missing Group.email",
          "Missing Group.members",
          "Missing Group.admins"
        )
      }
    }

    "return a 404 response when the creator is not a user" in {
      val notFoundRequest = CreateGroupInput(
        "not found",
        "test@test.com",
        Some("describe me"),
        Set(okUserId),
        Set(okUserId)
      )
      doReturn(result(UserNotFoundError("not found")))
        .when(membershipService)
        .createGroup(any[Group], any[AuthPrincipal])

      Post("/groups").withEntity(HttpEntity(ContentTypes.`application/json`, js(notFoundRequest))) ~> Route
        .seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a 500 response when fails" in {
      val badRequest = CreateGroupInput(
        "bad",
        "test@test.com",
        Some("describe me"),
        Set(okUserId),
        Set(okUserId)
      )
      doReturn(result(new IllegalArgumentException("fail")))
        .when(membershipService)
        .createGroup(any[Group], any[AuthPrincipal])

      Post("/groups").withEntity(HttpEntity(ContentTypes.`application/json`, js(badRequest))) ~> Route
        .seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }
  }

  "GET Groups" should {
    "return a 200 response with the groups when no optional parameters are used" in {
      val twoUserGroupInfo = GroupInfo(twoUserGroup)

      doReturn(
        result(
          ListMyGroupsResponse(Seq(okGroupInfo, twoUserGroupInfo), None, None, None, 100, false)
        )
      ).when(membershipService).listMyGroups(None, None, 100, okAuth, false, false)

      Get("/groups") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.OK

        val result = responseAs[ListMyGroupsResponse]
        val expected =
          ListMyGroupsResponse(Seq(okGroupInfo, twoUserGroupInfo), None, None, None, 100, false)

        result shouldBe expected
      }
    }
    "return a 200 response with groups when all optional parameters are used" in {
      doReturn(
        result(
          ListMyGroupsResponse(
            groups = Seq(okGroupInfo),
            groupNameFilter = Some("ok"),
            startFrom = Some("anyString"),
            nextId = None,
            maxItems = 100,
            ignoreAccess = false
          )
        )
      ).when(membershipService)
        .listMyGroups(
          groupNameFilter = Some("ok"),
          startFrom = Some("anyString"),
          maxItems = 100,
          okAuth,
          ignoreAccess = false,
          abridged = true
        )
      Get("/groups?startFrom=anyString&maxItems=100&groupNameFilter=ok&abridged=true") ~> Route.seal(
        membershipRoute
      ) ~> check {
        status shouldBe StatusCodes.OK

        val result = responseAs[ListMyGroupsResponse]
        val expected = ListMyGroupsResponse(
          groups = Seq(okGroupInfo),
          groupNameFilter = Some("ok"),
          startFrom = Some("anyString"),
          maxItems = 100,
          nextId = None,
          ignoreAccess = false
        )

        result shouldBe expected
      }
    }
    "return with a 400 response when the page size is 0" in {
      Get("/groups?maxItems=0") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
    "return a 400 response when maxItems is more than 1500" in {
      Get("/groups?maxItems=1501") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
    "return a 500 response when fails" in {
      doReturn(result(new IllegalArgumentException("fail")))
        .when(membershipService)
        .listMyGroups(None, None, 100, okAuth, false, abridged = false)

      Get("/groups") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }
  }

  "GET group" should {
    "return a 200 response with the group when found" in {
      val grpBaseTime = okGroup.copy(created = baseTime)
      doReturn(result(grpBaseTime)).when(membershipService).getGroup("ok", okAuth)
      Get("/groups/ok") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.OK

        val result = responseAs[GroupInfo]
        result shouldBe GroupInfo(grpBaseTime)
      }
    }

    "return a 404 Not Found when the group is not found" in {
      doReturn(result(GroupNotFoundError("fail")))
        .when(membershipService)
        .getGroup("notFound", okAuth)
      Get("/groups/notFound") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  "DELETE group" should {
    "return a 200 response with the deleted group when it exists" in {
      val grpBaseTime = deletedGroup.copy(created = baseTime)
      doReturn(result(grpBaseTime)).when(membershipService).deleteGroup("ok", okAuth)
      Delete("/groups/ok") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.OK

        val result = responseAs[GroupInfo]
        result shouldBe GroupInfo(grpBaseTime)
      }
    }

    "return a 404 Not Found when the group is not found" in {
      doReturn(result(GroupNotFoundError("fail")))
        .when(membershipService)
        .deleteGroup("notFound", okAuth)
      Delete("/groups/notFound") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a 400 Bad Request when the group is an admin of a zone" in {
      doReturn(result(InvalidGroupRequestError("fail")))
        .when(membershipService)
        .deleteGroup("adminGroup", okAuth)
      Delete("/groups/adminGroup") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "return a 500 response when fails" in {
      doReturn(result(new IllegalArgumentException("fail")))
        .when(membershipService)
        .deleteGroup("bad", okAuth)

      Delete("/groups/bad") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }

    "return a 403 response when not authorized" in {
      doReturn(result(NotAuthorizedError("forbidden")))
        .when(membershipService)
        .deleteGroup("forbidden", okAuth)
      Delete("/groups/forbidden") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }
  }

  "UPDATE group" should {
    "return a 200 response with the updated group" in {
      val goodRequest = UpdateGroupInput(
        "id",
        "good",
        "test@test.com",
        Some("describe me"),
        Set(okUserId),
        Set(okUserId)
      )

      doReturn(result(okGroup))
        .when(membershipService)
        .updateGroup(
          anyString,
          anyString,
          anyString,
          any[Option[String]],
          any[Set[String]],
          any[Set[String]],
          any[AuthPrincipal]
        )

      Put("/groups/good").withEntity(HttpEntity(ContentTypes.`application/json`, js(goodRequest))) ~> Route
        .seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.OK

        val result = responseAs[GroupInfo]

        result.name shouldBe okGroup.name
        result.email shouldBe okGroup.email
        result.description shouldBe okGroup.description
        result.members shouldBe okGroup.memberIds.map(UserId(_))
        result.admins shouldBe okGroup.memberIds.map(UserId(_))
      }
    }

    "return a 409 response when a group of the same name already exists" in {
      val duplicateRequest = UpdateGroupInput(
        "id",
        "duplicate",
        "test@test.com",
        Some("describe me"),
        Set(okUserId),
        Set(okUserId)
      )
      doReturn(result(GroupAlreadyExistsError("fail")))
        .when(membershipService)
        .updateGroup(
          anyString,
          anyString,
          anyString,
          any[Option[String]],
          any[Set[String]],
          any[Set[String]],
          any[AuthPrincipal]
        )

      Put("/groups/duplicate").withEntity(
        HttpEntity(ContentTypes.`application/json`, js(duplicateRequest))
      ) ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }

    "return a 404 Not Found when the group is not found" in {
      val notFoundRequest = UpdateGroupInput(
        "id",
        "notFound",
        "test@test.com",
        Some("describe me"),
        Set.empty,
        Set.empty
      )
      doReturn(result(GroupNotFoundError("fail")))
        .when(membershipService)
        .updateGroup(
          anyString,
          anyString,
          anyString,
          any[Option[String]],
          any[Set[String]],
          any[Set[String]],
          any[AuthPrincipal]
        )
      Put("/groups/notFound").withEntity(
        HttpEntity(ContentTypes.`application/json`, js(notFoundRequest))
      ) ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a 403 response when not authorized" in {
      val forbiddenRequest = UpdateGroupInput(
        "id",
        "forbidden",
        "test@test.com",
        Some("describe me"),
        Set(okUserId),
        Set(okUserId)
      )

      doReturn(result(NotAuthorizedError("fail")))
        .when(membershipService)
        .updateGroup(
          anyString,
          anyString,
          anyString,
          any[Option[String]],
          any[Set[String]],
          any[Set[String]],
          any[AuthPrincipal]
        )
      Put("/groups/forbidden").withEntity(
        HttpEntity(ContentTypes.`application/json`, js(forbiddenRequest))
      ) ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    "return a 500 response when fails" in {
      val badRequest =
        UpdateGroupInput("bad", "bad", "test@test.com", Some("describe me"), Set.empty, Set.empty)
      doReturn(result(new IllegalArgumentException("fail")))
        .when(membershipService)
        .updateGroup(
          anyString,
          anyString,
          anyString,
          any[Option[String]],
          any[Set[String]],
          any[Set[String]],
          any[AuthPrincipal]
        )

      Put("/groups/bad").withEntity(HttpEntity(ContentTypes.`application/json`, js(badRequest))) ~> Route
        .seal(Route.seal(membershipRoute)) ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }

    "return a 400 response if the group doesn't have members or admins" in {
      val badRequest =
        UpdateGroupInput("bad", "bad", "test@test.com", Some("describe me"), Set(), Set())
      doReturn(result(InvalidGroupError("fail")))
        .when(membershipService)
        .updateGroup(
          anyString,
          anyString,
          anyString,
          any[Option[String]],
          any[Set[String]],
          any[Set[String]],
          any[AuthPrincipal]
        )

      Put("/groups/bad").withEntity(HttpEntity(ContentTypes.`application/json`, js(badRequest))) ~> Route
        .seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "return a 400 response when request is malformed" in {
      val missingFields: JValue =
        ("invalidField" -> "randomValue") ~~
          ("description" -> "missing values")
      val malformed = compact(render(missingFields))

      Put("/groups/malformed").withEntity(HttpEntity(ContentTypes.`application/json`, malformed)) ~> Route
        .seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
        val result = responseAs[JValue]
        val errs = (result \ "errors").extractOpt[List[String]]
        errs should not be None
        errs.get.toSet shouldBe Set(
          "Missing Group.id",
          "Missing Group.name",
          "Missing Group.email",
          "Missing Group.members",
          "Missing Group.admins"
        )
      }
    }
  }

  "GET ListGroupMember" should {
    "return a 200 response with the member list" in {
      doReturn(result(ListMembersResponse(Seq(dummyMemberInfo, okMemberInfo), maxItems = 100)))
        .when(membershipService)
        .listMembers(anyString, any[Option[String]], anyInt, any[AuthPrincipal])

      Get(s"/groups/good/members") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[ListMembersResponse]

        result.members should contain theSameElementsAs Set(dummyMemberInfo, okMemberInfo)
        result.startFrom shouldBe None
        result.nextId shouldBe None
      }
    }
    "return a 404 response when the group is not found" in {
      doReturn(result(GroupNotFoundError("fail")))
        .when(membershipService)
        .listMembers(anyString, any[Option[String]], anyInt, any[AuthPrincipal])

      Get(s"/groups/notFound/members") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
    "return a 500 response on failure" in {
      doReturn(result(new RuntimeException("fail")))
        .when(membershipService)
        .listMembers(anyString, any[Option[String]], anyInt, any[AuthPrincipal])

      Get(s"/groups/bad/members") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }
    "return a 200 response with query parameters" in {
      doReturn(
        result(
          ListMembersResponse(
            Seq(dummyMemberInfo, okMemberInfo),
            Some("dummy"),
            Some("ok"),
            maxItems = 50
          )
        )
      ).when(membershipService)
        .listMembers("goodQuery", Some("dummy"), 50, okAuth)

      Get(s"/groups/goodQuery/members?startFrom=dummy&maxItems=50") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[ListMembersResponse]

        result.members should contain theSameElementsAs Set(dummyMemberInfo, okMemberInfo)
        result.startFrom shouldBe Some("dummy")
        result.nextId shouldBe Some("ok")
        result.maxItems shouldBe 50
      }
    }
    "maxItems should default to 100" in {
      doReturn(result(ListMembersResponse(Seq(dummyMemberInfo, okMemberInfo), maxItems = 100)))
        .when(membershipService)
        .listMembers(anyString, any[Option[String]], anyInt, any[AuthPrincipal])

      Get(s"/groups/pageSize/members") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[ListMembersResponse]

        result.members should contain theSameElementsAs Set(dummyMemberInfo, okMemberInfo)
        result.nextId shouldBe None

        val maxItemsCaptor = ArgumentCaptor.forClass(classOf[Int])
        verify(membershipService).listMembers(
          anyString,
          any[Option[String]],
          maxItemsCaptor.capture(),
          any[AuthPrincipal]
        )

        maxItemsCaptor.getValue shouldBe 100
      }
    }
    "return with a 400 response when the page size is 0" in {
      Get(s"/groups/badPageSize/members?maxItems=0") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
    "return a 400 response when maxItems is more than 1000" in {
      Get(s"/groups/pageSize/members?maxItems=1001") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  "GET ListGroupAdmins" should {
    "return a 200 response with the admin list" in {
      doReturn(result(ListAdminsResponse(Seq(dummyUserInfo, okUserInfo))))
        .when(membershipService)
        .listAdmins("good", okAuth)
      Get(s"/groups/good/admins") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[ListAdminsResponse]

        result.admins should contain theSameElementsAs Set(dummyUserInfo, okUserInfo)
      }
    }
    "return a 404 response when the group is not found" in {
      doReturn(result(GroupNotFoundError("fail")))
        .when(membershipService)
        .listAdmins("notFound", okAuth)
      Get(s"/groups/notFound/admins") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
    "return a 500 response on failure" in {
      doReturn(result(new RuntimeException("fail")))
        .when(membershipService)
        .listAdmins("bad", okAuth)
      Get(s"/groups/bad/admins") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }
  }

  "GET GroupActivity" should {
    "return a 200 response with the group changes" in {
      val expected = ListGroupChangesResponse(
        Seq(okGroupChangeInfo, okGroupChangeUpdateInfo, okGroupChangeDeleteInfo),
        None,
        None,
        100
      )
      doReturn(result(expected))
        .when(membershipService)
        .getGroupActivity("ok", None, 100, okAuth)

      Get(s"/groups/ok/activity") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[ListGroupChangesResponse]

        result.changes should contain theSameElementsAs Seq(
          okGroupChangeInfo,
          okGroupChangeUpdateInfo,
          okGroupChangeDeleteInfo
        )
        result.maxItems shouldBe 100
        result.nextId shouldBe None
        result.startFrom shouldBe None
      }
    }
    "return a 404 response when the group is not found" in {
      doReturn(result(GroupNotFoundError("fail")))
        .when(membershipService)
        .getGroupActivity("notFound", None, 100, okAuth)
      Get(s"/groups/notFound/activity") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
    "return with a 400 response when the page size is 0" in {
      Get(s"/groups/badPageSize/activity?maxItems=0") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
    "return a 400 response when maxItems is more than 1000" in {
      Get(s"/groups/pageSize/activity?maxItems=1001") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
    "maxItems should default to 100" in {
      val expected = ListGroupChangesResponse(
        Seq(okGroupChangeInfo, okGroupChangeUpdateInfo, okGroupChangeDeleteInfo),
        None,
        None,
        100
      )
      doReturn(result(expected))
        .when(membershipService)
        .getGroupActivity(anyString, any[Option[Int]], anyInt, any[AuthPrincipal])

      Get(s"/groups/pageSize/activity") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.OK
        val maxItemsCaptor = ArgumentCaptor.forClass(classOf[Int])
        verify(membershipService).getGroupActivity(
          anyString,
          any[Option[Int]],
          maxItemsCaptor.capture(),
          any[AuthPrincipal]
        )

        maxItemsCaptor.getValue shouldBe 100
      }
    }
    "return a 500 response on failure" in {
      doReturn(result(new RuntimeException("fail")))
        .when(membershipService)
        .getGroupActivity("bad", None, 100, okAuth)
      Get(s"/groups/bad/activity") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }
  }

  "GET group change" should {
    "return a 200 response with the group change when found" in {
      val grpChange = GroupChangeInfo(okGroupChange)
      doReturn(result(grpChange)).when(membershipService).getGroupChange("ok", okAuth)
      Get("/groups/change/ok") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.OK

        val result = responseAs[GroupChangeInfo]
        result shouldBe grpChange
      }
    }

    "return a 400 Bad Request when the group change id is not valid" in {
      doReturn(result(InvalidGroupRequestError("Invalid Group Change ID")))
        .when(membershipService)
        .getGroupChange("notValid", okAuth)
      Get("/groups/change/notValid") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "return a 500 response on failure" in {
      doReturn(result(new RuntimeException("fail")))
        .when(membershipService)
        .getGroupChange("bad", okAuth)
      Get(s"/groups/change/bad") ~> Route.seal(membershipRoute) ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }
  }

  "PUT update user lock status" should {
    "return a 200 response with the user locked" in {
      membershipRoute = superUserRoute
      val updatedUser = okUser.copy(lockStatus = LockStatus.Locked)
      doReturn(result(updatedUser))
        .when(membershipService)
        .updateUserLockStatus("ok", LockStatus.Locked, superUserAuth)

      Put("/users/ok/lock") ~> membershipRoute ~> check {
        status shouldBe StatusCodes.OK

        val result = responseAs[UserInfo]

        result.id shouldBe okUser.id
        result.lockStatus shouldBe LockStatus.Locked
      }
    }

    "return a 200 response with the user unlocked" in {
      membershipRoute = superUserRoute
      val updatedUser = lockedUser.copy(lockStatus = LockStatus.Unlocked)
      doReturn(result(updatedUser))
        .when(membershipService)
        .updateUserLockStatus("locked", LockStatus.Unlocked, superUserAuth)

      Put("/users/locked/unlock") ~> membershipRoute ~> check {
        status shouldBe StatusCodes.OK

        val result = responseAs[UserInfo]

        result.id shouldBe lockedUser.id
        result.lockStatus shouldBe LockStatus.Unlocked
      }
    }

    "return a 404 Not Found when the user is not found" in {
      doReturn(result(UserNotFoundError("fail")))
        .when(membershipService)
        .updateUserLockStatus(anyString, any[LockStatus], any[AuthPrincipal])
      Put("/users/notFound/lock") ~> membershipRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return a 403 Forbidden when not authorized" in {
      doReturn(result(NotAuthorizedError("fail")))
        .when(membershipService)
        .updateUserLockStatus(anyString, any[LockStatus], any[AuthPrincipal])
      Put("/users/forbidden/lock") ~> membershipRoute ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }
  }

  "GET user" should {
    "return a 200 response with the user info" in {
      doReturn(result(okUserResponseInfo))
        .when(membershipService)
        .getGroupByUser("ok", okAuth)
      Get("/users/ok") ~> membershipRoute ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[UserResponseInfo]
        result.id shouldBe okUserInfo.id
      }
    }

    "return a 200 response with the user info when the user ID is valid" in {
      val testUser = listOfDummyUsers.head
      doReturn(result(dummyUserResponseInfo))
        .when(membershipService)
        .getGroupByUser("dummy000", okAuth)
      Get("/users/dummy000") ~> membershipRoute ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[UserResponseInfo]
        result.id shouldBe testUser.id
      }
    }

    "return a 200 response with the user info when the username is valid" in {
      val testUser = listOfDummyUsers.head
      doReturn(result(dummyUserResponseInfo))
        .when(membershipService)
        .getGroupByUser("name-dummy000", okAuth)
      Get("/users/name-dummy000") ~> membershipRoute ~> check {
        status shouldBe StatusCodes.OK
        val result = responseAs[UserResponseInfo]
        result.id shouldBe testUser.id
      }
    }

    "return a 404 Not Found response when the userIdentifier is not a valid user ID or username" in {
      doReturn(result(UserNotFoundError("fail")))
        .when(membershipService)
        .getGroupByUser("fail", okAuth)
      Get("/users/fail") ~> membershipRoute ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }
}