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

import org.joda.time.DateTime
import vinyldns.core.domain.membership.{Group, User}

object TestMembershipData {

  val okUser: User = User(
    userName = "ok",
    id = "ok",
    created = DateTime.now.secondOfDay().roundFloorCopy(),
    accessKey = "okAccessKey",
    secretKey = "okSecretKey",
    firstName = Some("ok"),
    lastName = Some("ok"),
    email = Some("test@test.com")
  )

  val dummyUser = User("dummyName", "dummyAccess", "dummySecret")

  val okGroup: Group = Group(
    "ok",
    "test@test.com",
    Some("a test group"),
    memberIds = Set(okUser.id),
    adminUserIds = Set(okUser.id),
    created = DateTime.now.secondOfDay().roundFloorCopy())

  val emptyGroup = Group("grpName", "grpEmail")

}
