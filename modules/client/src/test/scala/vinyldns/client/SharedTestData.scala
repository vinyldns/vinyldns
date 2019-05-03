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

package vinyldns.client

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

import java.util.UUID

import japgolly.scalajs.react.Callback
import vinyldns.client.models.batch.BatchChangeSummaryResponse
import vinyldns.client.models.membership.{GroupResponse, Id, UserResponse}
import vinyldns.client.models.record.{RecordData, RecordSetChangeResponse, RecordSetResponse}
import vinyldns.client.models.zone.{ACLRule, Rules, ZoneResponse}
import vinyldns.core.domain.batch.BatchChangeStatus
import vinyldns.core.domain.record.{RecordSetChangeStatus, RecordSetChangeType, RecordType}
import vinyldns.core.domain.zone.{AccessLevel, ZoneStatus}

trait SharedTestData {
  val testUser: UserResponse =
    UserResponse("testUser", "testId", Some("test"), Some("user"), Some("testuser@email.com"))
  val dummyUser: UserResponse =
    UserResponse("dummyUser", "dummyId", Some("dummy"), Some("user"), Some("dummyuser@email.com"))

  val testUUID = "99701afe-9794-431c-9986-41ce074c9387"

  val userAclRule = ACLRule(
    AccessLevel.Write,
    List(RecordType.A, RecordType.CNAME),
    Some("desc"),
    Some(testUser.id),
    Some(testUser.userName),
    None,
    Some("mask*"),
    Some(testUser.userName)
  )

  val groupAclRule = ACLRule(
    AccessLevel.Delete,
    List(RecordType.NS, RecordType.AAAA),
    Some("desc"),
    None,
    None,
    Some(generateGroups(1).head.id),
    Some("mask*"),
    Some(generateGroups(1).head.name)
  )

  def generateGroups(
      numGroups: Int,
      members: List[UserResponse] = List(testUser),
      admins: List[UserResponse] = List(testUser)): Seq[GroupResponse] = {
    val memberIds = members.map(m => Id(m.id))
    val adminIds = admins.map(m => Id(m.id))

    for {
      i <- 0 until numGroups
    } yield
      GroupResponse(
        s"name-$i",
        s"email-$i@test.com",
        s"id-$i",
        memberIds,
        adminIds,
        None,
        Some(s"created-$i"))
  }

  def generateZones(numZones: Int): Seq[ZoneResponse] =
    for {
      i <- 0 until numZones
    } yield
      ZoneResponse(
        UUID.randomUUID().toString,
        s"name-$i.",
        s"email-$i@test.com",
        UUID.randomUUID().toString,
        ZoneStatus.Active,
        s"created-$i",
        "system",
        false,
        Rules(List()),
        latestSync = Some(s"sync-$i"),
        updated = Some(s"updated-$i"),
        adminGroupName = Some(s"adminGroupName-$i")
      )

  def generateRecordSets(numRecords: Int, zoneId: String): Seq[RecordSetResponse] =
    for {
      i <- 0 until numRecords
    } yield
      RecordSetResponse(
        s"id-$i",
        RecordType.A,
        zoneId,
        s"name-$i",
        300,
        "Active",
        List(RecordData(address = Some("1.1.1.1"))),
        "account",
        s"created-$i",
        Some("Delete") // note the records table update and delete buttons are conditional
      )

  def generateRecordSetChanges(
      numChanges: Int,
      zone: ZoneResponse): Seq[RecordSetChangeResponse] = {
    val records = generateRecordSets(numChanges, zone.id)
    for {
      i <- 0 until numChanges
    } yield
      RecordSetChangeResponse(
        zone,
        records(i),
        testUser.id,
        s"created-$i",
        s"id-$i",
        testUser.userName,
        RecordSetChangeType.Create,
        RecordSetChangeStatus.Complete
      )
  }

  def generateBatchChangeSummaries(numChanges: Int): Seq[BatchChangeSummaryResponse] =
    for {
      i <- 0 until numChanges
    } yield
      BatchChangeSummaryResponse(
        testUser.id,
        testUser.userName,
        s"created-$i",
        i,
        BatchChangeStatus.Complete,
        UUID.randomUUID().toString,
        Some("comments"),
        None
      )

  // a lot of times components use anonymous functions like refreshGroups, setNotification, etc
  def generateNoOpHandler[T]: T => Callback = _ => Callback.empty
}
