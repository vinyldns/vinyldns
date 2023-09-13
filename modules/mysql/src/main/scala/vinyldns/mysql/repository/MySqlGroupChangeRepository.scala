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
import org.slf4j.LoggerFactory
import vinyldns.core.domain.membership.{GroupChange, GroupChangeRepository, ListGroupChangesResults}
import vinyldns.core.protobuf.GroupProtobufConversions
import vinyldns.core.route.Monitored
import scalikejdbc._
import vinyldns.proto.VinylDNSProto

class MySqlGroupChangeRepository extends GroupChangeRepository with Monitored {
  import MySqlGroupChangeRepository._

  private final val logger = LoggerFactory.getLogger(classOf[MySqlGroupChangeRepository])

  private final val PUT_GROUP_CHANGE =
    sql"""
      |INSERT INTO group_change (id, group_id, created_timestamp, data)
      | VALUES ({id}, {group_id}, {created_timestamp}, {data})
      | ON DUPLICATE KEY UPDATE group_id = {group_id}, created_timestamp = {created_timestamp}, data = {data}
    """.stripMargin

  private final val GET_GROUP_CHANGE =
    sql"""
      |SELECT data
      |  FROM group_change
      | WHERE id = ?
    """.stripMargin

  private final val LIST_GROUP_CHANGES_WITH_START =
    sql"""
      |SELECT data
      |  FROM group_change
      | WHERE group_id = {groupId}
      | ORDER BY created_timestamp DESC
      | LIMIT {maxItems} OFFSET {startFrom}
    """.stripMargin

  private final val LIST_GROUP_CHANGE_NO_START =
    sql"""
      |SELECT data
      |  FROM group_change
      | WHERE group_id = {groupId}
      | ORDER BY created_timestamp DESC
      | LIMIT {maxItems}
    """.stripMargin

  def save(db: DB, groupChange: GroupChange): IO[GroupChange] =
    monitor("repo.GroupChange.save") {
      IO {
        logger.debug(
          s"Saving group change with (group_change_id, group_id): " +
            s"(${groupChange.id}, ${groupChange.newGroup.id})"
        )
        db.withinTx { implicit s =>
          PUT_GROUP_CHANGE
            .bindByName(
              'id -> groupChange.id,
              'group_id -> groupChange.newGroup.id,
              'created_timestamp -> groupChange.created.toEpochMilli,
              'data -> fromGroupChange(groupChange)
            )
            .update()
            .apply()

          groupChange
        }
      }
    }

  def getGroupChange(groupChangeId: String): IO[Option[GroupChange]] =
    monitor("repo.GroupChange.getGroupChange") {
      IO {
        logger.debug(s"Getting group change with group_change_id: $groupChangeId")
        DB.readOnly { implicit s =>
          GET_GROUP_CHANGE
            .bind(groupChangeId)
            .map(toGroupChange(1))
            .first()
            .apply()
        }
      }
    }

  def getGroupChanges(
      groupId: String,
      startFrom: Option[Int],
      maxItems: Int
  ): IO[ListGroupChangesResults] =
    monitor("repo.GroupChange.getGroupChanges") {
      IO {
        logger.debug(
          s"Getting group changes with (groupId, startFrom, maxItems): ($groupId, $startFrom, $maxItems)"
        )
        DB.readOnly { implicit s =>
          val query = startFrom match {
            case Some(start) =>
              LIST_GROUP_CHANGES_WITH_START
                .bindByName('groupId -> groupId, 'startFrom -> start, 'maxItems -> (maxItems + 1))
            case None =>
              LIST_GROUP_CHANGE_NO_START
                .bindByName('groupId -> groupId, 'maxItems -> (maxItems + 1))
          }
          val queryResult = query
            .map(toGroupChange(1))
            .list()
            .apply()

          val maxQueries = queryResult.take(maxItems)
          val startValue = startFrom.getOrElse(0)

          val nextId = queryResult match {
            case _ if queryResult.size <= maxItems | queryResult.isEmpty => None
            case _ => Some(startValue + maxItems)
          }

          ListGroupChangesResults(maxQueries, nextId)
        }
      }
    }
}

object MySqlGroupChangeRepository extends GroupProtobufConversions {
  def toGroupChange(colIndex: Int): WrappedResultSet => GroupChange =
    res => fromPB(VinylDNSProto.GroupChange.parseFrom(res.bytes(colIndex)))

  def fromGroupChange(groupChange: GroupChange): Array[Byte] =
    toPB(groupChange).toByteArray
}
