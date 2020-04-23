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
import vinyldns.core.domain.membership.{Group, GroupRepository}
import vinyldns.core.protobuf.GroupProtobufConversions
import vinyldns.core.route.Monitored
import scalikejdbc._
import vinyldns.proto.VinylDNSProto

class MySqlGroupRepository extends GroupRepository with GroupProtobufConversions with Monitored {
  import MySqlGroupRepository._

  private final val logger = LoggerFactory.getLogger(classOf[MySqlGroupRepository])

  private final val PUT_GROUP =
    sql"""
         |REPLACE INTO groups(id, name, data, description, created_timestamp, email)
         | VALUES ({id}, {name}, {data}, {description}, {createdTimestamp}, {email})
       """.stripMargin

  private final val DELETE_GROUP =
    sql"""
         |DELETE FROM groups
         | WHERE id = ?
       """.stripMargin

  private final val GET_GROUP_BY_ID =
    sql"""
         |SELECT data
         |  FROM groups
         | WHERE id = ?
       """.stripMargin

  private final val GET_GROUP_BY_NAME =
    sql"""
         |SELECT data
         |  FROM groups
         | WHERE name = ?
       """.stripMargin

  private final val GET_ALL_GROUPS =
    sql"""
         |SELECT data
         |  FROM groups
       """.stripMargin

  private val BASE_GET_GROUPS_BY_IDS =
    """
      |SELECT data
      |  FROM groups
      | WHERE id
    """.stripMargin

  def save(group: Group): IO[Group] =
    monitor("repo.Group.save") {
      IO {
        logger.info(s"Saving group with (id, name): (${group.id}, ${group.name})")
        DB.localTx { implicit s =>
          PUT_GROUP
            .bindByName(
              'id -> group.id,
              'name -> group.name,
              'data -> fromGroup(group),
              'description -> group.description,
              'createdTimestamp -> group.created,
              'email -> group.email
            )
            .update()
            .apply()

          group
        }
      }
    }

  def delete(group: Group): IO[Group] =
    monitor("repo.Group.delete") {
      IO {
        logger.info(s"Deleting group with (id, name): (${group.id}, ${group.name})")
        DB.localTx { implicit s =>
          DELETE_GROUP
            .bind(group.id)
            .update()
            .apply()

          group
        }
      }
    }

  def getGroup(groupId: String): IO[Option[Group]] =
    monitor("repo.Group.getGroup") {
      IO {
        logger.info(s"Getting group with id: $groupId")
        DB.readOnly { implicit s =>
          GET_GROUP_BY_ID
            .bind(groupId)
            .map(toGroup(1))
            .first()
            .apply()
        }
      }
    }

  def getGroups(groupIds: Set[String]): IO[Set[Group]] =
    monitor("repo.Group.getGroups") {
      IO {
        logger.info(s"Getting group with ids: $groupIds")
        if (groupIds.isEmpty)
          Set[Group]()
        else {
          DB.readOnly { implicit s =>
            val groupIdList = groupIds.toList
            val inClause = " IN (" + groupIdList.as("?").mkString(",") + ")"
            val query = BASE_GET_GROUPS_BY_IDS + inClause
            SQL(query)
              .bind(groupIdList: _*)
              .map(toGroup(1))
              .list()
              .apply()
          }.toSet
        }
      }
    }

  def getGroupByName(groupName: String): IO[Option[Group]] =
    monitor("repo.Group.getGroupByName") {
      IO {
        logger.info(s"Getting group with name: $groupName")
        DB.readOnly { implicit s =>
          GET_GROUP_BY_NAME
            .bind(groupName)
            .map(toGroup(1))
            .first()
            .apply()
        }
      }
    }

  def getAllGroups(): IO[Set[Group]] =
    monitor("repo.Group.getAllGroups") {
      IO {
        logger.info(s"Getting all groups")
        DB.readOnly { implicit s =>
          GET_ALL_GROUPS
            .map(toGroup(1))
            .list()
            .apply()
        }.toSet
      }
    }

}

object MySqlGroupRepository extends GroupProtobufConversions {
  def toGroup(colIndex: Int): WrappedResultSet => Group = res => {
    fromPB(VinylDNSProto.Group.parseFrom(res.bytes(colIndex)))
  }

  def fromGroup(group: Group): Array[Byte] =
    toPB(group).toByteArray
}
