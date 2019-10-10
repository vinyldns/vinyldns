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

package vinyldns.mysql.repository

import cats.effect.IO
import cats.implicits._
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.core.domain.membership.MembershipRepository
import vinyldns.core.route.Monitored

class MySqlMembershipRepository extends MembershipRepository with Monitored {
  private final val logger = LoggerFactory.getLogger(classOf[MySqlMembershipRepository])

  private final val BASE_ADD_MEMBERS = "INSERT IGNORE INTO membership (user_id, group_id)"

  private final val BASE_REMOVE_MEMBERS = "DELETE FROM membership WHERE group_id = ?"

  private final val GET_GROUPS_FOR_USER =
    sql"""
      |SELECT group_id
      |  FROM membership
      | WHERE user_id = ?
    """.stripMargin

  def addMembers(groupId: String, memberUserIds: Set[String]): IO[Set[String]] =
    memberUserIds.toList match {
      case Nil => IO.pure(memberUserIds)
      case nonEmpty =>
        monitor("repo.Membership.addMembers") {
          IO {
            logger.info(s"Saving into group $groupId members $nonEmpty")
            DB.localTx { implicit s =>
              val valueClause = " VALUES " + nonEmpty.as("(?, ?)").mkString(",")
              val query = BASE_ADD_MEMBERS + valueClause
              val valueParams: List[String] = nonEmpty.flatMap(Seq(_, groupId))
              SQL(query)
                .bind(valueParams: _*)
                .update
                .apply()

              memberUserIds
            }
          }
        }
    }

  def removeMembers(groupId: String, memberUserIds: Set[String]): IO[Set[String]] =
    memberUserIds.toList match {
      case Nil => IO.pure(memberUserIds)
      case nonEmpty =>
        monitor("repo.Membership.removeMembers") {
          IO {
            logger.info(s"Removing from group $groupId members $nonEmpty")
            DB.localTx { implicit s =>
              val inClause = " AND user_id IN (" + nonEmpty.as("?").mkString(",") + ")"
              val query = BASE_REMOVE_MEMBERS + inClause
              SQL(query)
                .bind(groupId :: nonEmpty: _*)
                .update()
                .apply()

              memberUserIds
            }
          }
        }
    }

  def getGroupsForUser(userId: String): IO[Set[String]] =
    monitor("repo.Membership.getGroupsForUser") {
      IO {
        logger.info(s"Getting groups for user $userId")
        DB.readOnly { implicit s =>
          GET_GROUPS_FOR_USER
            .bind(userId)
            .map(_.string(1))
            .list()
            .apply()
            .toSet
        }
      }
    }
}
