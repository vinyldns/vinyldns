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

package vinyldns.api.repository

import cats.effect.{ContextShift, IO}
import cats.implicits._
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc.DB
import vinyldns.core.domain.Encrypted
import vinyldns.core.domain.membership._
import vinyldns.core.domain.zone._
import vinyldns.mysql.TransactionProvider

// $COVERAGE-OFF$
object TestDataLoader extends TransactionProvider {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  private val logger: Logger = LoggerFactory.getLogger("vinyldns.api.repository.TestDataLoader")

  final val testUser = User(
    userName = "testuser",
    id = "testuser",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "testUserAccessKey",
    secretKey = Encrypted("testUserSecretKey"),
    firstName = Some("Test"),
    lastName = Some("User"),
    email = Some("test@test.com"),
    isTest = true
  )
  final val okUser = User(
    userName = "ok",
    id = "ok",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "okAccessKey",
    secretKey = Encrypted("okSecretKey"),
    firstName = Some("ok"),
    lastName = Some("ok"),
    email = Some("test@test.com"),
    isTest = true
  )
  final val dummyUser = User(
    userName = "dummy",
    id = "dummy",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "dummyAccessKey",
    secretKey = Encrypted("dummySecretKey"),
    isTest = true
  )
  final val sharedZoneUser = User(
    userName = "sharedZoneUser",
    id = "sharedZoneUser",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "sharedZoneUserAccessKey",
    secretKey = Encrypted("sharedZoneUserSecretKey"),
    firstName = Some("sharedZoneUser"),
    lastName = Some("sharedZoneUser"),
    email = Some("test@test.com"),
    isTest = true
  )
  final val lockedUser = User(
    userName = "locked",
    id = "locked",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "lockedAccessKey",
    secretKey = Encrypted("lockedSecretKey"),
    firstName = Some("Locked"),
    lastName = Some("User"),
    email = Some("testlocked@test.com"),
    lockStatus = LockStatus.Locked,
    isTest = true
  )
  final val listOfDummyUsers: List[User] = List.range(0, 200).map { runner =>
    User(
      userName = "name-dummy%03d".format(runner),
      id = "dummy%03d".format(runner),
      created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
      accessKey = "dummy",
      secretKey = Encrypted("dummy"),
      isTest = true
    )
  }
  final val listGroupUser = User(
    userName = "list-group-user",
    id = "list-group-user",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "listGroupAccessKey",
    secretKey = Encrypted("listGroupSecretKey"),
    firstName = Some("list-group"),
    lastName = Some("list-group"),
    email = Some("test@test.com"),
    isTest = true
  )

  final val listZonesUser = User(
    userName = "list-zones-user",
    id = "list-zones-user",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "listZonesAccessKey",
    secretKey = Encrypted("listZonesSecretKey"),
    firstName = Some("list-zones"),
    lastName = Some("list-zones"),
    email = Some("test@test.com"),
    isTest = true
  )

  final val zoneHistoryUser = User(
    userName = "history-user",
    id = "history-id",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "history-key",
    secretKey = Encrypted("history-secret"),
    firstName = Some("history-first"),
    lastName = Some("history-last"),
    email = Some("history@history.com"),
    isTest = true
  )

  final val listRecordsUser = User(
    userName = "list-records-user",
    id = "list-records-user",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "listRecordsAccessKey",
    secretKey = Encrypted("listRecordsSecretKey"),
    firstName = Some("list-records"),
    lastName = Some("list-records"),
    email = Some("test@test.com"),
    isTest = true
  )

  final val listBatchChangeSummariesUser = User(
    userName = "list-batch-summaries-user",
    id = "list-batch-summaries-id",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "listBatchSummariesAccessKey",
    secretKey = Encrypted("listBatchSummariesSecretKey"),
    firstName = Some("list-batch-summaries"),
    lastName = Some("list-batch-summaries"),
    email = Some("test@test.com"),
    isTest = true
  )

  final val listBatchChangeSummariesGroup = Group(
    name = "testListBatchChangeSummariesGroup",
    id = "list-summaries-group",
    email = "email",
    memberIds = Set(listBatchChangeSummariesUser.id),
    adminUserIds = Set(listBatchChangeSummariesUser.id)
  )

  final val listZeroBatchChangeSummariesUser = User(
    userName = "list-zero-summaries-user",
    id = "list-zero-summaries-id",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "listZeroSummariesAccessKey",
    secretKey = Encrypted("listZeroSummariesSecretKey"),
    firstName = Some("list-zero-summaries"),
    lastName = Some("list-zero-summaries"),
    email = Some("test@test.com"),
    isTest = true
  )

  final val supportUser = User(
    userName = "support-user",
    id = "support-user-id",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "supportUserAccessKey",
    secretKey = Encrypted("supportUserSecretKey"),
    firstName = Some("support-user"),
    lastName = Some("support-user"),
    email = Some("test@test.com"),
    isSupport = true,
    isTest = true
  )

  final val superUser = User(
    userName = "super-user",
    id = "super-user-id",
    created = Instant.now.truncatedTo(ChronoUnit.SECONDS),
    accessKey = "superUserAccessKey",
    secretKey = "superUserSecretKey",
    secretKey = Encrypted("superUserSecretKey"),
    firstName = Some("super-user"),
    lastName = Some("super-user"),
    email = Some("test@test.com"),
    isSuper = true,
    isTest = true
  )

