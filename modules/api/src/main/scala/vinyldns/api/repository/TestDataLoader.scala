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
import vinyldns.core.domain.membership._
import vinyldns.core.domain.zone._

// $COVERAGE-OFF$
object TestDataLoader {

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

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

  final val sharedZoneGroup = Group(
    name = "testSharedZoneGroup",
    id = "shared-zone-group",
    email = "email",
    memberIds = Set(sharedZoneUser.id),
    adminUserIds = Set(sharedZoneUser.id))

  final val sharedZone = Zone(
    name = "shared.",
    email = "email",
    id = "shared-zone",
    adminGroupId = sharedZoneGroup.id,
    shared = true,
    isTest = true
  )

  def loadTestData(
      userRepo: UserRepository,
      groupRepo: GroupRepository,
      zoneRepo: ZoneRepository,
      membershipRepo: MembershipRepository): IO[Unit] =
    for {
      _ <- (testUser :: okUser :: dummyUser :: sharedZoneUser :: lockedUser :: listGroupUser :: listZonesUser ::
        listBatchChangeSummariesUser :: listZeroBatchChangeSummariesUser :: zoneHistoryUser :: listOfDummyUsers).map {
        user =>
          userRepo.save(user)
      }.parSequence
      _ <- groupRepo.save(sharedZoneGroup)
      _ <- membershipRepo.addMembers(
        groupId = "shared-zone-group",
        memberUserIds = Set(sharedZoneUser.id))
      _ <- zoneRepo.save(sharedZone)
    } yield ()
}
// $COVERAGE-ON$
