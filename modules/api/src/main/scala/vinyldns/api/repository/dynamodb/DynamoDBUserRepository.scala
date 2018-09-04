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

package vinyldns.api.repository.dynamodb

import java.util
import java.util.HashMap

import cats.effect._
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model._
import com.typesafe.config.Config
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.VinylDNSConfig
import vinyldns.core.domain.membership.{ListUsersResults, User, UserRepository}
import vinyldns.core.route.Monitored

import scala.collection.JavaConverters._

object DynamoDBUserRepository {

  def apply(
      config: Config = VinylDNSConfig.usersStoreConfig,
      dynamoConfig: Config = VinylDNSConfig.dynamoConfig): DynamoDBUserRepository =
    new DynamoDBUserRepository(
      config,
      new DynamoDBHelper(
        DynamoDBClient(dynamoConfig),
        LoggerFactory.getLogger("DynamoDBUserRepository")))
}

class DynamoDBUserRepository(
    config: Config = VinylDNSConfig.usersStoreConfig,
    dynamoDBHelper: DynamoDBHelper)
    extends UserRepository
    with Monitored {

  val log: Logger = LoggerFactory.getLogger(classOf[DynamoDBUserRepository])

  private[repository] val USER_ID = "userid"
  private[repository] val USER_NAME = "username"
  private[repository] val FIRST_NAME = "firstname"
  private[repository] val LAST_NAME = "lastname"
  private[repository] val EMAIL = "email"
  private[repository] val CREATED = "created"
  private[repository] val ACCESS_KEY = "accesskey"
  private[repository] val SECRET_KEY = "secretkey"
  private[repository] val IS_SUPER = "super"
  private[repository] val USER_NAME_INDEX_NAME = "username_index"
  private[repository] val ACCESS_KEY_INDEX_NAME = "access_key_index"

  private val dynamoReads = config.getLong("dynamo.provisionedReads")
  private val dynamoWrites = config.getLong("dynamo.provisionedWrites")
  private[repository] val USER_TABLE = config.getString("dynamo.tableName")

  private[repository] val tableAttributes = Seq(
    new AttributeDefinition(USER_ID, "S"),
    new AttributeDefinition(USER_NAME, "S"),
    new AttributeDefinition(ACCESS_KEY, "S")
  )

  private[repository] val secondaryIndexes = Seq(
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

  dynamoDBHelper.setupTable(
    new CreateTableRequest()
      .withTableName(USER_TABLE)
      .withAttributeDefinitions(tableAttributes: _*)
      .withKeySchema(new KeySchemaElement(USER_ID, KeyType.HASH))
      .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
      .withGlobalSecondaryIndexes(secondaryIndexes: _*)
  )

  def getUser(userId: String): IO[Option[User]] =
    monitor("repo.User.getUser") {
      log.info(s"Getting user by id $userId")

      val key = new HashMap[String, AttributeValue]()
      key.put(USER_ID, new AttributeValue(userId))
      val request = new GetItemRequest().withTableName(USER_TABLE).withKey(key)

      dynamoDBHelper.getItem(request).map(result => Option(result.getItem).map(fromItem))
    }

  def getUsers(
      userIds: Set[String],
      exclusiveStartKey: Option[String],
      pageSize: Option[Int]): IO[ListUsersResults] = {

    def toBatchGetItemRequest(userIds: List[String]): BatchGetItemRequest = {
      val allKeys = new util.ArrayList[util.Map[String, AttributeValue]]()

      for { userId <- userIds } {
        val key = new util.HashMap[String, AttributeValue]()
        key.put(USER_ID, new AttributeValue(userId))
        allKeys.add(key)
      }

      val keysAndAttributes = new KeysAndAttributes().withKeys(allKeys)

      val request = new util.HashMap[String, KeysAndAttributes]()
      request.put(USER_TABLE, keysAndAttributes)

      new BatchGetItemRequest().withRequestItems(request)
    }

    def parseUsers(result: BatchGetItemResult): List[User] = {
      val userAttributes = result.getResponses.asScala.get(USER_TABLE)
      userAttributes match {
        case None =>
          List()
        case Some(items) =>
          items.asScala.toList.map(fromItem)
      }
    }

    monitor("repo.User.getUsers") {
      log.info(s"Getting users by id $userIds")

      val sortedUserIds = userIds.toList.sorted

      val filtered = exclusiveStartKey match {
        case None => sortedUserIds
        case Some(startId) => sortedUserIds.filter(startId < _)
      }

      val page = pageSize match {
        case None => filtered
        case Some(size) => filtered.take(size)
      }

      // Group the user ids into batches of 100, that is the max size of the BatchGetItemRequest
      val batches = page.grouped(100).toList

      val batchGets = batches.map(toBatchGetItemRequest)

      // run the batches in parallel
      val batchGetIo = batchGets.map(dynamoDBHelper.batchGetItem)

      val allBatches: IO[List[BatchGetItemResult]] = batchGetIo.sequence

      val allUsers = allBatches.map { batchGetItemResults =>
        batchGetItemResults.flatMap(parseUsers)
      }

      allUsers.map { list =>
        val lastEvaluatedId =
          if (filtered.size > list.size) list.sortBy(_.id).lastOption.map(_.id) else None
        ListUsersResults(list, lastEvaluatedId)
      }
    }
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
        .withTableName(USER_TABLE)
        .withIndexName(ACCESS_KEY_INDEX_NAME)
        .withExpressionAttributeNames(expressionAttributeNames)
        .withExpressionAttributeValues(expressionAttributeValues)
        .withKeyConditionExpression(keyConditionExpression)

      dynamoDBHelper.query(queryRequest).map { results =>
        results.getItems.asScala.headOption.map(fromItem)
      }
    }

  def save(user: User): IO[User] = //For testing purposes
    monitor("repo.User.save") {
      log.info(s"Saving user id: ${user.id} name: ${user.userName}.")

      val item = toItem(user)
      val request = new PutItemRequest().withTableName(USER_TABLE).withItem(item)
      dynamoDBHelper.putItem(request).map(_ => user)
    }

  def toItem(user: User): java.util.Map[String, AttributeValue] = {
    val item = new java.util.HashMap[String, AttributeValue]()
    item.put(USER_ID, new AttributeValue(user.id))
    item.put(USER_NAME, new AttributeValue(user.userName))
    item.put(CREATED, new AttributeValue().withN(user.created.getMillis.toString))
    item.put(ACCESS_KEY, new AttributeValue(user.accessKey))
    item.put(SECRET_KEY, new AttributeValue(user.secretKey))
    item.put(IS_SUPER, new AttributeValue().withBOOL(user.isSuper))

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

  def fromItem(item: java.util.Map[String, AttributeValue]): User =
    User(
      id = item.get(USER_ID).getS,
      userName = item.get(USER_NAME).getS,
      created = new DateTime(item.get(CREATED).getN.toLong),
      accessKey = item.get(ACCESS_KEY).getS,
      secretKey = item.get(SECRET_KEY).getS,
      firstName = if (item.get(FIRST_NAME) == null) None else Option(item.get(FIRST_NAME).getS),
      lastName = if (item.get(LAST_NAME) == null) None else Option(item.get(LAST_NAME).getS),
      email = if (item.get(EMAIL) == null) None else Option(item.get(EMAIL).getS),
      isSuper = if (item.get(IS_SUPER) == null) false else item.get(IS_SUPER).getBOOL
    )
}
