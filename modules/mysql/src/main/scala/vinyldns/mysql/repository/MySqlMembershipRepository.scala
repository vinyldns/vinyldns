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
  private final val COLUMN_INDEX = 1

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

  private final val GET_EXISTING_USERS =
    "SELECT user_id FROM membership WHERE group_id = ?"

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
            // Get existing users already present in the group
            val existingMembers = getExistingMembers(groupId).toList
            // Intersect is used to check if the users we are trying to add in the group is already present.
            // If they already exist in the group, we update the users.
            val updateMembers = existingMembers.intersect(nonEmpty)
            // Diff is used to check if the users we are trying to add in the group is already present.
            // If they don't exist in the group, we save the users.
            val saveMembers = nonEmpty.diff(existingMembers)
            logger.debug(s"Saving into group $groupId members $nonEmpty")

            db.withinTx { implicit s =>
              SAVE_MEMBERS.batchByName(saveParams(saveMembers, groupId, isAdmin): _*).apply()
              UPDATE_MEMBERS.batchByName(saveParams(updateMembers, groupId, isAdmin): _*).apply()
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
            logger.debug(s"Removing from group $groupId members $nonEmpty")
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

  def getExistingMembers(groupId: String): Set[String] =
    monitor("repo.Membership.getExistingUsers") {
      IO {
        logger.debug(s"Getting existing users")
        DB.readOnly { implicit s =>
          SQL(GET_EXISTING_USERS)
            .bind(groupId)
            .map(_.string(COLUMN_INDEX))
            .list()
            .apply()
            .toSet
        }
      }
    }.unsafeRunSync()

  def getGroupsForUser(userId: String): IO[Set[String]] =
    monitor("repo.Membership.getGroupsForUser") {
      IO {
        logger.debug(s"Getting groups for user $userId")
        DB.readOnly { implicit s =>
          GET_GROUPS_FOR_USER
            .bind(userId)
            .map(_.string(COLUMN_INDEX))
            .list()
            .apply()
            .toSet
        }
      }
    }

  def getUsersForGroup(groupId: String, isAdmin: Option[Boolean]): IO[Set[String]] =
    IO {
      logger.debug(s"Getting users for group $groupId")
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
          .map(_.string(COLUMN_INDEX))
          .list()
          .apply()
          .toSet
      }
    }
}
