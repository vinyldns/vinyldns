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

package vinyldns.dynamodb.repository

import java.util
import java.util.HashMap

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model._
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.crypto.CryptoAlgebra
import vinyldns.core.domain.membership.LockStatus.LockStatus
import vinyldns.core.domain.membership.{ListUsersResults, LockStatus, User, UserRepository}
import vinyldns.core.logging.StructuredArgs._
import vinyldns.core.route.Monitored

import scala.collection.JavaConverters._
import scala.util.Try

object DynamoDBUserRepository {

  private[repository] val USER_ID = "userid"
  private[repository] val USER_NAME = "username"
  private[repository] val FIRST_NAME = "firstname"
  private[repository] val LAST_NAME = "lastname"
  private[repository] val EMAIL = "email"
  private[repository] val CREATED = "created"
  private[repository] val ACCESS_KEY = "accesskey"
  private[repository] val SECRET_KEY = "secretkey"
  private[repository] val IS_SUPER = "super"
  private[repository] val LOCK_STATUS = "lockstatus"
  private[repository] val IS_SUPPORT = "support"
  private[repository] val IS_TEST_USER = "istest"
  private[repository] val USER_NAME_INDEX_NAME = "username_index"
  private[repository] val ACCESS_KEY_INDEX_NAME = "access_key_index"
  private val log: Logger = LoggerFactory.getLogger(classOf[DynamoDBUserRepository])
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  def apply(
      config: DynamoDBRepositorySettings,
      dynamoConfig: DynamoDBDataStoreSettings,
      crypto: CryptoAlgebra): IO[DynamoDBUserRepository] = {

    val dynamoDBHelper = new DynamoDBHelper(
      DynamoDBClient(dynamoConfig),
      LoggerFactory.getLogger("DynamoDBUserRepository"))

    val dynamoReads = config.provisionedReads
    val dynamoWrites = config.provisionedWrites
    val tableName = config.tableName

    val tableAttributes = Seq(
      new AttributeDefinition(USER_ID, "S"),
      new AttributeDefinition(USER_NAME, "S"),
      new AttributeDefinition(ACCESS_KEY, "S")
    )

    val secondaryIndexes = Seq(
      new GlobalSecondaryIndex()
        .withIndexName(USER_NAME_INDEX_NAME)
        .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
        .withKeySchema(new KeySchemaElement(USER_NAME, KeyType.HASH))
        .withProjection(new Projection().withProjectionType("ALL")),
      new GlobalSecondaryIndex()
        .withIndexName(ACCESS_KEY_INDEX_NAME)
        .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
        .withKeySchema(new KeySchemaElement(ACCESS_KEY, KeyType.HASH))
        .withProjection(new Projection().withProjectionType("ALL"))
    )

    val setup = dynamoDBHelper.setupTable(
      new CreateTableRequest()
        .withTableName(tableName)
        .withAttributeDefinitions(tableAttributes: _*)
        .withKeySchema(new KeySchemaElement(USER_ID, KeyType.HASH))
        .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
        .withGlobalSecondaryIndexes(secondaryIndexes: _*)
    )

    setup.as(new DynamoDBUserRepository(tableName, dynamoDBHelper, toItem(crypto, _), fromItem))
  }

  def toItem(crypto: CryptoAlgebra, user: User): java.util.Map[String, AttributeValue] = {
    val item = new java.util.HashMap[String, AttributeValue]()
    item.put(USER_ID, new AttributeValue(user.id))
    item.put(USER_NAME, new AttributeValue(user.userName))
    item.put(CREATED, new AttributeValue().withN(user.created.getMillis.toString))
    item.put(ACCESS_KEY, new AttributeValue(user.accessKey))
    item.put(SECRET_KEY, new AttributeValue(crypto.encrypt(user.secretKey)))
    item.put(IS_SUPER, new AttributeValue().withBOOL(user.isSuper))
    item.put(IS_TEST_USER, new AttributeValue().withBOOL(user.isTest))
    item.put(LOCK_STATUS, new AttributeValue(user.lockStatus.toString))
    item.put(IS_SUPPORT, new AttributeValue().withBOOL(user.isSupport))

    val firstName =
      user.firstName.map(new AttributeValue(_)).getOrElse(new AttributeValue().withNULL(true))
    item.put(FIRST_NAME, firstName)
    val lastName =
      user.lastName.map(new AttributeValue(_)).getOrElse(new AttributeValue().withNULL(true))
    item.put(LAST_NAME, lastName)
    val email = user.email.map(new AttributeValue(_)).getOrElse(new AttributeValue().withNULL(true))
    item.put(EMAIL, email)
    item
  }

