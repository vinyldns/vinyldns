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
import vinyldns.api.repository.dynamodb.DynamoDBMembershipRepository

trait MembershipRepository extends Repository {

  def addMembers(groupId: String, memberUserIds: Set[String]): IO[Set[String]]

  def removeMembers(groupId: String, memberUserIds: Set[String]): IO[Set[String]]

  def getGroupsForUser(userId: String): IO[Set[String]]
}

object MembershipRepository {
  def apply(): MembershipRepository =
    DynamoDBMembershipRepository()

  def loadTestData(repository: MembershipRepository): IO[Set[Set[String]]] =
    List("ok-group", "ok").map(repository.addMembers(_, Set("ok"))).parSequence.map(_.toSet)
}
