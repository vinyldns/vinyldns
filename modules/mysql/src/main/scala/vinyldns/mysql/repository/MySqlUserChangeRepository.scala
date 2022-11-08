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
import scalikejdbc._
import vinyldns.core.domain.membership.{UserChange, UserChangeRepository}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto

class MySqlUserChangeRepository
    extends UserChangeRepository
    with Monitored
    with ProtobufConversions {
  private final val logger = LoggerFactory.getLogger(classOf[MySqlUserChangeRepository])

  private final val PUT_USER_CHANGE =
    sql"""
         |  INSERT INTO user_change (change_id, user_id, data, created_timestamp)
         |       VALUES ({changeId}, {userId}, {data}, {createdTimestamp}) ON DUPLICATE KEY
         |       UPDATE user_id=VALUES(user_id),
         |              data=VALUES(data),
         |              created_timestamp=VALUES(created_timestamp)
       """.stripMargin

  private final val GET_USER_CHANGE_BY_ID =
    sql"""
         |  SELECT data
         |    FROM user_change
         |   WHERE change_id = ?
       """.stripMargin

  def get(changeId: String): IO[Option[UserChange]] =
    monitor("repo.UserChange.get") {
      logger.debug(s"Getting user change with id: $changeId")
      IO {
        DB.readOnly { implicit s =>
          GET_USER_CHANGE_BY_ID
            .bind(changeId)
            .map(toUserChange(1))
            .first()
            .apply()
        }
      }
    }

  def save(change: UserChange): IO[UserChange] =
    monitor("repo.UserChange.save") {
      logger.debug(s"Saving user change: $change")
      IO {
        DB.localTx { implicit s =>
          PUT_USER_CHANGE
            .bindByName(
              'changeId -> change.id,
              'userId -> change.madeByUserId,
              'data -> toPb(change).toByteArray,
              'createdTimestamp -> change.created.toEpochMilli
            )
            .update()
            .apply()
        }
        change
      }
    }

  private def toUserChange(colIndex: Int): WrappedResultSet => UserChange = res => {
    fromPb(VinylDNSProto.UserChange.parseFrom(res.bytes(colIndex)))
  }
}
