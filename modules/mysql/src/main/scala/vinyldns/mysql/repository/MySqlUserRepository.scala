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
         |   FROM user
         |  WHERE id = ?
       """.stripMargin

  private final val GET_USER_BY_ACCESS_KEY =
    sql"""
         | SELECT data
         |   FROM user
         |  WHERE access_key = ?
       """.stripMargin

  private final val GET_USER_BY_USER_NAME =
    sql"""
         | SELECT data
         |   FROM user
         |  WHERE user_name = ?
       """.stripMargin

  private final val GET_USER_BY_ID_OR_NAME =
    sql"""
         | SELECT data
         |   FROM user
         |    WHERE id = {id} OR user_name LIKE {userName}
     """.stripMargin

  private final val BASE_GET_USERS: String =
    """
      | SELECT data
      |  FROM user
      """.stripMargin

  def getUser(userId: String): IO[Option[User]] =
    monitor("repo.User.getUser") {
      logger.debug(s"Getting user with id: $userId")
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

  def getUsers(
      userIds: Set[String],
      startFrom: Option[String],
      maxItems: Option[Int]
  ): IO[ListUsersResults] =
    monitor("repo.User.getUsers") {
      logger.debug(s"Getting users with ids: $userIds")
      IO {
        if (userIds.isEmpty)
          ListUsersResults(List[User](), None)
        else {
          val users = DB.readOnly { implicit s =>
            val sb = new StringBuilder
            sb.append(BASE_GET_USERS)
            sb.append("WHERE ID IN (" + userIds.toList.as("?").mkString(",") + ")")
            startFrom.foreach(start => sb.append(s" AND id > '$start'"))
            sb.append(" ORDER BY id ASC")
            // Grab one more than the maxItem limit, if provided, to determine whether nextId should be returned
            maxItems.foreach(limit => sb.append(s" LIMIT ${limit + 1}"))
            val query = sb.toString
            SQL(query)
              .bind(userIds.toList: _*)
              .map(toUser(1))
              .list()
              .apply()
          }

          maxItems match {
            case Some(limit) =>
              val returnUsers = users.take(limit)
              if (users.size == limit + 1) ListUsersResults(returnUsers, Some(returnUsers.last.id))
              else ListUsersResults(returnUsers, None)
            case None => ListUsersResults(users, None)
          }
        }
      }
    }

  def getAllUsers: IO[List[User]] =
    monitor("repo.User.getAllUsers") {
      IO {
        DB.readOnly { implicit s =>
          SQL(BASE_GET_USERS)
            .map(toUser(1))
            .list()
            .apply()
        }
      }
    }

  def getUserByAccessKey(accessKey: String): IO[Option[User]] =
    monitor("repo.User.getUserByAccessKey") {
      IO {
        logger.debug(s"Getting user with accessKey: $accessKey")
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
        logger.debug(s"Getting user with userName: $userName")
        DB.readOnly { implicit s =>
          GET_USER_BY_USER_NAME
            .bind(userName)
            .map(toUser(1))
            .first()
            .apply()
        }
      }
    }

  /**
   * Retrieves the requested User from the database by the given userIdentifier, which can be a userId or username
   * @param userIdentifier The userId or username
   * @return The found User
   */
  def getUserByIdOrName(userIdentifier: String): IO[Option[User]] =
    monitor("repo.User.getUser") {
      val userInfo=
        if (userIdentifier.endsWith("%"))
        userIdentifier.dropRight(1)
      else  userIdentifier
      logger.debug(s"Getting user with id: $userIdentifier")
      IO {
        DB.readOnly { implicit s =>
          GET_USER_BY_ID_OR_NAME
            .bindByName('id -> userInfo,'userName ->s"$userInfo%")
            .map(toUser(1))
            .first()
            .apply()
        }
      }
    }

  def save(user: User): IO[User] =
    monitor("repo.User.save") {
      IO {
        logger.debug(s"Saving user with id: ${user.id}")
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

  def save(users: List[User]): IO[List[User]] =
    monitor("repo.User.save") {
      IO {
        logger.debug(s"Saving users with ids: ${users.map(_.id).mkString(", ")}")

        val updates = users.map { u =>
          Seq(
            'id -> u.id,
            'userName -> u.userName,
            'accessKey -> u.accessKey,
            'data -> toPB(u.withEncryptedSecretKey(cryptoAlgebra)).toByteArray
          )
        }

        DB.localTx { implicit s =>
          updates.grouped(1000).foreach { group =>
            PUT_USER.batchByName(group: _*).apply()
          }
        }

        users
      }
    }

  private def toUser(colIndex: Int): WrappedResultSet => User = res => {
    fromPB(VinylDNSProto.User.parseFrom(res.bytes(colIndex)))
  }
}
