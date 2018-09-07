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

import cats.effect.IO
import cats.implicits._
import org.joda.time.DateTime
import vinyldns.api.VinylDNSConfig
import vinyldns.api.crypto.Crypto
import vinyldns.core.domain.membership._

// $COVERAGE-OFF$
object TestDataLoader {

  final val testUser = User(
    userName = "testuser",
    id = "testuser",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "testUserAccessKey",
    secretKey = "testUserSecretKey",
    firstName = Some("Test"),
    lastName = Some("User"),
    email = Some("test@test.com")
  )
  final val okUser = User(
    userName = "ok",
    id = "ok",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "okAccessKey",
    secretKey = "okSecretKey",
    firstName = Some("ok"),
    lastName = Some("ok"),
    email = Some("test@test.com")
  )
  final val dummyUser = User(
    userName = "dummy",
    id = "dummy",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "dummyAccessKey",
    secretKey = "dummySecretKey")
  final val listOfDummyUsers: List[User] = List.range(0, 200).map { runner =>
    User(
      userName = "name-dummy%03d".format(runner),
      id = "dummy%03d".format(runner),
      created = DateTime.now.secondOfDay().roundFloorCopy(),
      accessKey = "dummy",
      secretKey = "dummy"
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
    email = Some("test@test.com")
  )

  final val listZonesUser = User(
    userName = "list-zones-user",
    id = "list-zones-user",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "listZonesAccessKey",
    secretKey = "listZonesSecretKey",
    firstName = Some("list-zones"),
    lastName = Some("list-zones"),
    email = Some("test@test.com")
  )

  final val zoneHistoryUser = User(
    userName = "history-user",
    id = "history-id",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "history-key",
    secretKey = "history-secret",
    firstName = Some("history-first"),
    lastName = Some("history-last"),
    email = Some("history@history.com")
  )

  final val listBatchChangeSummariesUser = User(
    userName = "list-batch-summaries-user",
    id = "list-batch-summaries-id",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "listBatchSummariesAccessKey",
    secretKey = "listBatchSummariesSecretKey",
    firstName = Some("list-batch-summaries"),
    lastName = Some("list-batch-summaries"),
    email = Some("test@test.com")
  )

  final val listZeroBatchChangeSummariesUser = User(
    userName = "list-zero-summaries-user",
    id = "list-zero-summaries-id",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "listZeroSummariesAccessKey",
    secretKey = "listZeroSummariesSecretKey",
    firstName = Some("list-zero-summaries"),
    lastName = Some("list-zero-summaries"),
    email = Some("test@test.com")
  )

  def loadTestData(repository: UserRepository): IO[List[User]] =
    (testUser :: okUser :: dummyUser :: listGroupUser :: listZonesUser :: listBatchChangeSummariesUser ::
      listZeroBatchChangeSummariesUser :: zoneHistoryUser :: listOfDummyUsers).map { user =>
      val encrypted =
        if (VinylDNSConfig.encryptUserSecrets)
          user.copy(secretKey = Crypto.encrypt(user.secretKey))
        else user
      repository.save(encrypted)
    }.parSequence
}
// $COVERAGE-ON$
