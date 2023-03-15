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
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import vinyldns.api.Interfaces._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.zone.ZoneRepository
import cats.effect._
import scalikejdbc.{ConnectionPool, DB}
import vinyldns.api.config.ValidEmailConfig
import vinyldns.api.domain.zone.NotAuthorizedError
import vinyldns.core.TestMembershipData._
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.membership._
import vinyldns.core.domain.record.RecordSetRepository

class MembershipServiceSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with EitherMatchers {

  private val mockGroupRepo = mock[GroupRepository]
  private val mockUserRepo = mock[UserRepository]
  private val mockMembershipRepo = mock[MembershipRepository]
  private val mockZoneRepo = mock[ZoneRepository]
  private val mockGroupChangeRepo = mock[GroupChangeRepository]
  private val mockRecordSetRepo = mock[RecordSetRepository]
  private val mockValidEmailConfig = ValidEmailConfig(valid_domains = List("test.com","*dummy.com"))
  private val mockValidEmailConfigNew = ValidEmailConfig(valid_domains = List())

  private val backingService = new MembershipService(
    mockGroupRepo,
    mockUserRepo,
    mockMembershipRepo,
    mockZoneRepo,
    mockGroupChangeRepo,
    mockRecordSetRepo,
    mockValidEmailConfig
  )
  private val backingServiceNew = new MembershipService(
    mockGroupRepo,
    mockUserRepo,
    mockMembershipRepo,
    mockZoneRepo,
    mockGroupChangeRepo,
    mockRecordSetRepo,
    mockValidEmailConfigNew
  )
  private val underTest = spy(backingService)
  private val underTestNew = spy(backingServiceNew)

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
    adminUserIds = Set("user1", "user2", "ok")
  )

  // the update will remove users 3 and 4, add users 5 and 6, as well as a new admin user 7 and remove user2 as admin
  private val updatedInfo = Group(
    name = "new.name",
    email = "test@test.com",
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
      underTest
    )
  // Add connection to run tests
  ConnectionPool.add('default, "jdbc:h2:mem:vinyldns;MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;IGNORECASE=TRUE;INIT=RUNSCRIPT FROM 'classpath:test/ddl.sql'","sa","")
  "MembershipService" should {
    "create a new group" should {
      "save the group and add the members when the group is valid" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(().toResult).when(underTest).groupValidation(groupInfo)
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(groupInfo.name)
        doReturn(().toResult).when(underTest).usersExist(groupInfo.memberIds)
        doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[DB], any[Group])
        doReturn(IO.pure(Set(okUser.id)))
          .when(mockMembershipRepo)
          .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
        doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[DB], any[GroupChange])

        val result: Group = underTest.createGroup(groupInfo, okAuth).value.unsafeRunSync().toOption.get
        result shouldBe groupInfo

        val groupCaptor = ArgumentCaptor.forClass(classOf[Group])

        verify(mockMembershipRepo, times(2))
          .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
        verify(mockGroupRepo).save(any[DB], groupCaptor.capture())

        val savedGroup = groupCaptor.getValue
        (savedGroup.memberIds should contain).only(okUser.id)
        (savedGroup.adminUserIds should contain).only(okUser.id)
        savedGroup.name shouldBe groupInfo.name
        savedGroup.email shouldBe groupInfo.email
        savedGroup.description shouldBe groupInfo.description
      }

      "save the groupChange in the groupChangeRepo" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(().toResult).when(underTest).groupValidation(groupInfo)
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(groupInfo.name)
        doReturn(().toResult).when(underTest).usersExist(groupInfo.memberIds)
        doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[DB], any[Group])
        doReturn(IO.pure(Set(okUser.id)))
          .when(mockMembershipRepo)
          .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
        doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[DB], any[GroupChange])

        val result: Group = underTest.createGroup(groupInfo, okAuth).value.unsafeRunSync().toOption.get
        result shouldBe groupInfo

        val groupChangeCaptor = ArgumentCaptor.forClass(classOf[GroupChange])
        verify(mockGroupChangeRepo).save(any[DB], groupChangeCaptor.capture())

        val savedGroupChange = groupChangeCaptor.getValue
        savedGroupChange.userId shouldBe okUser.id
        savedGroupChange.changeType shouldBe GroupChangeType.Create
        savedGroupChange.newGroup shouldBe groupInfo
      }

      "add the admins as members of the group" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        val info = groupInfo.copy(
          memberIds = Set(okUserInfo.id, dummyUserInfo.id),
          adminUserIds = Set(okUserInfo.id, dummyUserInfo.id)
        )
        val expectedMembersAdded = Set(okUserInfo.id, dummyUserInfo.id)
        doReturn(().toResult).when(underTest).groupValidation(info)
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(info.name)
        doReturn(().toResult).when(underTest).usersExist(any[Set[String]])
        doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[DB], any[Group])
        when(
          mockMembershipRepo
            .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
        ).thenReturn(IO.pure(expectedMembersAdded))
        doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[DB], any[GroupChange])

        val result: Group = underTest.createGroup(info, okAuth).value.unsafeRunSync().toOption.get
        result shouldBe info

        val memberIdCaptor = ArgumentCaptor.forClass(classOf[Set[String]])
        verify(mockMembershipRepo, times(2)).saveMembers(
          any[DB],
          anyString,
          memberIdCaptor.capture(),
          isAdmin = anyBoolean
        )

        val memberIdsAdded = memberIdCaptor.getAllValues
        memberIdsAdded should contain(expectedMembersAdded)
      }

      "set the current user as a member" in {
        val info = groupInfo.copy(memberIds = Set.empty, adminUserIds = Set.empty)
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(().toResult).when(underTest).groupValidation(info)
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(info.name)
        doReturn(().toResult).when(underTest).usersExist(Set(okAuth.userId))
        doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[DB], any[Group])
        doReturn(IO.pure(Set(okUser.id)))
          .when(mockMembershipRepo)
          .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
        doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[DB], any[GroupChange])

        val result: Group = underTest.createGroup(info, okAuth).value.unsafeRunSync().toOption.get
        result.memberIds should contain(okAuth.userId)
      }

      "return an error if a group with the same name exists" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(result(GroupAlreadyExistsError("fail")))
          .when(underTest)
          .groupWithSameNameDoesNotExist(groupInfo.name)

        val error = underTest.createGroup(groupInfo, okAuth).value.unsafeRunSync().swap.toOption.get
        error shouldBe a[GroupAlreadyExistsError]

        verify(mockGroupRepo, never()).save(any[DB], any[Group])
        verify(mockMembershipRepo, never())
          .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
      }

      "return an error if users do not exist" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(().toResult).when(underTest).groupValidation(groupInfo)
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(groupInfo.name)
        doReturn(result(UserNotFoundError("fail")))
          .when(underTest)
          .usersExist(groupInfo.memberIds)

        val error = underTest.createGroup(groupInfo, okAuth).value.unsafeRunSync().swap.toOption.get
        error shouldBe a[UserNotFoundError]

        verify(mockGroupRepo, never()).save(any[DB], any[Group])
        verify(mockMembershipRepo, never())
          .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
      }

      "return an error if fail while saving the group" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(().toResult).when(underTest).groupValidation(groupInfo)
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(groupInfo.name)
        doReturn(().toResult).when(underTest).usersExist(groupInfo.memberIds)
        doReturn(IO.raiseError(new RuntimeException("fail"))).when(mockGroupRepo).save(any[DB], any[Group])
        doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[DB], any[GroupChange])

        val error = underTest.createGroup(groupInfo, okAuth).value.unsafeRunSync().swap.toOption.get
        error shouldBe a[RuntimeException]

        verify(mockMembershipRepo, never())
          .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
      }

      "return an error if fail while adding the members" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(().toResult).when(underTest).groupValidation(groupInfo)
        doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(groupInfo.name)
        doReturn(().toResult).when(underTest).usersExist(groupInfo.memberIds)
        doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[DB], any[Group])
        doReturn(IO.raiseError(new RuntimeException("fail")))
          .when(mockMembershipRepo)
          .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
        doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[DB], any[GroupChange])

        val error = underTest.createGroup(groupInfo, okAuth).value.unsafeRunSync().swap.toOption.get
        error shouldBe a[RuntimeException]
      }

      "return an error if group name and/or email is empty" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
        doReturn(result(GroupValidationError("fail")))
          .when(underTest)
          .groupValidation(groupInfo.copy(name = "", email = ""))

        val error = underTest.createGroup(groupInfo.copy(name = "", email = ""), okAuth).value.unsafeRunSync().swap.toOption.get
        error shouldBe a[GroupValidationError]

        verify(mockGroupRepo, never()).save(any[DB], any[Group])
        verify(mockMembershipRepo, never())
          .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
      }

      "return an error if an invalid domain is entered" in {
        val error = underTest.createGroup(groupInfo.copy(email = "test@ok.com"), okAuth).value.unsafeRunSync().swap.toOption.get
        error shouldBe a[EmailValidationError]
      }

      "return an error if an invalid email is entered" in {
        val error = underTest.createGroup(groupInfo.copy(email = "test.ok.com"), okAuth).value.unsafeRunSync().swap.toOption.get
        error shouldBe a[EmailValidationError]
      }

      "return an error if an invalid email with * is entered" in {
        val error = underTest.createGroup(groupInfo.copy(email = "test@*dummy.com"), okAuth).value.unsafeRunSync().swap.toOption.get
        error shouldBe a[EmailValidationError]
      }
    }

    "return an error if an email is invalid test case 1" in {
      val error = underTest.emailValidation(email = "test.ok.com").value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if a domain is invalid test case 1" in {
      val error = underTest.emailValidation(email = "test@ok.com").value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 2" in {
      val error = underTest.emailValidation(email = "test@.@.test.com").value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 3" in {
      val error = underTest.emailValidation(email = "test@.@@.test.com").value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 4" in {
      val error = underTest.emailValidation(email = "@te@st@test.com").value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 5" in {
      val error = underTest.emailValidation(email = ".test@test.com").value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 6" in {
      val error = underTest.emailValidation(email = "te.....st@test.com").value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "return an error if an email is invalid test case 7" in {
      val error = underTest.emailValidation(email = "test@test.com.").value.unsafeRunSync().swap.toOption.get
      error shouldBe a[EmailValidationError]
    }

    "Check whether *dummy.com is a valid email" in {
      val result = underTest.emailValidation(email = "test@ok.dummy.com").value.unsafeRunSync()
      result shouldBe Right(())
    }

    "Check whether test.com is a valid email" in {
      val result = underTest.emailValidation(email = "test@test.com").value.unsafeRunSync()
      result shouldBe Right(())
    }

    "Check whether it is allowing any domain when the config is empty" in {
      val result = underTestNew.emailValidation(email = "test@abc.com").value.unsafeRunSync()
      result shouldBe Right(())
    }

    "Create Group when email has domain *dummy.com" in {
      doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
      doReturn(().toResult).when(underTest).groupValidation(groupInfo)
      doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(groupInfo.name)
      doReturn(().toResult).when(underTest).usersExist(groupInfo.memberIds)
      doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[DB], any[Group])
      doReturn(IO.pure(Set(okUser.id)))
        .when(mockMembershipRepo)
        .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
      doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[DB], any[GroupChange])

      val result = underTest.createGroup(groupInfo.copy(email = "test@ok.dummy.com"), okAuth).value.unsafeRunSync().toOption.get
      result shouldBe groupInfo.copy(email = "test@ok.dummy.com")

      val groupCaptor = ArgumentCaptor.forClass(classOf[Group])

      verify(mockMembershipRepo, times(2))
        .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
      verify(mockGroupRepo).save(any[DB], groupCaptor.capture())

      val savedGroup = groupCaptor.getValue
      (savedGroup.memberIds should contain).only(okUser.id)
      (savedGroup.adminUserIds should contain).only(okUser.id)
      savedGroup.name shouldBe groupInfo.name
      savedGroup.email shouldBe groupInfo.copy(email = "test@ok.dummy.com").email
      savedGroup.description shouldBe groupInfo.description
  }

    "Create Group when email with any domain when config is empty" in {
      doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
      doReturn(().toResult).when(underTestNew).groupValidation(groupInfo)
      doReturn(().toResult).when(underTestNew).groupWithSameNameDoesNotExist(groupInfo.name)
      doReturn(().toResult).when(underTestNew).usersExist(groupInfo.memberIds)
      doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[DB], any[Group])
      doReturn(IO.pure(Set(okUser.id)))
        .when(mockMembershipRepo)
        .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
      doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[DB], any[GroupChange])

      val result = underTestNew.createGroup(groupInfo.copy(email = "test@abc.com"), okAuth).value.unsafeRunSync().toOption.get
      result shouldBe groupInfo.copy(email = "test@abc.com")

      val groupCaptor = ArgumentCaptor.forClass(classOf[Group])

      verify(mockMembershipRepo, times(2))
        .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
      verify(mockGroupRepo).save(any[DB], groupCaptor.capture())

      val savedGroup = groupCaptor.getValue
      (savedGroup.memberIds should contain).only(okUser.id)
      (savedGroup.adminUserIds should contain).only(okUser.id)
      savedGroup.name shouldBe groupInfo.name
      savedGroup.email shouldBe groupInfo.copy(email = "test@abc.com").email
      savedGroup.description shouldBe groupInfo.description
    }

    "Create Group when email has domain test.com" in {
    doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUser("ok")
    doReturn(().toResult).when(underTest).groupValidation(groupInfo)
    doReturn(().toResult).when(underTest).groupWithSameNameDoesNotExist(groupInfo.name)
    doReturn(().toResult).when(underTest).usersExist(groupInfo.memberIds)
    doReturn(IO.pure(okGroup)).when(mockGroupRepo).save(any[DB], any[Group])
    doReturn(IO.pure(Set(okUser.id)))
      .when(mockMembershipRepo)
      .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
    doReturn(IO.pure(okGroupChange)).when(mockGroupChangeRepo).save(any[DB], any[GroupChange])

      val result = underTest.createGroup(groupInfo.copy(email = "test@test.com"), okAuth).value.unsafeRunSync().toOption.get
      result shouldBe groupInfo.copy(email = "test@test.com")

    val groupCaptor = ArgumentCaptor.forClass(classOf[Group])

    verify(mockMembershipRepo, times(2))
      .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
    verify(mockGroupRepo).save(any[DB], groupCaptor.capture())

    val savedGroup = groupCaptor.getValue
    (savedGroup.memberIds should contain).only(okUser.id)
    (savedGroup.adminUserIds should contain).only(okUser.id)
    savedGroup.name shouldBe groupInfo.name
    savedGroup.email shouldBe groupInfo.copy(email = "test@test.com").email
    savedGroup.description shouldBe groupInfo.description
  }

    "update an existing group" should {
      "save the update and add new members and remove deleted members" in {
        doReturn(IO.pure(Some(existingGroup))).when(mockGroupRepo).getGroup(any[String])
        doReturn(().toResult)
          .when(underTest)
          .differentGroupWithSameNameDoesNotExist(any[String], any[String])
        doReturn(().toResult).when(underTest).usersExist(any[Set[String]])
        doReturn(IO.pure(modifiedGroup)).when(mockGroupRepo).save(any[DB], any[Group])
        doReturn(IO.pure(Set()))
          .when(mockMembershipRepo)
          .saveMembers(any[DB], anyString, any[Set[String]], isAdmin = anyBoolean)
        doReturn(IO.pure(Set()))
          .when(mockMembershipRepo)
          .removeMembers(any[DB], anyString, any[Set[String]])
        doReturn(IO.pure(okGroupChangeUpdate))
          .when(mockGroupChangeRepo)
          .save(any[DB], any[GroupChange])

          underTest
            .updateGroup(
              updatedInfo.id,
              updatedInfo.name,
              updatedInfo.email,
              updatedInfo.description,
              updatedInfo.memberIds,
              updatedInfo.adminUserIds,
              okAuth
            )
            .value.unsafeRunSync()

        val groupCaptor = ArgumentCaptor.forClass(classOf[Group])
        val addedMemberCaptor = ArgumentCaptor.forClass(classOf[Set[String]])
        val removedMemberCaptor = ArgumentCaptor.forClass(classOf[Set[String]])
        val groupChangeCaptor = ArgumentCaptor.forClass(classOf[GroupChange])

        verify(mockGroupRepo).save(any[DB], groupCaptor.capture())
        verify(mockMembershipRepo, times(2)).saveMembers(
          any[DB],
          anyString,
          addedMemberCaptor.capture(),
          isAdmin = anyBoolean
        )
        verify(mockMembershipRepo).removeMembers(any[DB], anyString, removedMemberCaptor.capture())
        verify(mockGroupChangeRepo).save(any[DB], groupChangeCaptor.capture())

        val expectedMembers = Set("user1", "user2", "user5", "user6", "user7")
        val expectedAdmins = Set("user1", "user7")
        val savedGroup = groupCaptor.getValue
        savedGroup.name shouldBe updatedInfo.name
        savedGroup.email shouldBe updatedInfo.email
        savedGroup.description shouldBe updatedInfo.description
        savedGroup.memberIds should contain theSameElementsAs expectedMembers
        savedGroup.adminUserIds should contain theSameElementsAs expectedAdmins
        savedGroup.created shouldBe existingGroup.created

        val expectedAddedAdmins = Set("user7")
        val expectedAddedNonAdmins = Set("user2", "user5", "user6")
        val expectedRemovedMembers = Set("user3", "user4")

        val addedMembers = addedMemberCaptor.getAllValues
        addedMembers should contain theSameElementsAs List(
          expectedAddedAdmins,
          expectedAddedNonAdmins
        )

        val removedMembers = removedMemberCaptor.getAllValues
        removedMembers should contain(expectedRemovedMembers)

        val groupChange = groupChangeCaptor.getValue
        groupChange.changeType shouldBe GroupChangeType.Update
        groupChange.newGroup shouldBe savedGroup
        groupChange.oldGroup shouldBe Some(existingGroup)
        groupChange.userId shouldBe okAuth.userId
      }

      "return an error if the user is not an admin" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)

        val error =
          underTest
            .updateGroup(
              updatedInfo.id,
              updatedInfo.name,
              updatedInfo.email,
              updatedInfo.description,
              updatedInfo.memberIds,
              updatedInfo.adminUserIds,
              dummyAuth
            )
            .value.unsafeRunSync().swap.toOption.get

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

        val error =
          underTest
            .updateGroup(
              updatedInfo.id,
              updatedInfo.name,
              updatedInfo.email,
              updatedInfo.description,
              updatedInfo.memberIds,
              updatedInfo.adminUserIds,
              okAuth
            )
            .value.unsafeRunSync().swap.toOption.get

        error shouldBe a[GroupAlreadyExistsError]
      }

      "return an error if group name and/or email is empty" in {
        doReturn(IO.pure(Some(existingGroup)))
          .when(mockGroupRepo)
          .getGroup(existingGroup.id)
        doReturn(().toResult).when(underTest).usersExist(any[Set[String]])
        doReturn(result(GroupValidationError("fail")))
          .when(underTest)
          .groupValidation(existingGroup.copy(name = "", email = ""))

        val error =
          underTest
            .updateGroup(
              updatedInfo.id,
              name = "",
              email = "",
              updatedInfo.description,
              updatedInfo.memberIds,
              updatedInfo.adminUserIds,
              okAuth
            )
            .value.unsafeRunSync().swap.toOption.get

        error shouldBe a[GroupValidationError]
      }

      "return an error if the group is not found" in {
        doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(existingGroup.id)

        val error =
          underTest
            .updateGroup(
              updatedInfo.id,
              updatedInfo.name,
              updatedInfo.email,
              updatedInfo.description,
              updatedInfo.memberIds,
              updatedInfo.adminUserIds,
              okAuth
            )
            .value.unsafeRunSync().swap.toOption.get

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

        val error =
          underTest
            .updateGroup(
              updatedInfo.id,
              updatedInfo.name,
              updatedInfo.email,
              updatedInfo.description,
              updatedInfo.memberIds,
              updatedInfo.adminUserIds,
              okAuth
            )
            .value.unsafeRunSync().swap.toOption.get

        error shouldBe a[UserNotFoundError]
      }

      "return an error if the group has no members or admins" in {
        doReturn(IO.pure(Some(existingGroup)))
          .when(mockGroupRepo)
          .getGroup(existingGroup.id)

        val error =
          underTest
            .updateGroup(
              updatedInfo.id,
              updatedInfo.name,
              updatedInfo.email,
              updatedInfo.description,
              Set(),
              Set(),
              okAuth
            )
            .value.unsafeRunSync().swap.toOption.get

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
          .save(any[DB], any[GroupChange])
        doReturn(IO.pure(Set[String]()))
          .when(mockMembershipRepo)
          .removeMembers(any[DB], anyString, any[Set[String]])
        doReturn(IO.pure(None))
          .when(mockRecordSetRepo)
          .getFirstOwnedRecordByGroup(anyString)
        doReturn(IO.pure(None))
          .when(mockZoneRepo)
          .getFirstOwnedZoneAclGroupId(anyString())

        val result: Group = underTest.deleteGroup("ok", okAuth).value.unsafeRunSync().toOption.get
        result shouldBe okGroup.copy(status = GroupStatus.Deleted)

        val groupCaptor = ArgumentCaptor.forClass(classOf[Group])
        val groupChangeCaptor = ArgumentCaptor.forClass(classOf[GroupChange])
        verify(mockGroupRepo).delete(groupCaptor.capture())
        verify(mockGroupChangeRepo).save(any[DB], groupChangeCaptor.capture())
        verify(mockMembershipRepo).removeMembers(any[DB], anyString, any[Set[String]])

        val savedGroup = groupCaptor.getValue
        savedGroup.status shouldBe GroupStatus.Deleted

        val savedGroupChange = groupChangeCaptor.getValue
        savedGroupChange.changeType shouldBe GroupChangeType.Delete
        savedGroupChange.userId shouldBe okAuth.userId
        savedGroupChange.newGroup shouldBe okGroup
      }

      "return an error if the user is not an admin" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)

        val error = underTest.deleteGroup("ok", dummyAuth).value.unsafeRunSync().swap.toOption.get

        error shouldBe a[NotAuthorizedError]
      }

      "return an error if the group is not found" in {
        doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(anyString)
        doReturn(IO.pure(List())).when(mockZoneRepo).getZonesByAdminGroupId(anyString)

        val error = underTest.deleteGroup("ok", okAuth).value.unsafeRunSync().swap.toOption.get

        error shouldBe a[GroupNotFoundError]
      }

      "return an error if the group is the admin group of a zone" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
        doReturn(IO.pure(List(zoneActive)))
          .when(mockZoneRepo)
          .getZonesByAdminGroupId(anyString)

        val error = underTest.deleteGroup("ok", okAuth).value.unsafeRunSync().swap.toOption.get

        error shouldBe an[InvalidGroupRequestError]
      }

      "return an error if the group is an owner for a record set" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
        doReturn(IO.pure(Some("somerecordsetid")))
          .when(mockRecordSetRepo)
          .getFirstOwnedRecordByGroup(anyString())
        val error = underTest.deleteGroup("ok", okAuth).value.unsafeRunSync().swap.toOption.get

        error shouldBe an[InvalidGroupRequestError]
      }

      "return an error if the group has an ACL rule on a zone" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
        doReturn(IO.pure(Some("someId")))
          .when(mockZoneRepo)
          .getFirstOwnedZoneAclGroupId(anyString())
        val error = underTest.deleteGroup("ok", okAuth).value.unsafeRunSync().swap.toOption.get

        error shouldBe an[InvalidGroupRequestError]
      }

    }

    "get a group" should {
      "return the group" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroup(anyString)
        val result: Group = underTest.getGroup(okGroup.id, okAuth).value.unsafeRunSync().toOption.get
        result shouldBe okGroup
      }

      "return an error if the group is not found" in {
        doReturn(IO.pure(None)).when(mockGroupRepo).getGroup(anyString)
        val error = underTest.getGroup("notfound", okAuth).value.unsafeRunSync().swap.toOption.get
        error shouldBe a[GroupNotFoundError]
      }
    }

    "get my groups" should {
      "return all the groups where user is a member when no optional parameters are used" in {
        doReturn(IO.pure(listOfDummyGroups.toSet))
          .when(mockGroupRepo)
          .getGroups(any[Set[String]])
        val result: ListMyGroupsResponse =
          underTest.listMyGroups(None, None, 100, listOfDummyGroupsAuth, false).value.unsafeRunSync().toOption.get
        verify(mockGroupRepo, never()).getAllGroups()
        result shouldBe ListMyGroupsResponse(
          groups = listOfDummyGroupInfo.take(100),
          None,
          None,
          nextId = Some(listOfDummyGroups(99).id),
          maxItems = 100,
          ignoreAccess = false
        )
      }
      "return only return groups whose name matches the filter" in {
        doReturn(IO.pure(listOfDummyGroups.toSet))
          .when(mockGroupRepo)
          .getGroups(any[Set[String]])
        val result: ListMyGroupsResponse =
          underTest
            .listMyGroups(
              groupNameFilter = Some("name-dummy01"),
              startFrom = None,
              maxItems = 100,
              listOfDummyGroupsAuth,
              false
            )
            .value.unsafeRunSync().toOption.get

        result shouldBe ListMyGroupsResponse(
          groups = listOfDummyGroupInfo.slice(10, 20),
          groupNameFilter = Some("name-dummy01"),
          startFrom = None,
          nextId = None,
          maxItems = 100,
          ignoreAccess = false
        )
      }
      "return only return groups whose name matches the filter, regardless of case" in {
        doReturn(IO.pure(listOfDummyGroups.toSet))
          .when(mockGroupRepo)
          .getGroups(any[Set[String]])
        val result: ListMyGroupsResponse =
          underTest
            .listMyGroups(
              groupNameFilter = Some("Name-Dummy01"),
              startFrom = None,
              maxItems = 100,
              listOfDummyGroupsAuth,
              false
            )
            .value.unsafeRunSync().toOption.get

        result shouldBe ListMyGroupsResponse(
          groups = listOfDummyGroupInfo.slice(10, 20),
          groupNameFilter = Some("Name-Dummy01"),
          startFrom = None,
          nextId = None,
          maxItems = 100,
          ignoreAccess = false
        )
      }
      "return only return groups after startFrom" in {
        doReturn(IO.pure(listOfDummyGroups.toSet))
          .when(mockGroupRepo)
          .getGroups(any[Set[String]])
        val result: ListMyGroupsResponse =
          underTest
            .listMyGroups(
              groupNameFilter = None,
              startFrom = Some(listOfDummyGroups(99).id),
              maxItems = 100,
              listOfDummyGroupsAuth,
              ignoreAccess = false
            )
            .value.unsafeRunSync().toOption.get

        result shouldBe ListMyGroupsResponse(
          groups = listOfDummyGroupInfo.slice(100, 200),
          groupNameFilter = None,
          startFrom = Some(listOfDummyGroups(99).id),
          nextId = None,
          maxItems = 100,
          ignoreAccess = false
        )
      }
      "return only return maxItems groups" in {
        doReturn(IO.pure(listOfDummyGroups.toSet))
          .when(mockGroupRepo)
          .getGroups(any[Set[String]])
        val result: ListMyGroupsResponse =
          underTest
            .listMyGroups(
              groupNameFilter = None,
              startFrom = None,
              maxItems = 10,
              listOfDummyGroupsAuth,
              ignoreAccess = false
            )
            .value.unsafeRunSync().toOption.get

        result shouldBe ListMyGroupsResponse(
          groups = listOfDummyGroupInfo.slice(0, 10),
          groupNameFilter = None,
          startFrom = None,
          nextId = Some(listOfDummyGroups(9).id),
          maxItems = 10,
          ignoreAccess = false
        )
      }
      "return an empty set if the user is not a member of any groups" in {
        doReturn(IO.pure(Set())).when(mockGroupRepo).getGroups(any[Set[String]])
        val result: ListMyGroupsResponse =
          underTest.listMyGroups(None, None, 100, notAuth, false).value.unsafeRunSync().toOption.get
        result shouldBe ListMyGroupsResponse(Seq(), None, None, None, 100, false)
      }
      "return all groups from the database if ignoreAccess is true" in {
        doReturn(IO.pure(Set(okGroup, dummyGroup))).when(mockGroupRepo).getAllGroups()
        val result: ListMyGroupsResponse =
          underTest.listMyGroups(None, None, 100, notAuth, true).value.unsafeRunSync().toOption.get
        verify(mockGroupRepo).getAllGroups()
        result.groups should contain theSameElementsAs Seq(
          GroupInfo(dummyGroup),
          GroupInfo(okGroup)
        )
      }
      "return all groups from the database for super users even if ignoreAccess is false" in {
        doReturn(IO.pure(Set(okGroup, dummyGroup))).when(mockGroupRepo).getAllGroups()
        val result: ListMyGroupsResponse =
          underTest.listMyGroups(None, None, 100, superUserAuth, false).value.unsafeRunSync().toOption.get
        verify(mockGroupRepo).getAllGroups()
        result.groups should contain theSameElementsAs Seq(
          GroupInfo(dummyGroup),
          GroupInfo(okGroup)
        )
      }
      "return all groups from the database for super users if ignoreAccess is true" in {
        doReturn(IO.pure(Set(okGroup, dummyGroup))).when(mockGroupRepo).getAllGroups()
        val result: ListMyGroupsResponse =
          underTest.listMyGroups(None, None, 100, superUserAuth, true).value.unsafeRunSync().toOption.get
        verify(mockGroupRepo).getAllGroups()
        result.groups should contain theSameElementsAs Seq(
          GroupInfo(dummyGroup),
          GroupInfo(okGroup)
        )
      }
      "return all groups from the database for support users even if ignoreAccess is false" in {
        val supportAuth = AuthPrincipal(okUser.copy(isSupport = true), Seq())
        doReturn(IO.pure(Set(okGroup, dummyGroup))).when(mockGroupRepo).getAllGroups()
        val result: ListMyGroupsResponse =
          underTest.listMyGroups(None, None, 100, supportAuth, false).value.unsafeRunSync().toOption.get
        verify(mockGroupRepo).getAllGroups()
        result.groups should contain theSameElementsAs Seq(
          GroupInfo(dummyGroup),
          GroupInfo(okGroup)
        )
      }
      "return all groups from the database for support users if ignoreAccess is true" in {
        val supportAuth = AuthPrincipal(okUser.copy(isSupport = true), Seq())
        doReturn(IO.pure(Set(okGroup, dummyGroup))).when(mockGroupRepo).getAllGroups()
        val result: ListMyGroupsResponse =
          underTest.listMyGroups(None, None, 100, supportAuth, true).value.unsafeRunSync().toOption.get
        verify(mockGroupRepo).getAllGroups()
        result.groups should contain theSameElementsAs Seq(
          GroupInfo(dummyGroup),
          GroupInfo(okGroup)
        )
      }
      "do not return deleted groups" in {
        val deletedGroupAuth: AuthPrincipal = AuthPrincipal(okUser, Seq(deletedGroup.id))
        doReturn(IO.pure(Set(deletedGroup)))
          .when(mockGroupRepo)
          .getGroups(any[Set[String]])
        val result: ListMyGroupsResponse =
          underTest.listMyGroups(None, None, 100, deletedGroupAuth, false).value.unsafeRunSync().toOption.get
        result shouldBe ListMyGroupsResponse(Seq(), None, None, None, 100, false)
      }
    }

    "getGroupChange" should {
      "return the single group change" in {
        val groupChangeRepoResponse = listOfDummyGroupChanges.take(1).head
        doReturn(IO.pure(Option(groupChangeRepoResponse)))
          .when(mockGroupChangeRepo)
          .getGroupChange(anyString)

        doReturn(IO.pure(ListUsersResults(Seq(dummyUser), Some("1"))))
          .when(mockUserRepo)
          .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])

        val userMap = Seq(dummyUser).map(u => (u.id, u.userName)).toMap
        val expected: GroupChangeInfo =
          listOfDummyGroupChanges.map(change => GroupChangeInfo.apply(change.copy(userName = userMap.get(change.userId)))).take(1).head

        val result: GroupChangeInfo =
          underTest.getGroupChange(dummyGroup.id, dummyAuth).value.unsafeRunSync().toOption.get
        result shouldBe expected
      }

      "return the single group change even if the user is not authorized" in {
        val groupChangeRepoResponse = listOfDummyGroupChanges.take(1).head
        doReturn(IO.pure(Some(groupChangeRepoResponse)))
          .when(mockGroupChangeRepo)
          .getGroupChange(anyString)

        doReturn(IO.pure(ListUsersResults(Seq(dummyUser), Some("1"))))
          .when(mockUserRepo)
          .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])

        val userMap = Seq(dummyUser).map(u => (u.id, u.userName)).toMap
        val expected: GroupChangeInfo =
          listOfDummyGroupChanges.map(change => GroupChangeInfo.apply(change.copy(userName = userMap.get(change.userId)))).take(1).head

        val result: GroupChangeInfo =
          underTest.getGroupChange(dummyGroup.id, okAuth).value.unsafeRunSync().toOption.get
        result shouldBe expected
      }

      "return a InvalidGroupRequestError if the group change id is not valid" in {
        doReturn(IO.pure(None))
          .when(mockGroupChangeRepo)
          .getGroupChange(anyString)

        doReturn(IO.pure(ListUsersResults(Seq(dummyUser), Some("1"))))
          .when(mockUserRepo)
          .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])

        val result = underTest.getGroupChange(dummyGroup.id, okAuth).value.unsafeRunSync().swap.toOption.get
        result shouldBe a[InvalidGroupRequestError]
      }
    }

    "getGroupActivity" should {
      "return the group activity" in {
        val groupChangeRepoResponse = ListGroupChangesResults(
          listOfDummyGroupChanges.take(100),
          Some(listOfDummyGroupChanges(100).id)
        )
        doReturn(IO.pure(groupChangeRepoResponse))
          .when(mockGroupChangeRepo)
          .getGroupChanges(anyString, any[Option[String]], anyInt)

        doReturn(IO.pure(ListUsersResults(Seq(dummyUser), Some("1"))))
          .when(mockUserRepo)
          .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])

        val userMap = Seq(dummyUser).map(u => (u.id, u.userName)).toMap
        val expected: List[GroupChangeInfo] =
          listOfDummyGroupChanges.map(change => GroupChangeInfo.apply(change.copy(userName = userMap.get(change.userId)))).take(100)

        val result: ListGroupChangesResponse =
          underTest.getGroupActivity(dummyGroup.id, None, 100, dummyAuth).value.unsafeRunSync().toOption.get
        result.changes should contain theSameElementsAs expected
        result.maxItems shouldBe 100
        result.nextId shouldBe Some(listOfDummyGroupChanges(100).id)
        result.startFrom shouldBe None
      }

    "return group activity even if the user is not authorized" in {
      val groupChangeRepoResponse = ListGroupChangesResults(
        listOfDummyGroupChanges.take(100),
        Some(listOfDummyGroupChanges(100).id)
      )
      doReturn(IO.pure(groupChangeRepoResponse))
        .when(mockGroupChangeRepo)
        .getGroupChanges(anyString, any[Option[String]], anyInt)

      doReturn(IO.pure(ListUsersResults(Seq(dummyUser), Some("1"))))
        .when(mockUserRepo)
        .getUsers(any[Set[String]], any[Option[String]], any[Option[Int]])

      val userMap = Seq(dummyUser).map(u => (u.id, u.userName)).toMap
      val expected: List[GroupChangeInfo] =
        listOfDummyGroupChanges.map(change => GroupChangeInfo.apply(change.copy(userName = userMap.get(change.userId)))).take(100)

      val result: ListGroupChangesResponse =
        underTest.getGroupActivity(dummyGroup.id, None, 100, okAuth).value.unsafeRunSync().toOption.get
      result.changes should contain theSameElementsAs expected
      result.maxItems shouldBe 100
      result.nextId shouldBe Some(listOfDummyGroupChanges(100).id)
      result.startFrom shouldBe None
    }
  }

    "get group user ids" should {
      "get all users in a group change" in {
        val groupChange = Seq(okGroupChange, dummyGroupChangeUpdate, okGroupChange.copy(changeType = GroupChangeType.Delete))
        val result: Set[String] = underTest.getGroupUserIds(groupChange)
        result shouldBe Set("12345-abcde-6789", "56789-edcba-1234", "ok")
      }
    }

    "determine group difference" should {
      "return difference between two groups" in {
        val groupChange = Seq(okGroupChange, dummyGroupChangeUpdate, okGroupChange.copy(changeType = GroupChangeType.Delete))
        val allUserMap = Map("ok" -> "ok", "12345-abcde-6789" -> "dummyName", "56789-edcba-1234" -> "super")
        val result: Seq[String] = underTest.determineGroupDifference(groupChange, allUserMap).value.unsafeRunSync().toOption.get
        // Newly created group's change message
        result(0) shouldBe "Group Created."
        // Updated group's change message
        result(1) shouldBe "Group name changed to 'dummy-group'. Group email changed to 'dummy@test.com'. Group description changed to 'dummy group'. Group admin/s with user name/s 'dummyName','super' added. Group admin/s with user name/s 'ok' removed. Group member/s with user name/s 'dummyName','super' added. Group member/s with user name/s 'ok' removed."
        // Deleted group's change message
        result(2) shouldBe "Group Deleted."
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
          underTest.listAdmins(testGroup.id, okAuth).value.unsafeRunSync().toOption.get
        result.admins should contain theSameElementsAs expectedAdmins
      }

      "return a list of admins even if the user is not authorized" in {
        val testGroup =
          okGroup.copy(memberIds = Set(okUser.id), adminUserIds = Set(okUser.id))
        val testListUsersResult = ListUsersResults(Seq(okUser), Some("1"))
        val expectedAdmins = List(UserInfo(okUser))

        doReturn(IO.pure(Some(testGroup))).when(mockGroupRepo).getGroup(testGroup.id)
        doReturn(IO.pure(testListUsersResult))
          .when(mockUserRepo)
          .getUsers(testGroup.adminUserIds, None, None)

        val result: ListAdminsResponse =
          underTest.listAdmins(testGroup.id, dummyAuth).value.unsafeRunSync().toOption.get
        result.admins should contain theSameElementsAs expectedAdmins
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
          underTest.listMembers(testGroup.id, None, 100, testAuth).value.unsafeRunSync().toOption.get

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
          memberGroupIds = Seq.empty
        )

        doReturn(IO.pure(Some(testGroup))).when(mockGroupRepo).getGroup(testGroup.id)
        doReturn(IO.pure(testListUsersResult))
          .when(mockUserRepo)
          .getUsers(testGroup.memberIds, None, Some(100))

        val result: ListMembersResponse =
          underTest.listMembers(testGroup.id, None, 100, supportAuth).value.unsafeRunSync().toOption.get

        result.members should contain theSameElementsAs expectedMembers
        result.nextId shouldBe testListUsersResult.lastEvaluatedId
        result.maxItems shouldBe 100
        result.startFrom shouldBe None
      }

      "return a list of members even if the user is not a group member" in {
        val testGroup =
          okGroup.copy(memberIds = Set(okUser.id, dummyUser.id), adminUserIds = Set(okUser.id))
        val testUsers = Seq(okUser, dummyUser)
        val testListUsersResult = ListUsersResults(testUsers, Some("1"))
        val expectedMembers = List(MemberInfo(okUser, okGroup), MemberInfo(dummyUser, dummyGroup))

        doReturn(IO.pure(Some(testGroup))).when(mockGroupRepo).getGroup(testGroup.id)
        doReturn(IO.pure(testListUsersResult))
          .when(mockUserRepo)
          .getUsers(testGroup.memberIds, None, Some(100))

        val result: ListMembersResponse =
          underTest.listMembers(testGroup.id, None, 100, dummyAuth).value.unsafeRunSync().toOption.get

        result.members should contain theSameElementsAs expectedMembers
        result.nextId shouldBe testListUsersResult.lastEvaluatedId
        result.maxItems shouldBe 100
        result.startFrom shouldBe None
      }
    }

    "groupWithSameNameDoesNotExist" should {
      "return true when a group with the same name does not exist" in {
        doReturn(IO.pure(None)).when(mockGroupRepo).getGroupByName("foo")

        val result = underTest.groupWithSameNameDoesNotExist("foo").value.unsafeRunSync()
        result should be(right)
      }

      "return a GroupAlreadyExistsError if a group with the same name already exists" in {
        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroupByName("foo")

        val result = underTest.groupWithSameNameDoesNotExist("foo").value.unsafeRunSync().swap.toOption.get
        result shouldBe a[GroupAlreadyExistsError]
      }

      "return true if a group with the same name exists but is deleted" in {
        doReturn(IO.pure(Some(deletedGroup))).when(mockGroupRepo).getGroupByName("foo")

        val result = underTest.groupWithSameNameDoesNotExist("foo").value.unsafeRunSync()
        result should be(right)
      }
    }

    "usersExist" should {
      "return a () if all users exist" in {
        doReturn(IO.pure(ListUsersResults(Seq(okUser), None)))
          .when(mockUserRepo)
          .getUsers(okGroup.memberIds, None, None)

        val result = underTest.usersExist(okGroup.memberIds).value.unsafeRunSync()
        result should be(right)
      }

      "return UserNotFoundError if any of the requested users were not found" in {
        doReturn(IO.pure(ListUsersResults(Seq(okUser), None)))
          .when(mockUserRepo)
          .getUsers(Set(okUser.id, dummyUser.id), None, None)

        val result = underTest.usersExist(Set(okUser.id, dummyUser.id)).value.unsafeRunSync().swap.toOption.get
        result shouldBe a[UserNotFoundError]
      }
    }

    "differentGroupWithSameNameDoesNotExist" should {
      "return GroupAlreadyExistsError if a different group with the same name already exists" in {
        val existingGroup = okGroup.copy(id = "something else")

        doReturn(IO.pure(Some(existingGroup))).when(mockGroupRepo).getGroupByName("foo")

        val error =
          underTest.differentGroupWithSameNameDoesNotExist("foo", "bar").value.unsafeRunSync().swap.toOption.get
        error shouldBe a[GroupAlreadyExistsError]
      }

      "return true if the same group exists with the same name" in {

        doReturn(IO.pure(Some(okGroup))).when(mockGroupRepo).getGroupByName(okGroup.name)

        val result =
          underTest.differentGroupWithSameNameDoesNotExist(okGroup.name, okGroup.id).value.unsafeRunSync()
        result should be(right)
      }

      "return true if the a different group exists but is deleted" in {
        val existingGroup = okGroup.copy(id = "something else", status = GroupStatus.Deleted)

        doReturn(IO.pure(Some(existingGroup)))
          .when(mockGroupRepo)
          .getGroupByName(okGroup.name)

        val result =
          underTest.differentGroupWithSameNameDoesNotExist(okGroup.name, okGroup.id).value.unsafeRunSync()
        result should be(right)
      }
    }

    "isNotZoneAdmin" should {
      "return true when a group for deletion is not the admin of a zone" in {
        doReturn(IO.pure(List())).when(mockZoneRepo).getZonesByAdminGroupId(okGroup.id)

        val result = underTest.isNotZoneAdmin(okGroup).value.unsafeRunSync()
        result should be(right)
      }

      "return an InvalidGroupRequestError when a group for deletion is admin of a zone" in {
        doReturn(IO.pure(List(zoneActive)))
          .when(mockZoneRepo)
          .getZonesByAdminGroupId(okGroup.id)

        val error = underTest.isNotZoneAdmin(okGroup).value.unsafeRunSync().swap.toOption.get
        error shouldBe an[InvalidGroupRequestError]
      }
    }

    "isNotRecordOwnerGroup" should {
      "return true when a group for deletion is not the admin of a zone" in {
        doReturn(IO.pure(None)).when(mockRecordSetRepo).getFirstOwnedRecordByGroup(okGroup.id)

        val result = underTest.isNotRecordOwnerGroup(okGroup).value.unsafeRunSync()
        result should be(right)
      }

      "return an InvalidGroupRequestError when a group for deletion is admin of a zone" in {
        doReturn(IO.pure(Some("somerecordsetid")))
          .when(mockRecordSetRepo)
          .getFirstOwnedRecordByGroup(okGroup.id)

        val error = underTest.isNotRecordOwnerGroup(okGroup).value.unsafeRunSync().swap.toOption.get
        error shouldBe an[InvalidGroupRequestError]
      }
    }

    "isNotZoneAclGroupId" should {
      "return successfully when a groupId is not in any zone ACL" in {
        doReturn(IO.pure(None)).when(mockZoneRepo).getFirstOwnedZoneAclGroupId(okGroup.id)

        val result = underTest.isNotInZoneAclRule(okGroup).value.unsafeRunSync()
        result should be(right)
      }

      "return an InvalidGroupRequestError when a group has an ACL rule in a zone" in {
        doReturn(IO.pure(Some("someZoneId")))
          .when(mockZoneRepo)
          .getFirstOwnedZoneAclGroupId(okGroup.id)

        val error = underTest.isNotInZoneAclRule(okGroup).value.unsafeRunSync().swap.toOption.get
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
        val error =
          underTest
            .updateUserLockStatus(okUser.id, LockStatus.Locked, dummyAuth)
            .value.unsafeRunSync().swap.toOption.get

        error shouldBe a[NotAuthorizedError]
      }

      "return an error if the signed in user is only a support admin" in {
        val supportAuth = okAuth.copy(
          signedInUser = dummyAuth.signedInUser.copy(isSupport = true),
          memberGroupIds = Seq.empty
        )
        val error =
          underTest
            .updateUserLockStatus(okUser.id, LockStatus.Locked, supportAuth)
            .value.unsafeRunSync().swap.toOption.get

        error shouldBe a[NotAuthorizedError]
      }

      "return an error if the requested user is not found" in {
        doReturn(IO.pure(None)).when(mockUserRepo).getUser(okUser.id)

        val error =
          underTest
            .updateUserLockStatus(okUser.id, LockStatus.Locked, superUserAuth)
            .value.unsafeRunSync().swap.toOption.get

        error shouldBe a[UserNotFoundError]
      }
    }

    "get user" should {
      "return the user" in {
        doReturn(IO.pure(Some(okUser))).when(mockUserRepo).getUserByIdOrName(anyString)
        val result: User = underTest.getUser(okUser.id, okAuth).value.unsafeRunSync().toOption.get
        result shouldBe okUser
      }

      "return an error if the user is not found" in {
        doReturn(IO.pure(None)).when(mockUserRepo).getUserByIdOrName(anyString)
        val error = underTest.getUser("notfound", okAuth).value.unsafeRunSync().swap.toOption.get
        error shouldBe a[UserNotFoundError]
      }
    }
  }
}
