package vinyldns.mysql.repository

import cats.effect.IO
import org.slf4j.LoggerFactory
import scalikejdbc._
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.membership.{ListUsersResults, User, UserRepository}
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto
import vinyldns.core.protobuf.ProtobufConversions

class MySqlUserRepository
(crypto: CryptoAlgebra)
  extends UserRepository
    with Monitored
    with ProtobufConversions {

  private final val logger = LoggerFactory.getLogger(classOf[MySqlZoneChangeRepository])

  private final val PUT_USER =
    sql"""
         | REPLACE INTO user (id, user_name, created_timestamp, access_key, data)
         |  VALUES ({id}, {userName}, {createdTimestamp}, {accessKey}, {data})
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

  private final val LIST_USERS =
    sql"""
         | SELECT data
         |  FROM user
         |  WHERE id = {id} AND created_timestamp <= {startFrom}
         |  ORDER BY created_timestamp DESC
         |  LIMIT {maxItems}
       """.stripMargin

  override def getUser(userId: String): IO[Option[User]] =
    monitor("repo.User.getUser") {
      IO {
        DB.readOnly { implicit s =>
          GET_USER_BY_ID
            .bind(userId)
            .map(extractUser(1))
            .first()
            .apply()
        }
      }
    }

  override def getUsers(userIds: Set[String], exclusiveStartKey: Option[String], pageSize: Option[Int]): IO[ListUsersResults] = ???

  override def getUserByAccessKey(accessKey: String): IO[Option[User]] =
    monitor("repo.User.getUserByAccessKey") {
      IO {
        DB.readOnly { implicit s =>
          GET_USER_BY_ACCESS_KEY
            .bind(accessKey)
            .map(extractUser(1))
            .first()
            .apply()
        }
      }
    }

  override def getUserByName(userName: String): IO[Option[User]] =
    monitor("repo.User.getUserByName") {
      IO {
        DB.readOnly { implicit s =>
          GET_USER_BY_USER_NAME
            .bind(userName)
            .map(extractUser(1))
            .first()
            .apply()
        }
      }
    }

  override def save(user: User): IO[User] = {
    monitor("repo.User.save") {
      IO {
        logger.info(s"Saving user ${user.id}")
        DB.localTx { implicit s =>
          PUT_USER
            .bindByName(
              'id -> user.id,
              'userName -> user.userName,
              'createdTimestamp -> user.created.getMillis,
              'accessKey -> user.accessKey,
              'data -> toPB(user, crypto).toByteArray
            )
        }
        user
      }
    }
  }

  private def extractUser(colIndex: Int): WrappedResultSet => User = res => {
    fromPB(VinylDNSProto.User.parseFrom(res.bytes(colIndex)))
  }

}
