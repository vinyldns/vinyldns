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
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import vinyldns.api.Interfaces._
import vinyldns.api.ResultHelpers
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.zone.ZoneRepository
import cats.effect._
import vinyldns.api.domain.zone.NotAuthorizedError
import vinyldns.core.TestMembershipData._
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.membership._
import vinyldns.core.domain.record.RecordSetRepository

class MembershipServiceSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with ResultHelpers
    with EitherMatchers {

  private val mockGroupRepo = mock[GroupRepository]
  private val mockUserRepo = mock[UserRepository]
  private val mockMembershipRepo = mock[MembershipRepository]
  private val mockZoneRepo = mock[ZoneRepository]
  private val mockGroupChangeRepo = mock[GroupChangeRepository]
  private val mockRecordSetRepo = mock[RecordSetRepository]

  private val backingService = new MembershipService(
    mockGroupRepo,
    mockUserRepo,
    mockMembershipRepo,
    mockZoneRepo,
    mockGroupChangeRepo,
    mockRecordSetRepo)
  private val underTest = spy(backingService)

  private val okUserInfo: UserInfo = UserInfo(okUser)
  private val dummyUserInfo: UserInfo = UserInfo(dummyUser)
  private val groupInfo = Group(
    name = "test-group",
    email = "test@test.com",
    description = Some("desc"),
    id = "id",
    memberIds = Set(okUserInfo.id),
    adminUserIds = Set(okUserInfo.id)
  )
  private val listOfDummyGroupsAuth: AuthPrincipal =
    AuthPrincipal(dummyUser, listOfDummyGroups.map(_.id))
  private val listOfDummyGroupInfo: List[GroupInfo] = listOfDummyGroups.map(GroupInfo.apply)

  private val existingGroup = okGroup.copy(
    id = "id",
    memberIds = Set("user1", "user2", "user3", "user4"),
    adminUserIds = Set("user1", "user2", "ok"))

  // the update will remove users 3 and 4, add users 5 and 6, as well as a new admin user 7 and remove user2 as admin
  private val updatedInfo = Group(
    name = "new.name",
    email = "new.email",
    description = Some("new desc"),
    id = "id",
    memberIds = Set("user1", "user2", "user5", "user6", "user7"),
    adminUserIds = Set("user1", "user7")
  )

  private val modifiedGroup = updatedInfo.copy(
    name = existingGroup.name,
    email = existingGroup.email,
    description = existingGroup.description,
    memberIds = existingGroup.memberIds ++ updatedInfo.memberIds,
    adminUserIds = existingGroup.adminUserIds ++ updatedInfo.memberIds
  )

  override protected def beforeEach(): Unit =
    reset(
      mockGroupRepo,
      mockUserRepo,
      mockMembershipRepo,
      mockGroupChangeRepo,
      mockRecordSetRepo,
      underTest)

  "MembershipService" should {
    "create a new group" should {
      "save the group and add the members when the group is valid" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(groupInfo.name)
        doReturn(().toResult).when(underTest).usersExist(groupInfo.memberIds)
        doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[Group])
        doReturn(IO.pure(Set(okUser.id)))
          .when(mockMembershipRepo)
          .addMembers(anyString, any[Set[String]])
        doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[GroupChange])

        val result: Group = rightResultOf(underTest.createGroup(groupInfo, okAuth).value)
        result shouldBe groupInfo

        val groupCaptor = ArgumentCaptor.forClass(classOf[Group])

        verify(mockMembershipRepo).addMembers(anyString, any[Set[String]])
        verify(mockGroupRepo).save(groupCaptor.capture())

        val savedGroup = groupCaptor.getValue
        (savedGroup.memberIds should contain).only(okUser.id)
        (savedGroup.adminUserIds should contain).only(okUser.id)
        savedGroup.name shouldBe groupInfo.name
        savedGroup.email shouldBe groupInfo.email
        savedGroup.description shouldBe groupInfo.description
      }

      "save the groupChange in the groupChangeRepo" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(groupInfo.name)
        doReturn(().toResult).when(underTest).usersExist(groupInfo.memberIds)
        doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[Group])
        doReturn(IO.pure(Set(okUser.id)))
          .when(mockMembershipRepo)
          .addMembers(anyString, any[Set[String]])
        doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[GroupChange])

        val result: Group = rightResultOf(underTest.createGroup(groupInfo, okAuth).value)
        result shouldBe groupInfo

        val groupChangeCaptor = ArgumentCaptor.forClass(classOf[GroupChange])
        verify(mockGroupChangeRepo).save(groupChangeCaptor.capture())

        val savedGroupChange = groupChangeCaptor.getValue
        savedGroupChange.userId shouldBe okUser.id
        savedGroupChange.changeType shouldBe GroupChangeType.Create
        savedGroupChange.newGroup shouldBe groupInfo
      }

      "add the admins as members of the group" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        val info = groupInfo.copy(
          memberIds = Set(okUserInfo.id, dummyUserInfo.id),
          adminUserIds = Set(okUserInfo.id, dummyUserInfo.id))
        val expectedMembersAdded = Set(okUserInfo.id, dummyUserInfo.id)

        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(info.name)
        doReturn(().toResult).when(underTest).usersExist(any[Set[String]])
        doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[Group])
        doReturn(IO.pure(Set(okUser.id)))
          .when(mockMembershipRepo)
          .addMembers(anyString, any[Set[String]])
        doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[GroupChange])

        val result: Group = rightResultOf(underTest.createGroup(info, okAuth).value)
        result shouldBe info

        val memberIdCaptor = ArgumentCaptor.forClass(classOf[Set[String]])
        verify(mockMembershipRepo).addMembers(anyString, memberIdCaptor.capture())

        val memberIdsAdded = memberIdCaptor.getValue
        memberIdsAdded should contain theSameElementsAs expectedMembersAdded
      }

      "set the current user as a member" in {
        val info = groupInfo.copy(memberIds = Set.empty, adminUserIds = Set.empty)
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(info.name)
        doReturn(().toResult).when(underTest).usersExist(Set(okAuth.userId))
        doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[Group])
        doReturn(IO.pure(Set(okUser.id)))
          .when(mockMembershipRepo)
          .addMembers(anyString, any[Set[String]])
        doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[GroupChange])

        val result: Group = rightResultOf(underTest.createGroup(info, okAuth).value)
        result.memberIds should contain(okAuth.userId)
      }

      "return an error if a group with the same name exists" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(result(GroupAlreadyExistsError("fail")))
          .when(underTest)
          .groupWithSameNameDoesNotExist(groupInfo.name)

        val error = leftResultOf(underTest.createGroup(groupInfo, okAuth).value)
        error shouldBe a[GroupAlreadyExistsError]

        verify(mockGroupRepo, never()).save(any[Group])
        verify(mockMembershipRepo, never()).addMembers(anyString, any[Set[String]])
      }

      "return an error if users do not exist" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(groupInfo.name)
        doReturn(result(UserNotFoundError("fail")))
          .when(underTest)
          .usersExist(groupInfo.memberIds)

        val error = leftResultOf(underTest.createGroup(groupInfo, okAuth).value)
        error shouldBe a[UserNotFoundError]

        verify(mockGroupRepo, never()).save(any[Group])
        verify(mockMembershipRepo, never()).addMembers(anyString, any[Set[String]])
      }

      "return an error if fail while saving the group" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(groupInfo.name)
        doReturn(().toResult).when(underTest).usersExist(groupInfo.memberIds)
        doReturn(IO.raiseError(new RuntimeException("fail"))).when(mockGroupRepo).save(any[Group])
        doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[GroupChange])

        val error = leftResultOf(underTest.createGroup(groupInfo, okAuth).value)
        error shouldBe a[RuntimeException]

        verify(mockMembershipRepo, never()).addMembers(anyString, any[Set[String]])
      }

      "return an error if fail while adding the members" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(groupInfo.name)
        doReturn(().toResult).when(underTest).usersExist(groupInfo.memberIds)
        doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[Group])
        doReturn(IO.raiseError(new RuntimeException("fail")))
          .when(mockMembershipRepo)
          .addMembers(anyString, any[Set[String]])
        doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[GroupChange])

        val error = leftResultOf(underTest.createGroup(groupInfo, okAuth).value)
        error shouldBe a[RuntimeException]
      }
    }

    "update an existing group" should {
      "save the update and add new members and remove deleted members" in {
        doReturn(IO.pure(Some(existingGroup))).when(mockGroupRepo).getGroup(any[String])
        doReturn(().toResult)
          .when(underTest)
          .differentGroupWithSameNameDoesNotExist(any[String], any[String])
        doReturn(().toResult).when(underTest).usersExist(any[Set[String]])
        doReturn(IO.pure(modifiedGroup)).when(mockGroupRepo).save(any[Group])
        doReturn(IO.pure(Set()))
          .when(mockMembershipRepo)
          .addMembers(anyString, any[Set[String]])
        doReturn(IO.pure(Set()))
          .when(mockMembershipRepo)
          .removeMembers(anyString, any[Set[String]])
        doReturn(IO.pure(okGroupChangeUpdate))
          .when(mockGroupChangeRepo)
          .save(any[GroupChange])

        awaitResultOf(
          underTest
            .updateGroup(
              updatedInfo.id,
              updatedInfo.name,
              updatedInfo.email,
              updatedInfo.description,
              updatedInfo.memberIds,
              updatedInfo.adminUserIds,
              okAuth)
            .value)

        val groupCaptor = ArgumentCaptor.forClass(classOf[Group])
        val addedMemberCaptor = ArgumentCaptor.forClass(classOf[Set[String]])
        val removedMemberCaptor = ArgumentCaptor.forClass(classOf[Set[String]])
        val groupChangeCaptor = ArgumentCaptor.forClass(classOf[GroupChange])

        verify(mockGroupRepo).save(groupCaptor.capture())
        verify(mockMembershipRepo).addMembers(anyString, addedMemberCaptor.capture())
        verify(mockMembershipRepo).removeMembers(anyString, removedMemberCaptor.capture())
        verify(mockGroupChangeRepo).save(groupChangeCaptor.capture())

        val expectedMembers = Set("user1", "user2", "user5", "user6", "user7")
        val expectedAdmins = Set("user1", "user7")
        val savedGroup = groupCaptor.getValue
        savedGroup.name shouldBe updatedInfo.name
        savedGroup.email shouldBe updatedInfo.email
        savedGroup.description shouldBe updatedInfo.description
        savedGroup.memberIds should contain theSameElementsAs expectedMembers
        savedGroup.adminUserIds should contain theSameElementsAs expectedAdmins
        savedGroup.created shouldBe existingGroup.created

        val expectedAddedMembers = Set("user5", "user6", "user7")
        val expectedRemovedMembers = Set("user3", "user4")

        val addedMembers = addedMemberCaptor.getValue
        addedMembers should contain theSameElementsAs expectedAddedMembers

        val removedMembers = removedMemberCaptor.getValue
        removedMembers should contain theSameElementsAs expectedRemovedMembers

        val groupChange = groupChangeCaptor.getValue
        groupChange.changeType shouldBe GroupChangeType.Update
        groupChange.newGroup shouldBe savedGroup
        groupChange.oldGroup shouldBe Some(existingGroup)
        groupChange.userId shouldBe okAuth.userId
      }

      "return an error if the user is not an admin" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)

        val error = leftResultOf(
          underTest
            .updateGroup(
              updatedInfo.id,
              updatedInfo.name,
              updatedInfo.email,
              updatedInfo.description,
              updatedInfo.memberIds,
              updatedInfo.adminUserIds,
              dummyAuth)
            .value)

        error shouldBe a[NotAuthorizedError]
      }

      "return an error if a group with the same name already exists" in {
        doReturn(IO.pure(Some(existingGroup)))
          .when(mockGroupRepo)
          .getGroup(existingGroup.id)
        doReturn(().toResult).when(underTest).usersExist(any[Set[String]])
        doReturn(result(GroupAlreadyExistsError("fail")))
          .when(underTest)
          .differentGroupWithSameNameDoesNotExist(updatedInfo.name, existingGroup.id)

        val error = leftResultOf(
          underTest
            .updateGroup(
              updatedInfo.id,
              updatedInfo.name,
              updatedInfo.email,
              updatedInfo.description,
              updatedInfo.memberIds,
              updatedInfo.adminUserIds,
              okAuth)
            .value)
        error shouldBe a[GroupAlreadyExistsError]
      }

      "return an error if the group is not found" in {
        doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(existingGroup.id)

        val error = leftResultOf(
          underTest
            .updateGroup(
              updatedInfo.id,
              updatedInfo.name,
              updatedInfo.email,
              updatedInfo.description,
              updatedInfo.memberIds,
              updatedInfo.adminUserIds,
              okAuth)
            .value)
        error shouldBe a[GroupNotFoundError]
      }

      "return an error if the users do not exist" in {
        doReturn(IO.pure(Some(existingGroup)))
          .when(mockGroupRepo)
          .getGroup(existingGroup.id)
        doReturn(result(()))
          .when(underTest)
          .differentGroupWithSameNameDoesNotExist(updatedInfo.name, existingGroup.id)
        doReturn(result(UserNotFoundError("fail")))
          .when(underTest)
          .usersExist(any[Set[String]])

        val error = leftResultOf(
          underTest
            .updateGroup(
              updatedInfo.id,
              updatedInfo.name,
              updatedInfo.email,
              updatedInfo.description,
              updatedInfo.memberIds,
              updatedInfo.adminUserIds,
              okAuth)
            .value)
        error shouldBe a[UserNotFoundError]
      }

      "return an error if the group has no members or admins" in {
        doReturn(IO.pure(Some(existingGroup)))
          .when(mockGroupRepo)
          .getGroup(existingGroup.id)

        val error = leftResultOf(
          underTest
            .updateGroup(
              updatedInfo.id,
              updatedInfo.name,
              updatedInfo.email,
              updatedInfo.description,
              Set(),
              Set(),
              okAuth)
            .value)
        error shouldBe an[InvalidGroupError]
      }
    }

    "delete a group" should {
      "return the deleted group with a status of Deleted" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
        doReturn(IO.pure(okGroup)).when(mockGroupRepo).delete(any[Group])
        doReturn(IO.pure(List())).when(mockZoneRepo).getZonesByAdminGroupId(anyString)
        doReturn(IO.pure(okGroupChangeDelete))
          .when(mockGroupChangeRepo)
          .save(any[GroupChange])
        doReturn(IO.pure(Set[String]()))
          .when(mockMembershipRepo)
          .removeMembers(anyString, any[Set[String]])
        doReturn(IO.pure(None))
          .when(mockRecordSetRepo)
          .getFirstOwnedRecordByGroup(anyString)
        doReturn(IO.pure(None))
          .when(mockZoneRepo)
          .getFirstOwnedZoneAclGroupId(anyString())

        val result: Group = rightResultOf(underTest.deleteGroup("ok", okAuth).value)
        result shouldBe okGroup.copy(status = GroupStatus.Deleted)

        val groupCaptor = ArgumentCaptor.forClass(classOf[Group])
        val groupChangeCaptor = ArgumentCaptor.forClass(classOf[GroupChange])
        verify(mockGroupRepo).delete(groupCaptor.capture())
        verify(mockGroupChangeRepo).save(groupChangeCaptor.capture())
        verify(mockMembershipRepo).removeMembers(okGroup.id, okGroup.memberIds)

        val savedGroup = groupCaptor.getValue
        savedGroup.status shouldBe GroupStatus.Deleted

        val savedGroupChange = groupChangeCaptor.getValue
        savedGroupChange.changeType shouldBe GroupChangeType.Delete
        savedGroupChange.userId shouldBe okAuth.userId
        savedGroupChange.newGroup shouldBe okGroup
      }

      "return an error if the user is not an admin" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)

        val error = leftResultOf(underTest.deleteGroup("ok", dummyAuth).value)

        error shouldBe a[NotAuthorizedError]
      }

      "return an error if the group is not found" in {
        doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(anyString)
        doReturn(IO.pure(List())).when(mockZoneRepo).getZonesByAdminGroupId(anyString)

        val error = leftResultOf(underTest.deleteGroup("ok", okAuth).value)

        error shouldBe a[GroupNotFoundError]
      }

      "return an error if the group is the admin group of a zone" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
        doReturn(IO.pure(List(zoneActive)))
          .when(mockZoneRepo)
          .getZonesByAdminGroupId(anyString)

        val error = leftResultOf(underTest.deleteGroup("ok", okAuth).value)

        error shouldBe an[InvalidGroupRequestError]
      }

      "return an error if the group is an owner for a record set" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
        doReturn(IO.pure(Some("somerecordsetid")))
          .when(mockRecordSetRepo)
          .getFirstOwnedRecordByGroup(anyString())
        val error = leftResultOf(underTest.deleteGroup("ok", okAuth).value)

        error shouldBe an[InvalidGroupRequestError]
      }

      "return an error if the group has an ACL rule on a zone" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
        doReturn(IO.pure(Some("someId")))
          .when(mockZoneRepo)
          .getFirstOwnedZoneAclGroupId(anyString())
        val error = leftResultOf(underTest.deleteGroup("ok", okAuth).value)

        error shouldBe an[InvalidGroupRequestError]
      }

    }

    "get a group" should {
      "return the group" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
        val result: Group = rightResultOf(underTest.getGroup(okGroup.id, okAuth).value)
        result shouldBe okGroup
      }

      "return an error if not authorized" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
        val error = leftResultOf(underTest.getGroup(okGroup.id, dummyAuth).value)
        error shouldBe a[NotAuthorizedError]
      }

      "return an error if the group is not found" in {
        doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(anyString)
        val error = leftResultOf(underTest.getGroup("notfound", okAuth).value)
        error shouldBe a[GroupNotFoundError]
      }
    }

    "get my groups" should {
      "return all the groups where user is a member when no optional parameters are used" in {
        doReturn(IO.pure(listOfDummyGroups.toSet))
          .when(mockGroupRepo)
          .getGroups(any[Set[String]])
        val result: ListMyGroupsResponse =
          rightResultOf(underTest.listMyGroups(None, None, 100, listOfDummyGroupsAuth, false).value)
        verify(mockGroupRepo, never()).getAllGroups()
        result shouldBe ListMyGroupsResponse(
          groups = listOfDummyGroupInfo.take(100),
          None,
          None,
          nextId = Some(listOfDummyGroups(99).id),
          maxItems = 100,
          ignoreAccess = false)
      }
      "return only return groups whose name matches the filter" in {
        doReturn(IO.pure(listOfDummyGroups.toSet))
          .when(mockGroupRepo)
          .getGroups(any[Set[String]])
        val result: ListMyGroupsResponse = rightResultOf(
          underTest
            .listMyGroups(
              groupNameFilter = Some("name-dummy01"),
              startFrom = None,
              maxItems = 100,
              listOfDummyGroupsAuth,
              false)
            .value)
        result shouldBe ListMyGroupsResponse(
          groups = listOfDummyGroupInfo.slice(10, 20),
          groupNameFilter = Some("name-dummy01"),
          startFrom = None,
          nextId = None,
          maxItems = 100,
          ignoreAccess = false)
      }
      "return only return groups after startFrom" in {
        doReturn(IO.pure(listOfDummyGroups.toSet))
          .when(mockGroupRepo)
          .getGroups(any[Set[String]])
        val result: ListMyGroupsResponse = rightResultOf(
          underTest
            .listMyGroups(
              groupNameFilter = None,
              startFrom = Some(listOfDummyGroups(99).id),
              maxItems = 100,
              listOfDummyGroupsAuth,
              ignoreAccess = false)
            .value)
        result shouldBe ListMyGroupsResponse(
          groups = listOfDummyGroupInfo.slice(100, 200),
          groupNameFilter = None,
          startFrom = Some(listOfDummyGroups(99).id),
          nextId = None,
          maxItems = 100,
          ignoreAccess = false)
      }
      "return only return maxItems groups" in {
        doReturn(IO.pure(listOfDummyGroups.toSet))
          .when(mockGroupRepo)
          .getGroups(any[Set[String]])
        val result: ListMyGroupsResponse = rightResultOf(
          underTest
            .listMyGroups(
              groupNameFilter = None,
              startFrom = None,
              maxItems = 10,
              listOfDummyGroupsAuth,
              ignoreAccess = false)
            .value)
        result shouldBe ListMyGroupsResponse(
          groups = listOfDummyGroupInfo.slice(0, 10),
          groupNameFilter = None,
          startFrom = None,
          nextId = Some(listOfDummyGroups(9).id),
          maxItems = 10,
          ignoreAccess = false)
      }
      "return an empty set if the user is not a member of any groups" in {
        doReturn(IO.pure(Set())).when(mockGroupRepo).getGroups(any[Set[String]])
        val result: ListMyGroupsResponse =
          rightResultOf(underTest.listMyGroups(None, None, 100, notAuth, false).value)
        result shouldBe ListMyGroupsResponse(Seq(), None, None, None, 100, false)
      }
      "return groups from the database for super users" in {
        doReturn(IO.pure(Set(okGroup, dummyGroup))).when(mockGroupRepo).getAllGroups()
        val result: ListMyGroupsResponse =
          rightResultOf(underTest.listMyGroups(None, None, 100, superUserAuth, true).value)
        verify(mockGroupRepo).getAllGroups()
        result.groups should contain theSameElementsAs Seq(
          GroupInfo(dummyGroup),
          GroupInfo(okGroup))
      }
      "return groups from the database for support users" in {
        val supportAuth = AuthPrincipal(okUser.copy(isSupport = true), Seq())
        doReturn(IO.pure(Set(okGroup, dummyGroup))).when(mockGroupRepo).getAllGroups()
        val result: ListMyGroupsResponse =
          rightResultOf(underTest.listMyGroups(None, None, 100, supportAuth, true).value)
        verify(mockGroupRepo).getAllGroups()
        result.groups should contain theSameElementsAs Seq(
          GroupInfo(dummyGroup),
          GroupInfo(okGroup))
      }
      "do not return deleted groups" in {
        val deletedGroupAuth: AuthPrincipal = AuthPrincipal(okUser, Seq(deletedGroup.id))
        doReturn(IO.pure(Set(deletedGroup)))
          .when(mockGroupRepo)
          .getGroups(any[Set[String]])
        val result: ListMyGroupsResponse =
          rightResultOf(underTest.listMyGroups(None, None, 100, deletedGroupAuth, false).value)
        result shouldBe ListMyGroupsResponse(Seq(), None, None, None, 100, false)
      }
    }

    "getGroupActivity" should {
      "return the group activity" in {
        val groupChangeRepoResponse = ListGroupChangesResults(
          listOfDummyGroupChanges.take(100),
          Some(listOfDummyGroupChanges(100).id))
        doReturn(IO.pure(groupChangeRepoResponse))
          .when(mockGroupChangeRepo)
          .getGroupChanges(anyString, any[Option[String]], anyInt)

        val expected: List[GroupChangeInfo] =
          listOfDummyGroupChanges.map(GroupChangeInfo.apply).take(100)

        val result: ListGroupChangesResponse =
          rightResultOf(underTest.getGroupActivity(dummyGroup.id, None, 100, dummyAuth).value)
        result.changes should contain theSameElementsAs expected
        result.maxItems shouldBe 100
        result.nextId shouldBe Some(listOfDummyGroupChanges(100).id)
        result.startFrom shouldBe None
      }
      "return an error if the user is not authorized" in {
        val error =
          leftResultOf(underTest.getGroupActivity("notFound", None, 100, okAuth).value)
        error shouldBe a[NotAuthorizedError]
      }
    }

    "listAdmins" should {
      "return a list of admins" in {
        val testGroup =
          okGroup.copy(memberIds = Set(okUser.id, dummyUser.id), adminUserIds = Set(okUser.id))
        val testListUsersResult = ListUsersResults(Seq(okUser), Some("1"))
        val expectedAdmins = List(UserInfo(okUser))

        doReturn(IO.pure(Some(testGroup))).when(mockGroupRepo).getGroup(testGroup.id)
        doReturn(IO.pure(testListUsersResult))
          .when(mockUserRepo)
          .getUsers(testGroup.adminUserIds, None, None)

        val result: ListAdminsResponse =
          rightResultOf(underTest.listAdmins(testGroup.id, okAuth).value)
        result.admins should contain theSameElementsAs expectedAdmins
      }

      "return an error if the user is not authorized" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
        val error = leftResultOf(underTest.listAdmins("notFound", okAuth).value)
        error shouldBe a[NotAuthorizedError]
      }
    }

    "listMembers" should {
      "return a list of members" in {
        // make sure that the ok user is the admin user, would be an admin member
        val testGroup =
          okGroup.copy(memberIds = Set(okUser.id, dummyUser.id), adminUserIds = Set(okUser.id))
        val testUsers = Seq(okUser, dummyUser)
        val testListUsersResult = ListUsersResults(testUsers, Some("1"))
        val expectedMembers = List(MemberInfo(okUser, okGroup), MemberInfo(dummyUser, dummyGroup))
        val testAuth = AuthPrincipal(okUser, Seq(testGroup.id))

        doReturn(IO.pure(Some(testGroup))).when(mockGroupRepo).getGroup(testGroup.id)
        doReturn(IO.pure(testListUsersResult))
          .when(mockUserRepo)
          .getUsers(testGroup.memberIds, None, Some(100))

        val result: ListMembersResponse =
          rightResultOf(underTest.listMembers(testGroup.id, None, 100, testAuth).value)

        result.members should contain theSameElementsAs expectedMembers
        result.nextId shouldBe testListUsersResult.lastEvaluatedId
        result.maxItems shouldBe 100
        result.startFrom shouldBe None
      }

      "return a list of members if the requesting user is a support admin" in {
        val testGroup =
          okGroup.copy(memberIds = Set(okUser.id, dummyUser.id), adminUserIds = Set(okUser.id))
        val testUsers = Seq(okUser, dummyUser)
        val testListUsersResult = ListUsersResults(testUsers, Some("1"))
        val expectedMembers = List(MemberInfo(okUser, okGroup), MemberInfo(dummyUser, dummyGroup))
        val supportAuth = okAuth.copy(
          signedInUser = dummyAuth.signedInUser.copy(isSupport = true),
          memberGroupIds = Seq.empty)

        doReturn(IO.pure(Some(testGroup))).when(mockGroupRepo).getGroup(testGroup.id)
        doReturn(IO.pure(testListUsersResult))
          .when(mockUserRepo)
          .getUsers(testGroup.memberIds, None, Some(100))

        val result: ListMembersResponse =
          rightResultOf(underTest.listMembers(testGroup.id, None, 100, supportAuth).value)

        result.members should contain theSameElementsAs expectedMembers
        result.nextId shouldBe testListUsersResult.lastEvaluatedId
        result.maxItems shouldBe 100
        result.startFrom shouldBe None
      }

      "return an error if the user is not authorized" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
        val error = leftResultOf(underTest.listMembers("notFound", None, 100, okAuth).value)
        error shouldBe a[NotAuthorizedError]
      }
    }

    "groupWithSameNameDoesNotExist" should {
      "return true when a group with the same name does not exist" in {
        doReturn(IO.pure(None)).when(mockGroupRepo).getGroupByName("foo")

        val result = awaitResultOf(underTest.groupWithSameNameDoesNotExist("foo").value)
        result should be(right)
      }

      "return a GroupAlreadyExistsError if a group with the same name already exists" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroupByName("foo")

        val result = leftResultOf(underTest.groupWithSameNameDoesNotExist("foo").value)
        result shouldBe a[GroupAlreadyExistsError]
      }

      "return true if a group with the same name exists but is deleted" in {
        doReturn(IO.pure(Some(deletedGroup))).when(mockGroupRepo).getGroupByName("foo")

        val result = awaitResultOf(underTest.groupWithSameNameDoesNotExist("foo").value)
        result should be(right)
      }
    }

    "usersExist" should {
      "return a () if all users exist" in {
        doReturn(IO.pure(ListUsersResults(Seq(okUser), None)))
          .when(mockUserRepo)
          .getUsers(okGroup.memberIds, None, None)

        val result = awaitResultOf(underTest.usersExist(okGroup.memberIds).value)
        result should be(right)
      }

      "return UserNotFoundError if any of the requested users were not found" in {
        doReturn(IO.pure(ListUsersResults(Seq(okUser), None)))
          .when(mockUserRepo)
          .getUsers(Set(okUser.id, dummyUser.id), None, None)

        val result = leftResultOf(underTest.usersExist(Set(okUser.id, dummyUser.id)).value)
        result shouldBe a[UserNotFoundError]
      }
    }

    "differentGroupWithSameNameDoesNotExist" should {
      "return GroupAlreadyExistsError if a different group with the same name already exists" in {
        val existingGroup = okGroup.copy(id = "something else")

        doReturn(IO.pure(Some(existingGroup))).when(mockGroupRepo).getGroupByName("foo")

        val error =
          leftResultOf(underTest.differentGroupWithSameNameDoesNotExist("foo", "bar").value)
        error shouldBe a[GroupAlreadyExistsError]
      }

      "return true if the same group exists with the same name" in {

        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroupByName(okGroup.name)

        val result = awaitResultOf(
          underTest.differentGroupWithSameNameDoesNotExist(okGroup.name, okGroup.id).value)
        result should be(right)
      }

      "return true if the a different group exists but is deleted" in {
        val existingGroup = okGroup.copy(id = "something else", status = GroupStatus.Deleted)

        doReturn(IO.pure(Some(existingGroup)))
          .when(mockGroupRepo)
          .getGroupByName(okGroup.name)

        val result = awaitResultOf(
          underTest.differentGroupWithSameNameDoesNotExist(okGroup.name, okGroup.id).value)
        result should be(right)
      }
    }

    "isNotZoneAdmin" should {
      "return true when a group for deletion is not the admin of a zone" in {
        doReturn(IO.pure(List())).when(mockZoneRepo).getZonesByAdminGroupId(okGroup.id)

        val result = awaitResultOf(underTest.isNotZoneAdmin(okGroup).value)
        result should be(right)
      }

      "return an InvalidGroupRequestError when a group for deletion is admin of a zone" in {
        doReturn(IO.pure(List(zoneActive)))
          .when(mockZoneRepo)
          .getZonesByAdminGroupId(okGroup.id)

        val error = leftResultOf(underTest.isNotZoneAdmin(okGroup).value)
        error shouldBe an[InvalidGroupRequestError]
      }
    }

    "isNotRecordOwnerGroup" should {
      "return true when a group for deletion is not the admin of a zone" in {
        doReturn(IO.pure(None)).when(mockRecordSetRepo).getFirstOwnedRecordByGroup(okGroup.id)

        val result = awaitResultOf(underTest.isNotRecordOwnerGroup(okGroup).value)
        result should be(right)
      }

      "return an InvalidGroupRequestError when a group for deletion is admin of a zone" in {
        doReturn(IO.pure(Some("somerecordsetid")))
          .when(mockRecordSetRepo)
          .getFirstOwnedRecordByGroup(okGroup.id)

        val error = leftResultOf(underTest.isNotRecordOwnerGroup(okGroup).value)
        error shouldBe an[InvalidGroupRequestError]
      }
    }

    "isNotZoneAclGroupId" should {
      "return successfully when a groupId is not in any zone ACL" in {
        doReturn(IO.pure(None)).when(mockZoneRepo).getFirstOwnedZoneAclGroupId(okGroup.id)

        val result = awaitResultOf(underTest.isNotInZoneAclRule(okGroup).value)
        result should be(right)
      }

      "return an InvalidGroupRequestError when a group has an ACL rule in a zone" in {
        doReturn(IO.pure(Some("someZoneId")))
          .when(mockZoneRepo)
          .getFirstOwnedZoneAclGroupId(okGroup.id)

        val error = leftResultOf(underTest.isNotInZoneAclRule(okGroup).value)
        error shouldBe an[InvalidGroupRequestError]
      }
    }

    "updateUserLockStatus" should {
      "save the update and lock the user account" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser(okUser.id)
        doReturn(IO.pure(okUser)).when(mockUserRepo).save(any[User])

        underTest
          .updateUserLockStatus(okUser.id, LockStatus.Locked, superUserAuth)
          .value
          .unsafeRunSync()

        val userCaptor = ArgumentCaptor.forClass(classOf[User])

        verify(mockUserRepo).save(userCaptor.capture())

        val savedUser = userCaptor.getValue
        savedUser.lockStatus shouldBe LockStatus.Locked
        savedUser.id shouldBe okUser.id
      }

      "save the update and unlock the user account" in {
        doReturn(IO.pure(Some(lockedUser))).when(mockUserRepo).getUser(lockedUser.id)
        doReturn(IO.pure(okUser)).when(mockUserRepo).save(any[User])

        underTest
          .updateUserLockStatus(lockedUser.id, LockStatus.Unlocked, superUserAuth)
          .value
          .unsafeRunSync()

        val userCaptor = ArgumentCaptor.forClass(classOf[User])

        verify(mockUserRepo).save(userCaptor.capture())

        val savedUser = userCaptor.getValue
        savedUser.lockStatus shouldBe LockStatus.Unlocked
        savedUser.id shouldBe lockedUser.id
      }

      "return an error if the signed in user is not a super user" in {
        val error = leftResultOf(
          underTest
            .updateUserLockStatus(okUser.id, LockStatus.Locked, dummyAuth)
            .value)

        error shouldBe a[NotAuthorizedError]
      }

      "return an error if the signed in user is only a support admin" in {
        val supportAuth = okAuth.copy(
          signedInUser = dummyAuth.signedInUser.copy(isSupport = true),
          memberGroupIds = Seq.empty)
        val error = leftResultOf(
          underTest
            .updateUserLockStatus(okUser.id, LockStatus.Locked, supportAuth)
            .value)

        error shouldBe a[NotAuthorizedError]
      }

      "return an error if the requested user is not found" in {
        doReturn(IO.pure(None)).when(mockUserRepo).getUser(okUser.id)

        val error = leftResultOf(
          underTest
            .updateUserLockStatus(okUser.id, LockStatus.Locked, superUserAuth)
            .value)

        error shouldBe a[UserNotFoundError]
      }
    }
  }
}
