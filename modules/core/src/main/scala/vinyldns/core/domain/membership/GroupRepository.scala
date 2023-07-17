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

package vinyldns.core.domain.membership

import cats.effect._
import scalikejdbc.DB
import vinyldns.core.repository.Repository

trait GroupRepository extends Repository {

  def save(db: DB, group: Group): IO[Group]

  def delete(group: Group): IO[Group]

  /*Looks up a group.  If the group is not found, or if the group's status is Deleted, will return None */
  def getGroup(groupId: String): IO[Option[Group]]

  def getGroups(groupIds: Set[String]): IO[Set[Group]]

  def getGroupsByName(groupNames: Set[String]): IO[Set[Group]]

  def getGroupByName(groupName: String): IO[Option[Group]]

  def getGroupsByName(groupName: String): IO[Set[Group]]

  def getAllGroups(): IO[Set[Group]]

}
