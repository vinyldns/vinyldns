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

package vinyldns.core


import vinyldns.core.domain.Encrypted
import java.time.Instant
import java.time.temporal.ChronoUnit
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.membership._

object TestMembershipData {

  /* USERS */
  val okUser: User = User(
    userName = "ok",
    id = "ok",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "okAccessKey",
    secretKey = Encrypted("okSecretKey"),
    firstName = Some("ok"),
    lastName = Some("ok"),
    email = Some("test@test.com")
  )

  val dummyUser = User("dummyName", "dummyAccess", Encrypted("dummySecret"))
  val superUser = User("super", "superAccess", Encrypted("superSecret"), isSuper = true)
  val xyzUser = User("xyz", "xyzAccess", Encrypted("xyzSecret"))
  val supportUser = User("support", "supportAccess", Encrypted("supportSecret"), isSupport = true)
  val lockedUser = User("locked", "lockedAccess", Encrypted("lockedSecret"), lockStatus = LockStatus.Locked)
  val sharedZoneUser = User("sharedZoneAdmin", "sharedAccess", Encrypted("sharedSecret"))

  val listOfDummyUsers: List[User] = List.range(0, 200).map { runner =>
    User(
      userName = "name-dummy%03d".format(runner),
      id = "dummy%03d".format(runner),
      created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
      accessKey = "dummy",
      secretKey = Encrypted("dummy")
    )
  }

  /* GROUPS */
  val okGroup: Group = Group(
    "ok",
    "test@test.com",
    Some("a test group"),
    memberIds = Set(okUser.id),
    adminUserIds = Set(okUser.id),
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS)
  )

  val dummyGroup: Group = Group(
    "dummy",
    "test@test.com",
    Some("has the dummy users"),
    adminUserIds = listOfDummyUsers.map(_.id).toSet,
    memberIds = listOfDummyUsers.map(_.id).toSet
  )

  val twoUserGroup: Group = Group(
    "twoUsers",
    "test@test.com",
    Some("has two users"),
    memberIds = Set(okUser.id),
    adminUserIds = Set(okUser.id, dummyUser.id),
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS)
  )

  val abcGroup: Group = Group("abc", "abc", id = "abc", memberIds = Set("abc", sharedZoneUser.id))

  val emptyGroup = Group("grpName", "grpEmail")

  val deletedGroup: Group =
    Group("deleted", "test@test.com", Some("a deleted group"), status = GroupStatus.Deleted)

  val oneUserDummyGroup: Group = Group(
    "dummy",
    "test@test.com",
    Some("has a dummy user"),
    memberIds = Set(listOfDummyUsers(0).id)
  )

  val listOfDummyGroups: List[Group] = List.range(0, 200).map { i =>
    Group(
      name = "name-dummy%03d".format(i),
      id = "dummy%03d".format(i),
      email = "test@test.com",
      created = Instant.now.truncatedTo(ChronoUnit.SECONDS)
    )
  }

  val xyzGroup: Group = Group("xyz", "xyz", id = "xyz", memberIds = Set("xyz"))

  /* AUTHS */
  val okAuth: AuthPrincipal = AuthPrincipal(okUser, Seq(okGroup.id))

  val xyzAuth: AuthPrincipal = okAuth.copy(
    signedInUser = okAuth.signedInUser.copy(userName = "xyz", id = "xyz"),
    memberGroupIds = List(xyzGroup.id, okGroup.id)
  )

  val abcAuth: AuthPrincipal = okAuth.copy(
    signedInUser = okAuth.signedInUser.copy(userName = "abc", id = "abc"),
    memberGroupIds = List(abcGroup.id, okGroup.id)
  )

  val dummyAuth: AuthPrincipal = AuthPrincipal(dummyUser, Seq(dummyGroup.id, oneUserDummyGroup.id))

  val notAuth: AuthPrincipal = AuthPrincipal(User("not", "auth", Encrypted("secret")), Seq.empty)

  val sharedAuth: AuthPrincipal = AuthPrincipal(sharedZoneUser, Seq(abcGroup.id))

  val supportUserAuth: AuthPrincipal = AuthPrincipal(supportUser, Seq(okGroup.id))

  val superUserAuth: AuthPrincipal = AuthPrincipal(superUser, Seq.empty)

  /* GROUP CHANGES */
  val okGroupChange: GroupChange = GroupChange(
    okGroup,
    GroupChangeType.Create,
    okUser.id,
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS)
  )
  val okGroupChangeUpdate: GroupChange = GroupChange(
    okGroup,
    GroupChangeType.Update,
    okUser.id,
    Some(okGroup),
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS)
  )
  val okGroupChangeDelete: GroupChange = GroupChange(
    okGroup,
    GroupChangeType.Delete,
    okUser.id,
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS)
  )

  // changes added in reverse order
  val now: Instant = Instant.now.truncatedTo(ChronoUnit.SECONDS)
  val listOfDummyGroupChanges: List[GroupChange] = List.range(0, 300).map { i =>
    GroupChange(
      oneUserDummyGroup,
      GroupChangeType.Update,
      dummyUser.id,
      created = now.minusSeconds(i),
      id = s"$i"
    )
  }
  val dummyGroupChangeUpdate: GroupChange = GroupChange(
    okGroup.copy(name = "dummy-group", email = "dummy@test.com", description = Some("dummy group"),
      memberIds = Set(dummyUser.copy(id="12345-abcde-6789").id, superUser.copy(id="56789-edcba-1234").id),
      adminUserIds = Set(dummyUser.copy(id="12345-abcde-6789").id, superUser.copy(id="56789-edcba-1234").id)),
    GroupChangeType.Update,
    okUser.id,
    Some(okGroup),
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS)
  )
}
