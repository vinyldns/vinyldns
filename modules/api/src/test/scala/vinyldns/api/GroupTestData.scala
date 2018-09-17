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

package vinyldns.api

import java.util.UUID

import org.joda.time.DateTime
import org.scalatest.Matchers
import vinyldns.api.domain.membership.{GroupChangeInfo, GroupInfo, MemberInfo, UserInfo}
import vinyldns.api.repository.TestDataLoader
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership._
import vinyldns.core.domain.zone.{Zone, ZoneStatus}

trait GroupTestData { this: Matchers =>

  val okUser: User = TestDataLoader.okUser
  val dummyUser: User = TestDataLoader.dummyUser
  val lockedUser: User = TestDataLoader.lockedUser

  val listOfDummyUsers: List[User] = TestDataLoader.listOfDummyUsers
  val okUserInfo: UserInfo = UserInfo(okUser)
  val dummyUserInfo: UserInfo = UserInfo(dummyUser)

  val okGroup: Group = Group(
    "ok",
    "test@test.com",
    Some("a test group"),
    memberIds = Set(okUser.id),
    adminUserIds = Set(okUser.id),
    created = DateTime.now.secondOfDay().roundFloorCopy())
  val deletedGroup: Group =
    Group("deleted", "test@test.com", Some("a deleted group"), status = GroupStatus.Deleted)
  val updatedGroup: Group = okGroup.copy(
    name = "updated",
    email = "updated@tlistOfRandomTimeGroupChangesest.com",
    description = Some("a new description"))
  val twoUserGroup: Group = Group(
    "twoUsers",
    "test@test.com",
    Some("has two users"),
    memberIds = Set[String](okUser.id, dummyUser.id),
    created = DateTime.now.secondOfDay().roundFloorCopy())
  val twoAdminGroup: Group = Group(
    "twoAdmins",
    "test@test.com",
    Some("has two admins"),
    adminUserIds = Set[String](okUser.id, dummyUser.id))
  val dummyGroup: Group = Group(
    "dummy",
    "test@test.com",
    Some("has the dummy users"),
    memberIds = listOfDummyUsers.map(_.id).toSet)
  val oneUserDummyGroup: Group = Group(
    "dummy",
    "test@test.com",
    Some("has a dummy user"),
    memberIds = Set(listOfDummyUsers(0).id))
  val listOfDummyGroups: List[Group] = List.range(0, 200).map { i =>
    Group(
      name = "name-dummy%03d".format(i),
      id = "dummy%03d".format(i),
      email = "test@test.com",
      created = DateTime.now.secondOfDay().roundFloorCopy())
  }
  val listOfDummyGroupInfo: List[GroupInfo] = listOfDummyGroups.map(GroupInfo.apply)

  val okMemberInfo: MemberInfo = MemberInfo(okUser, okGroup)
  val dummyMemberInfo: MemberInfo = MemberInfo(dummyUser, okGroup)
  val listOfMembersInfo: List[MemberInfo] =
    TestDataLoader.listOfDummyUsers.map(MemberInfo(_, okGroup))

  val okGroupAuth: AuthPrincipal = AuthPrincipal(okUser, Seq(okGroup.id))
  val okGroupInfo: GroupInfo = GroupInfo(okGroup)
  val twoUserGroupInfo: GroupInfo = GroupInfo(twoUserGroup)

  val okUserAuth: AuthPrincipal = AuthPrincipal(okUser, Seq(okGroup.id, twoUserGroup.id))
  val noGroupsUserAuth: AuthPrincipal = AuthPrincipal(okUser, Seq())
  val deletedGroupAuth: AuthPrincipal = AuthPrincipal(okUser, Seq(deletedGroup.id))
  val dummyUserAuth: AuthPrincipal = AuthPrincipal(dummyUser, Seq(dummyGroup.id))
  val lockedUserAuth: AuthPrincipal = AuthPrincipal(lockedUser, Seq())
  val listOfDummyGroupsAuth: AuthPrincipal = AuthPrincipal(dummyUser, listOfDummyGroups.map(_.id))

  val memberOkZoneAuthorized: Zone = Zone(
    "memberok.zone.recordsets.",
    "test@test.com",
    status = ZoneStatus.Active,
    adminGroupId = okGroup.id)
  val memberZoneNotAuthorized: Zone =
    memberOkZoneAuthorized.copy(adminGroupId = UUID.randomUUID().toString)
  val memberZoneShared: Zone = memberZoneNotAuthorized.copy(shared = true)

  val okGroupChange: GroupChange = GroupChange(
    okGroup,
    GroupChangeType.Create,
    okUser.id,
    created = DateTime.now.secondOfDay().roundFloorCopy())
  val okGroupChangeUpdate: GroupChange = GroupChange(
    okGroup,
    GroupChangeType.Update,
    okUser.id,
    Some(okGroup),
    created = DateTime.now.secondOfDay().roundFloorCopy())
  val okGroupChangeDelete: GroupChange = GroupChange(
    okGroup,
    GroupChangeType.Delete,
    okUser.id,
    created = DateTime.now.secondOfDay().roundFloorCopy())

  val okGroupChangeInfo: GroupChangeInfo = GroupChangeInfo(okGroupChange)
  val okGroupChangeUpdateInfo: GroupChangeInfo = GroupChangeInfo(okGroupChangeUpdate)
  val okGroupChangeDeleteInfo: GroupChangeInfo = GroupChangeInfo(okGroupChangeDelete)

  val now: DateTime = DateTime.now().secondOfDay().roundFloorCopy()

  // changes added in reverse order
  val listOfDummyGroupChanges: List[GroupChange] = List.range(0, 300).map { i =>
    GroupChange(
      oneUserDummyGroup,
      GroupChangeType.Update,
      dummyUser.id,
      created = now.minusSeconds(i),
      id = s"$i")
  }

  val listOfDummyGroupChangesInfo: List[GroupChangeInfo] =
    listOfDummyGroupChanges.map(GroupChangeInfo.apply)

  /**
    * Verify that one group matches the other, cannot use case class equals
    * as the DateTimes are slightly off after JSON serialization
    */
  def verifyGroupsMatch(left: Group, right: Group): Unit = {
    left should have(
      'name (right.name),
      'email (right.email),
      'description (right.description),
      'status (right.status),
      'adminUserIds (right.adminUserIds)
    )

    Option(left.created) shouldBe defined
  }

  def verifyGroupsMatch(left: GroupInfo, right: Group): Unit = {
    left should have(
      'name (right.name),
      'email (right.email),
      'description (right.description),
      'status (right.status),
      'members (right.memberIds.map(UserInfo(_))),
      'admins (right.adminUserIds.map(UserInfo(_)))
    )

    Option(left.created) shouldBe defined
  }

  // Ignore created, members, and admins
  def verifyGroupInfoMatch(left: GroupInfo, right: GroupInfo): Unit = {
    left should have(
      'id (right.id),
      'name (right.name),
      'email (right.email),
      'description (right.description),
      'status (right.status)
    )

    Option(left.created) shouldBe defined
  }

  def verifyGroupInfoMatch(left: Set[GroupInfo], right: Set[GroupInfo]): Unit = {
    val pairs = left.toSeq.sortBy(_.id).zip(right.toSeq.sortBy(_.id))
    pairs.foreach(pair => verifyGroupInfoMatch(pair._1, pair._2))
  }
}
