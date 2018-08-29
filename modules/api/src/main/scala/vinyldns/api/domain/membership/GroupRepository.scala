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

import cats.effect._
import cats.implicits._
import vinyldns.api.repository.Repository
import vinyldns.api.repository.dynamodb.DynamoDBGroupRepository

trait GroupRepository extends Repository {

  def save(group: Group): IO[Group]

  /*Looks up a group.  If the group is not found, or if the group's status is Deleted, will return None */
  def getGroup(groupId: String): IO[Option[Group]]

  def getGroups(groupIds: Set[String]): IO[Set[Group]]

  def getGroupByName(groupName: String): IO[Option[Group]]

  def getAllGroups(): IO[Set[Group]]
}

object GroupRepository {
  def apply(): GroupRepository =
    DynamoDBGroupRepository()

  final val okGroup1 = Group(
    "ok-group",
    "test@test.com",
    memberIds = Set("ok"),
    adminUserIds = Set("ok"),
    id = "ok-group")
  final val okGroup2 =
    Group("ok", "test@test.com", memberIds = Set("ok"), adminUserIds = Set("ok"), id = "ok")

  def loadTestData(repository: GroupRepository): IO[List[Group]] =
    List(okGroup1, okGroup2).map(repository.save(_)).parSequence
}