  def fromItem(item: java.util.Map[String, AttributeValue]): IO[User] = IO {
    def userStatus(str: String): LockStatus = Try(LockStatus.withName(str)).getOrElse {
      log.error(s"Invalid locked status value '$str'; defaulting to unlocked")
      LockStatus.Unlocked
    }

    User(
      id = item.get(USER_ID).getS,
      userName = item.get(USER_NAME).getS,
      created = new DateTime(item.get(CREATED).getN.toLong),
      accessKey = item.get(ACCESS_KEY).getS,
      secretKey = item.get(SECRET_KEY).getS,
      firstName = Option(item.get(FIRST_NAME)).flatMap(fn => Option(fn.getS)),
      lastName = Option(item.get(LAST_NAME)).flatMap(ln => Option(ln.getS)),
      email = Option(item.get(EMAIL)).flatMap(e => Option(e.getS)),
      isSuper = if (item.get(IS_SUPER) == null) false else item.get(IS_SUPER).getBOOL,
      lockStatus =
        if (item.get(LOCK_STATUS) == null) LockStatus.Unlocked
        else userStatus(item.get(LOCK_STATUS).getS),
      isSupport = if (item.get(IS_SUPPORT) == null) false else item.get(IS_SUPPORT).getBOOL,
      isTest = if (item.get(IS_TEST_USER) == null) false else item.get(IS_TEST_USER).getBOOL
    )
  }
}

