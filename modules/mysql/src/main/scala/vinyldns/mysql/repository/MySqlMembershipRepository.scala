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

import java.sql.SQLException

class MySqlMembershipRepository extends MembershipRepository with Monitored {
  private final val logger = LoggerFactory.getLogger(classOf[MySqlMembershipRepository])

  private final val SAVE_MEMBERS =
    sql"""
         | INSERT INTO membership (user_id, group_id, is_admin)
         |      VALUES ({userId}, {groupId}, {isAdmin})
       """.stripMargin

  // Replace "ON DUPLICATE KEY UPDATE" which is used before to prevent possible deadlock
  private final val UPDATE_MEMBERS =
    sql"""
         | UPDATE membership
         |      SET is_admin = {isAdmin}
         |      WHERE user_id = {userId} AND group_id = {groupId}
       """.stripMargin

  private final val BASE_GET_USERS_FOR_GROUP =
    "SELECT user_id FROM membership WHERE group_id = {groupId}"

  private final val BASE_REMOVE_MEMBERS = "DELETE FROM membership WHERE group_id = ?"

  private final val GET_GROUPS_FOR_USER =
    sql"""
      |SELECT group_id
      |  FROM membership
      | WHERE user_id = ?
    """.stripMargin

  def saveParams(
      userIds: List[String],
      groupId: String,
      isAdmin: Boolean
  ): Seq[Seq[(Symbol, Any)]] =
    userIds.sorted.map { userId =>
      Seq(
        'userId -> userId,
        'groupId -> groupId,
        'isAdmin -> isAdmin
      )
    }

  def saveMembers(db: DB, groupId: String, memberUserIds: Set[String], isAdmin: Boolean): IO[Set[String]] =
    memberUserIds.toList match {
      case Nil => IO.pure(memberUserIds)
      case nonEmpty =>
        monitor("repo.Membership.addMembers") {
          IO {
            logger.info(s"Saving into group $groupId members $nonEmpty")
            db.withinTx { implicit s =>
              try {
                SAVE_MEMBERS.batchByName(saveParams(nonEmpty, groupId, isAdmin): _*).apply()
              }
              catch {
                case ex: SQLException =>
                  // Check for duplicate key exception and update the members if we get that exception
                  // 1062 is Error Code for Duplicate key entry
                  if(ex.getErrorCode == 1062){
                    UPDATE_MEMBERS.batchByName(saveParams(nonEmpty, groupId, isAdmin): _*).apply()
                  }
              }
              memberUserIds
            }
          }
        }
    }

  def removeMembers(db: DB, groupId: String, memberUserIds: Set[String]): IO[Set[String]] =
    memberUserIds.toList match {
      case Nil => IO.pure(memberUserIds)
      case nonEmpty =>
        monitor("repo.Membership.removeMembers") {
          IO {
            logger.info(s"Removing from group $groupId members $nonEmpty")
            db.withinTx { implicit s =>
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

  def getUsersForGroup(groupId: String, isAdmin: Option[Boolean]): IO[Set[String]] =
    IO {
      logger.info(s"Getting users for group $groupId")
      DB.readOnly { implicit s =>
        val baseConditions = Seq('groupId -> groupId)

        // extra conditions based on whether isAdmin is set
        val (extraQuery, extraConditions) = isAdmin match {
          case None => ("", Seq.empty)
          case Some(adminFlag) =>
            (" AND is_admin = {isAdmin}", Seq('isAdmin -> adminFlag))
        }

        val query = BASE_GET_USERS_FOR_GROUP + extraQuery
        val conditions = baseConditions ++ extraConditions

        SQL(query)
          .bindByName(conditions: _*)
          .map(_.string(1))
          .list()
          .apply()
          .toSet
      }
    }
}
