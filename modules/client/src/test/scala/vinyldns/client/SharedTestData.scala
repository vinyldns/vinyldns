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

import japgolly.scalajs.react.Callback
import vinyldns.client.models.membership.{Group, Id, User}
import vinyldns.client.models.record.{RecordData, RecordSet}
import vinyldns.client.models.zone.{Rules, Zone}
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.zone.ZoneStatus

trait SharedTestData {
  val testUser: User =
    User("testUser", "testId", Some("test"), Some("user"), Some("testuser@email.com"))
  val dummyUser: User =
    User("dummyUser", "dummyId", Some("dummy"), Some("user"), Some("dummyuser@email.com"))

  val testUUID = "99701afe-9794-431c-9986-41ce074c9387"

  def generateGroups(
      numGroups: Int,
      members: List[User] = List(testUser),
      admins: List[User] = List(testUser)): Seq[Group] = {
    val memberIds = members.map(m => Id(m.id))
    val adminIds = admins.map(m => Id(m.id))

    for {
      i <- 0 until numGroups
    } yield
      Group(
        s"name-$i",
        s"email-$i@test.com",
        s"id-$i",
        memberIds,
        adminIds,
        None,
        Some(s"created-$i"))
  }

  def generateZones(numZones: Int): Seq[Zone] =
    for {
      i <- 0 until numZones
    } yield
      Zone(
        s"id-$i",
        s"name-$i.",
        s"email-$i@test.com",
        s"adminGroupId-$i",
        ZoneStatus.Active,
        s"created-$i",
        "system",
        false,
        Rules(List()),
        adminGroupName = Some(s"adminGroupName-$i")
      )

  def generateRecordSets(numRecords: Int, zoneId: String): Seq[RecordSet] =
    for {
      i <- 0 until numRecords
    } yield
      RecordSet(
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

  // a lot of times components use anonymous functions like refreshGroups, setNotification, etc
  def generateNoOpHandler[T]: T => Callback = _ => Callback.empty
}
