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
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.domain.membership._
import vinyldns.core.domain.zone._

// $COVERAGE-OFF$
object TestDataLoader {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  private val logger: Logger = LoggerFactory.getLogger("vinyldns.api.repository.TestDataLoader")

  final val testUser = User(
    userName = "testuser",
    id = "testuser",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "testUserAccessKey",
    secretKey = "testUserSecretKey",
    firstName = Some("Test"),
    lastName = Some("User"),
    email = Some("test@test.com"),
    isTest = true
  )
  final val okUser = User(
    userName = "ok",
    id = "ok",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "okAccessKey",
    secretKey = "okSecretKey",
    firstName = Some("ok"),
    lastName = Some("ok"),
    email = Some("test@test.com"),
    isTest = true
  )
  final val dummyUser = User(
    userName = "dummy",
    id = "dummy",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "dummyAccessKey",
    secretKey = "dummySecretKey",
    isTest = true
  )
  final val sharedZoneUser = User(
    userName = "sharedZoneUser",
    id = "sharedZoneUser",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "sharedZoneUserAccessKey",
    secretKey = "sharedZoneUserSecretKey",
    firstName = Some("sharedZoneUser"),
    lastName = Some("sharedZoneUser"),
    email = Some("test@test.com"),
    isTest = true
  )
  final val lockedUser = User(
    userName = "locked",
    id = "locked",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "lockedAccessKey",
    secretKey = "lockedSecretKey",
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
      created = DateTime.now.secondOfDay().roundFloorCopy(),
      accessKey = "dummy",
      secretKey = "dummy",
      isTest = true
    )
  }
  final val listGroupUser = User(
    userName = "list-group-user",
    id = "list-group-user",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "listGroupAccessKey",
    secretKey = "listGroupSecretKey",
    firstName = Some("list-group"),
    lastName = Some("list-group"),
    email = Some("test@test.com"),
    isTest = true
  )

  final val listZonesUser = User(
    userName = "list-zones-user",
    id = "list-zones-user",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "listZonesAccessKey",
    secretKey = "listZonesSecretKey",
    firstName = Some("list-zones"),
    lastName = Some("list-zones"),
    email = Some("test@test.com"),
    isTest = true
  )

  final val zoneHistoryUser = User(
    userName = "history-user",
    id = "history-id",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "history-key",
    secretKey = "history-secret",
    firstName = Some("history-first"),
    lastName = Some("history-last"),
    email = Some("history@history.com"),
    isTest = true
  )

  final val listBatchChangeSummariesUser = User(
    userName = "list-batch-summaries-user",
    id = "list-batch-summaries-id",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "listBatchSummariesAccessKey",
    secretKey = "listBatchSummariesSecretKey",
    firstName = Some("list-batch-summaries"),
    lastName = Some("list-batch-summaries"),
    email = Some("test@test.com"),
    isTest = true
  )

  final val listZeroBatchChangeSummariesUser = User(
    userName = "list-zero-summaries-user",
    id = "list-zero-summaries-id",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "listZeroSummariesAccessKey",
    secretKey = "listZeroSummariesSecretKey",
    firstName = Some("list-zero-summaries"),
    lastName = Some("list-zero-summaries"),
    email = Some("test@test.com"),
    isTest = true
  )

  final val supportUser = User(
    userName = "support-user",
    id = "support-user-id",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "supportUserAccessKey",
    secretKey = "supportUserSecretKey",
    firstName = Some("support-user"),
    lastName = Some("support-user"),
    email = Some("test@test.com"),
    isSupport = true,
    isTest = true
  )

  final val sharedZoneGroup = Group(
    name = "testSharedZoneGroup",
    id = "shared-zone-group",
    email = "email",
    memberIds = Set(sharedZoneUser.id),
    adminUserIds = Set(sharedZoneUser.id))

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
    adminUserIds = Set(okUser.id, dummyUser.id))

  final val anotherGlobalACLGroup = Group(
    name = "globalACLGroup",
    id = "another-global-acl-group",
    email = "email",
    memberIds = Set(testUser.id),
    adminUserIds = Set(testUser.id))

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
      membershipRepo: MembershipRepository): IO[Unit] =
    for {
      _ <- (testUser :: okUser :: dummyUser :: sharedZoneUser :: lockedUser :: listGroupUser :: listZonesUser ::
        listBatchChangeSummariesUser :: listZeroBatchChangeSummariesUser :: zoneHistoryUser :: supportUser ::
        listOfDummyUsers).map { user =>
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
          s"Deleting existing shared zones on startup: ${toDelete.map(z => (z.name, z.id))}")
        IO.unit
      }
      _ <- toDelete.map(zoneRepo.save).parSequence
      _ <- groupRepo.save(sharedZoneGroup)
      _ <- groupRepo.save(globalACLGroup)
      _ <- groupRepo.save(anotherGlobalACLGroup)
      _ <- membershipRepo.addMembers(
        groupId = "shared-zone-group",
        memberUserIds = Set(sharedZoneUser.id))
      _ <- membershipRepo.addMembers(
        groupId = "global-acl-group-id",
        memberUserIds = Set(okUser.id, dummyUser.id))
      _ <- membershipRepo.addMembers(
        groupId = "another-global-acl-group",
        memberUserIds = Set(testUser.id))
      _ <- zoneRepo.save(sharedZone)
      _ <- zoneRepo.save(nonTestSharedZone)
    } yield ()
}
// $COVERAGE-ON$
