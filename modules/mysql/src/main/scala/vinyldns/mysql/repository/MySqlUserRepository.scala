package vinyldns.mysql.repository

import cats.effect.IO
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc._
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.membership.LockStatus.LockStatus
import vinyldns.core.domain.membership.{ListUsersResults, LockStatus, User, UserRepository}
import vinyldns.core.route.Monitored

import scala.util.Try

class MySqlUserRepository
(crypto: CryptoAlgebra)
  extends UserRepository
    with Monitored {

  private final val logger = LoggerFactory.getLogger(classOf[MySqlZoneChangeRepository])

  private final val PUT_USER =
    sql"""
         | REPLACE INTO user
         |  (user_id,
         |   user_name,
         |   created_timestamp,
         |   access_key,
         |   secret_key,
         |   is_super,
         |   lock_status,
         |   first_name,
         |   last_name,
         |   email)
         | VALUES
         |  ({user_id},
         |   {user_name},
         |   {created_timestamp},
         |   {access_key},
         |   {secret_key},
         |   {is_super},
         |   {lock_status},
         |   {first_name},
         |   {last_name},
         |   {email})
       """

  private final val GET_USER =

  override def getUser(userId: String): IO[Option[User]] = ???

  override def getUsers(userIds: Set[String], exclusiveStartKey: Option[String], pageSize: Option[Int]): IO[ListUsersResults] = ???

  override def getUserByAccessKey(accessKey: String): IO[Option[User]] = ???

  override def getUserByName(userName: String): IO[Option[User]] = ???

  override def save(user: User): IO[User] = {
    monitor("repo.User.save") {
      IO {
        logger.info(s"Saving user ${user.id}")
        val mySqlUser = MySqlUser(user, crypto)
        DB.localTx { implicit s =>
          PUT_USER
            .bindByName(
              'user_id -> mySqlUser.user_id,
              'user_name -> mySqlUser.user_name,
              'created_timestamp -> mySqlUser.created_timestamp,
              'access_key -> mySqlUser.access_key,
              'secret_key -> mySqlUser.secret_key,
              'is_super -> mySqlUser.is_super,
              'lock_status -> mySqlUser.lock_status,
              'first_name -> mySqlUser.first_name,
              'last_name -> mySqlUser.last_name,
              'email -> mySqlUser.email
            )
        }
        user
      }
    }
  }

}

case class MySqlUser(user_id: String,
                     user_name: String,
                     created_timestamp: Long,
                     access_key: String,
                     secret_key: String,
                     is_super: Int,
                     lock_status: String,
                     first_name: Option[String],
                     last_name: Option[String],
                     email: Option[String])

object MySqlUser {
  def apply(user: User, crypto: CryptoAlgebra): MySqlUser = new MySqlUser(
    user.id,
    user.userName,
    user.created.getMillis,
    user.accessKey,
    crypto.encrypt(user.secretKey),
    toMySqlIsSuper(user.isSuper),
    user.lockStatus.toString,
    user.firstName,
    user.lastName,
    user.email)

  def fromMySqlLockStatus(str: String, logger: Logger): LockStatus = Try(LockStatus.withName(str)).getOrElse {
    logger.error(s"Invalid locked status value '$str'; defaulting to unlocked")
    LockStatus.Unlocked
  }

  def toMySqlIsSuper(isSuper: Boolean): Int = {
    if (isSuper) 1
    else 0
  }

  def fromMySqlIsSuper(isSuper: Int): Boolean = isSuper == 1
}