  final val sharedZoneGroup = Group(
    name = "testSharedZoneGroup",
    id = "shared-zone-group",
    email = "email",
    memberIds = Set(sharedZoneUser.id),
    adminUserIds = Set(sharedZoneUser.id)
  )

  final val sharedZone = Zone(
    name = "shared.",
    email = "email",
    adminGroupId = sharedZoneGroup.id,
    shared = true,
    isTest = true
  )

  final val globalACLGroup = Group(
    name = "globalACLGroup",
    id = "global-acl-group-id",
    email = "email",
    memberIds = Set(okUser.id, dummyUser.id),
    adminUserIds = Set(okUser.id, dummyUser.id)
  )

  final val anotherGlobalACLGroup = Group(
    name = "globalACLGroup",
    id = "another-global-acl-group",
    email = "email",
    memberIds = Set(testUser.id),
    adminUserIds = Set(testUser.id)
  )

  final val duGroup = Group(
    name = "duGroup",
    id = "duGroup-id",
    email = "test@test.com",
    memberIds = listOfDummyUsers.map(_.id).toSet + testUser.id,
    adminUserIds = listOfDummyUsers.map(_.id).toSet + testUser.id
  )

  // NOTE: this is intentionally not a flagged test zone for validating our test users cannot access regular zone info
  // All other test zones should be flagged as test
  final val nonTestSharedZone = Zone(
    name = "non.test.shared.",
    email = "email",
    adminGroupId = sharedZoneGroup.id,
    shared = true
  )

  def loadTestData(
    userRepo: UserRepository,
    groupRepo: GroupRepository,
    zoneRepo: ZoneRepository,
    membershipRepo: MembershipRepository
  ): IO[Unit] = {

    def saveMembersData(
     groupId: String,
     memberUserIds: Set[String],
     isAdmin: Boolean
    ): IO[Unit] = {
      executeWithinTransaction { db: DB =>
        for {
          _ <- membershipRepo.saveMembers(
            db: DB,
            groupId = groupId,
            memberUserIds = memberUserIds,
            isAdmin = isAdmin
          )
        } yield ()
      }
    }

    def saveGroupData(
     group: Group
    ): IO[Unit] = {
      executeWithinTransaction { db: DB =>
        for {
          _ <- groupRepo.save(db, group)
        } yield ()
      }
    }

    for {
      _ <- (testUser :: okUser :: dummyUser :: sharedZoneUser :: lockedUser :: listGroupUser :: listZonesUser ::
        listBatchChangeSummariesUser :: listZeroBatchChangeSummariesUser :: zoneHistoryUser :: supportUser :: superUser ::
        listRecordsUser :: listOfDummyUsers).map { user =>
        listBatchChangeSummariesUser :: listZeroBatchChangeSummariesUser :: zoneHistoryUser :: supportUser ::
        superUser :: listRecordsUser :: listOfDummyUsers).map { user =>
        userRepo.save(user)
      }.parSequence
      // if the test shared zones exist already, clean them out
      existingShared <- zoneRepo.getZonesByFilters(Set(nonTestSharedZone.name, sharedZone.name))
      toDelete = existingShared.collect {
        case test
            if test.isTest ||
              (test.name == nonTestSharedZone.name && test.adminGroupId == nonTestSharedZone.adminGroupId) =>
          test.copy(status = ZoneStatus.Deleted)
      }.toList
      _ <- if (toDelete.length > 2) {
        val msg = s"Unexpected zones to delete on startup: ${toDelete.map(z => (z.name, z.id))}"
        logger.error(s"Unexpected zones to delete on startup: ${toDelete.map(z => (z.name, z.id))}")
        IO.raiseError(new RuntimeException(msg))
      } else {
        logger.info(
          s"Deleting existing shared zones on startup: ${toDelete.map(z => (z.name, z.id))}"
        )
        IO.unit
      }
      _ <- toDelete.map(zoneRepo.save).parSequence
      _ <- saveGroupData(sharedZoneGroup)
      _ <- saveGroupData(globalACLGroup)
      _ <- saveGroupData(anotherGlobalACLGroup)
      _ <- saveGroupData(duGroup)
      _ <- saveGroupData(listBatchChangeSummariesGroup)
      _ <- saveMembersData(
        groupId = "shared-zone-group",
        memberUserIds = Set(sharedZoneUser.id),
        isAdmin = true
      )
      _ <- saveMembersData(
        groupId = "global-acl-group-id",
        memberUserIds = Set(okUser.id, dummyUser.id),
        isAdmin = true
      )
      _ <- saveMembersData(
        groupId = "another-global-acl-group",
        memberUserIds = Set(testUser.id),
        isAdmin = true
      )
      _ <- saveMembersData(
        groupId = duGroup.id,
        memberUserIds = duGroup.memberIds,
        isAdmin = true
      )
      _ <- saveMembersData(
        groupId = listBatchChangeSummariesGroup.id,
        memberUserIds = listBatchChangeSummariesGroup.memberIds,
        isAdmin = true
      )
      _ <- zoneRepo.save(sharedZone)
      _ <- zoneRepo.save(nonTestSharedZone)
    } yield ()
  }
}
// $COVERAGE-ON$
