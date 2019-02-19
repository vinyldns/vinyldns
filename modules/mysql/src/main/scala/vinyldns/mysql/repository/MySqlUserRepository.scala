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
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.membership.{ListUsersResults, User, UserRepository}
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto
import vinyldns.core.protobuf.ProtobufConversions

class MySqlUserRepository(cryptoAlgebra: CryptoAlgebra)
    extends UserRepository
    with Monitored
    with ProtobufConversions {

  private final val logger = LoggerFactory.getLogger(classOf[MySqlUserRepository])

  private final val PUT_USER =
    sql"""
         | REPLACE INTO user (id, user_name, access_key, data)
         |  VALUES ({id}, {userName}, {accessKey}, {data})
       """.stripMargin

  private final val GET_USER_BY_ID =
    sql"""
         | SELECT data
         |  FROM user
         |  WHERE id = ?
       """.stripMargin

  private final val GET_USER_BY_ACCESS_KEY =
    sql"""
         | SELECT data
         |  FROM user
         |  WHERE access_key = ?
       """.stripMargin

  private final val GET_USER_BY_USER_NAME =
    sql"""
         | SELECT data
         |  FROM user
         |  WHERE user_name = ?
       """.stripMargin

  final val BASE_GET_USERS: String =
    """
      | SELECT data
      |  FROM user
      |  WHERE id
      """.stripMargin

  def getUser(userId: String): IO[Option[User]] =
    monitor("repo.User.getUser") {
      logger.info(s"Getting user with id: $userId")
      IO {
        DB.readOnly { implicit s =>
          GET_USER_BY_ID
            .bind(userId)
            .map(toUser(1))
            .first()
            .apply()
        }
      }
    }

  /*
   * startFrom and maxItems were originally made to batch the search in the dynamodb implementation,
   * not needed here with the current sql statement. This function is not exposed by an API route
   */
  def getUsers(
      userIds: Set[String],
      startFrom: Option[String],
      maxItems: Option[Int]): IO[ListUsersResults] =
    monitor("repo.User.getUsers") {
      logger.info(s"Getting users with ids: $userIds")
      IO {
        if (userIds.isEmpty)
          ListUsersResults(List[User](), None)
        else {
          val users = DB.readOnly { implicit s =>
            val inClause = " IN (" + userIds.toList.as("?").mkString(",") + ")"
            val query = BASE_GET_USERS + inClause
            SQL(query)
              .bind(userIds.toList: _*)
              .map(toUser(1))
              .list()
              .apply()
          }
          ListUsersResults(users, None)
        }
      }
    }

  def getUserByAccessKey(accessKey: String): IO[Option[User]] =
    monitor("repo.User.getUserByAccessKey") {
      IO {
        logger.info(s"Getting user with accessKey: $accessKey")
        DB.readOnly { implicit s =>
          GET_USER_BY_ACCESS_KEY
            .bind(accessKey)
            .map(toUser(1))
            .first()
            .apply()
        }
      }
    }

  def getUserByName(userName: String): IO[Option[User]] =
    monitor("repo.User.getUserByName") {
      IO {
        logger.info(s"Getting user with userName: $userName")
        DB.readOnly { implicit s =>
          GET_USER_BY_USER_NAME
            .bind(userName)
            .map(toUser(1))
            .first()
            .apply()
        }
      }
    }

  def save(user: User): IO[User] =
    monitor("repo.User.save") {
      IO {
        logger.info(s"Saving user with id: ${user.id}")
        DB.localTx { implicit s =>
          PUT_USER
            .bindByName(
              'id -> user.id,
              'userName -> user.userName,
              'accessKey -> user.accessKey,
              'data -> toPB(user.withEncryptedSecretKey(cryptoAlgebra)).toByteArray
            )
            .update()
            .apply()
        }
        user
      }
    }

  private def toUser(colIndex: Int): WrappedResultSet => User = res => {
    fromPB(VinylDNSProto.User.parseFrom(res.bytes(colIndex)))
  }
}