class DynamoDBUserRepository private[repository] (
    userTableName: String,
    val dynamoDBHelper: DynamoDBHelper,
    serialize: User => java.util.Map[String, AttributeValue],
    deserialize: java.util.Map[String, AttributeValue] => IO[User])
    extends UserRepository
    with Monitored {

  import DynamoDBUserRepository._
  val log: Logger = LoggerFactory.getLogger(classOf[DynamoDBUserRepository])

  def getUser(userId: String): IO[Option[User]] =
    monitor("repo.User.getUser") {
      log.info("Getting user by id", entries(event(Read, Id(userId, "user"))))

      val key = new HashMap[String, AttributeValue]()
      key.put(USER_ID, new AttributeValue(userId))
      val request = new GetItemRequest().withTableName(userTableName).withKey(key)

      OptionT
        .liftF(dynamoDBHelper.getItem(request))
        .subflatMap(r => Option(r.getItem))
        .semiflatMap(item => deserialize(item))
        .value
    }

  def getUserByName(username: String): IO[Option[User]] = {
    log.info("Getting user by name", entries(event(Read, Id(username, "user"))))
    val attributeNames = new util.HashMap[String, String]()
    attributeNames.put("#uname", USER_NAME)
    val attributeValues = new util.HashMap[String, AttributeValue]()
    attributeValues.put(":uname", new AttributeValue().withS(username))
    val request = new QueryRequest()
      .withTableName(userTableName)
      .withKeyConditionExpression("#uname = :uname")
      .withExpressionAttributeNames(attributeNames)
      .withExpressionAttributeValues(attributeValues)
      .withIndexName(USER_NAME_INDEX_NAME)

    // the question is what to do with duplicate usernames, in the portal we just log loudly, staying the same here
    dynamoDBHelper.query(request).flatMap { result =>
      result.getItems.asScala.toList match {
        case x :: Nil => fromItem(x).map(Some(_))
        case Nil => IO.pure(None)
        case x :: _ =>
          log.error(s"Inconsistent data, multiple user records found for user name '$username'")
          fromItem(x).map(Some(_))
      }
    }
  }

  def getUsers(
      userIds: Set[String],
      startFrom: Option[String],
      maxItems: Option[Int]): IO[ListUsersResults] = {

    def toBatchGetItemRequest(userIds: List[String]): BatchGetItemRequest = {
      val allKeys = new util.ArrayList[util.Map[String, AttributeValue]]()

      for { userId <- userIds } {
        val key = new util.HashMap[String, AttributeValue]()
        key.put(USER_ID, new AttributeValue(userId))
        allKeys.add(key)
      }

      val keysAndAttributes = new KeysAndAttributes().withKeys(allKeys)

      val request = new util.HashMap[String, KeysAndAttributes]()
      request.put(userTableName, keysAndAttributes)

      new BatchGetItemRequest().withRequestItems(request)
    }

    def parseUsers(result: BatchGetItemResult): IO[List[User]] = {
      val userAttributes = result.getResponses.asScala.get(userTableName)
      userAttributes match {
        case None =>
          IO.pure(List())
        case Some(items) =>
          items.asScala.toList.map(fromItem).sequence
      }
    }

    monitor("repo.User.getUsers") {
      log.info("Getting users by ids", entries(event(ReadAll, Ids(userIds, "user"))))

      val sortedUserIds = userIds.toList.sorted

      val filtered = startFrom match {
        case None => sortedUserIds
        case Some(startId) => sortedUserIds.filter(startId < _)
      }

      val page = maxItems match {
        case None => filtered
        case Some(size) => filtered.take(size)
      }

      // Group the user ids into batches of 100, that is the max size of the BatchGetItemRequest
      val batches = page.grouped(100).toList

      val batchGets = batches.map(toBatchGetItemRequest)

      val batchGetIo = batchGets.map(dynamoDBHelper.batchGetItem)

      // run the batches in parallel
      val allBatches: IO[List[BatchGetItemResult]] = batchGetIo.parSequence

      val allUsers = for {
        batches <- allBatches
        x <- batches.foldLeft(IO(List.empty[User])) { (acc, cur) =>
          for {
            users <- parseUsers(cur)
            accumulated <- acc
          } yield users ++ accumulated
        }
      } yield x

      allUsers.map { list =>
        val lastEvaluatedId =
          if (filtered.size > list.size) list.sortBy(_.id).lastOption.map(_.id) else None
        ListUsersResults(list, lastEvaluatedId)
      }
    }
  }

  def getAllUsers: IO[List[User]] =
    monitor("repo.User.getAllUsers") {
      IO.raiseError(
        UnsupportedDynamoDBRepoFunction(
          "getAllUsers is not supported by VinylDNS DynamoDB UserRepository")
      )
    }

  def getUserByAccessKey(accessKey: String): IO[Option[User]] =
    monitor("repo.User.getUserByAccessKey") {
      log.info(s"Getting user by access key $accessKey")
      val expressionAttributeValues = new HashMap[String, AttributeValue]
      expressionAttributeValues.put(":access_key", new AttributeValue(accessKey))

      val expressionAttributeNames = new HashMap[String, String]
      expressionAttributeNames.put("#access_key_attribute", ACCESS_KEY)

      val keyConditionExpression: String = "#access_key_attribute = :access_key"

      val queryRequest = new QueryRequest()
        .withTableName(userTableName)
        .withIndexName(ACCESS_KEY_INDEX_NAME)
        .withExpressionAttributeNames(expressionAttributeNames)
        .withExpressionAttributeValues(expressionAttributeValues)
        .withKeyConditionExpression(keyConditionExpression)

      dynamoDBHelper.query(queryRequest).flatMap { results =>
        results.getItems.asScala.headOption.map(deserialize).sequence
      }
    }

  def save(user: User): IO[User] = //For testing purposes
    monitor("repo.User.save") {
      log.info("Saving user", entries(event(Save, user)))
      val request = new PutItemRequest().withTableName(userTableName).withItem(serialize(user))
      dynamoDBHelper.putItem(request).map(_ => user)
    }

  def save(users: List[User]): IO[List[User]] =
    monitor("repo.User.save") {
      IO.raiseError(
        UnsupportedDynamoDBRepoFunction(
          "batch save is not supported by VinylDNS DynamoDb UserRepository")
      )
    }
}
