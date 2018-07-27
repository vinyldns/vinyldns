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

package controllers.datastores

import controllers.UserAccountStore
import java.util

import com.amazonaws.AmazonClientException
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model._
import models.UserAccount
import org.joda.time.DateTime
import play.api.Logger
import play.api.Configuration
import vinyldns.core.crypto.CryptoAlgebra

import scala.util.Try
import scala.collection.JavaConverters._

object DynamoDBUserAccountStore {
  def getAttributeOrNone(
      items: java.util.Map[String, AttributeValue],
      attribute: String): Option[String] =
    Try(items.get(attribute).getS).toOption

  def fromItem(items: java.util.Map[String, AttributeValue], crypto: CryptoAlgebra): UserAccount = {
    val superUser: Try[Boolean] = Try(items.get("super").getBOOL)
    new UserAccount(
      items.get("userid").getS,
      items.get("username").getS,
      getAttributeOrNone(items, "firstname"),
      getAttributeOrNone(items, "lastname"),
      getAttributeOrNone(items, "email"),
      new DateTime(items.get("created").getN.toLong),
      items.get("accesskey").getS,
      crypto.decrypt(items.get("secretkey").getS),
      superUser.getOrElse(false)
    )
  }

  def toItem(ua: UserAccount, crypto: CryptoAlgebra): java.util.Map[String, AttributeValue] = {
    val item = new util.HashMap[String, AttributeValue]()
    item.put("userid", new AttributeValue().withS(ua.userId))
    item.put("username", new AttributeValue().withS(ua.username))
    ua.firstName.foreach { firstname =>
      item.put("firstname", new AttributeValue().withS(firstname))
    }
    ua.lastName.foreach { lastname =>
      item.put("lastname", new AttributeValue().withS(lastname))
    }
    ua.email.foreach { email =>
      item.put("email", new AttributeValue().withS(email))
    }
    item.put("created", new AttributeValue().withN(ua.created.getMillis.toString))
    item.put("accesskey", new AttributeValue().withS(ua.accessKey))
    item.put("secretkey", new AttributeValue().withS(crypto.encrypt(ua.accessSecret)))
    item
  }
}

class DynamoDBUserAccountStore(
    dynamo: AmazonDynamoDBClient,
    config: Configuration,
    crypto: CryptoAlgebra)
    extends UserAccountStore {
  private val tableName = config.get[String]("users.tablename")
  private val readThroughput =
    config.getOptional[Long]("users.provisionedReadThroughput").getOrElse(1L)
  private val writeThroughput =
    config.getOptional[Long]("users.provisionedWriteThroughput").getOrElse(1L)

  private val USER_ID = "userid"
  private val USER_NAME = "username"
  private val USER_INDEX_NAME = "username_index"
  private val ACCESS_KEY = "accesskey"
  private val ACCESS_KEY_INDEX_NAME = "access_key_index"

  private val tableAttributes = Seq(
    new AttributeDefinition(USER_ID, "S"),
    new AttributeDefinition(USER_NAME, "S"),
    new AttributeDefinition(ACCESS_KEY, "S")
  )

  private val gsis = Seq(
    new GlobalSecondaryIndex()
      .withIndexName(USER_INDEX_NAME)
      .withProvisionedThroughput(new ProvisionedThroughput(readThroughput, writeThroughput))
      .withKeySchema(new KeySchemaElement(USER_NAME, KeyType.HASH))
      .withProjection(new Projection().withProjectionType("ALL")),
    new GlobalSecondaryIndex()
      .withIndexName(ACCESS_KEY_INDEX_NAME)
      .withProvisionedThroughput(new ProvisionedThroughput(readThroughput, writeThroughput))
      .withKeySchema(new KeySchemaElement(ACCESS_KEY, KeyType.HASH))
      .withProjection(new Projection().withProjectionType("ALL"))
  )

  try {
    dynamo.describeTable(new DescribeTableRequest(tableName))
  } catch {
    case _: AmazonClientException =>
      dynamo.createTable(
        new CreateTableRequest()
          .withTableName(tableName)
          .withAttributeDefinitions(tableAttributes: _*)
          .withKeySchema(new KeySchemaElement(USER_ID, KeyType.HASH))
          .withGlobalSecondaryIndexes(gsis: _*)
          .withProvisionedThroughput(new ProvisionedThroughput(readThroughput, writeThroughput)))
      dynamo.describeTable(new DescribeTableRequest(tableName))
  }

  def getUserById(userId: String): Try[Option[UserAccount]] = {
    val key = new util.HashMap[String, AttributeValue]()
    key.put(USER_ID, new AttributeValue(userId))
    val request = new GetItemRequest()
      .withTableName(tableName)
      .withKey(key)
    Try {
      dynamo.getItem(request) match {
        case null => None
        // Amazon's client java docs state "If there is no matching item, GetItem does not return any data."
        // that could mean the item has no data or a null is returned, so we need to handle both cases.
        case result: GetItemResult if result.getItem == null => None
        case result: GetItemResult if result.getItem.isEmpty => None
        case result: GetItemResult =>
          Some(DynamoDBUserAccountStore.fromItem(result.getItem, crypto))
      }
    }
  }

  def getUserByName(username: String): Try[Option[UserAccount]] = {
    val attributeNames = new util.HashMap[String, String]()
    attributeNames.put("#uname", USER_NAME)
    val attributeValues = new util.HashMap[String, AttributeValue]()
    attributeValues.put(":uname", new AttributeValue().withS(username))
    val request = new QueryRequest()
      .withTableName(tableName)
      .withKeyConditionExpression("#uname = :uname")
      .withExpressionAttributeNames(attributeNames)
      .withExpressionAttributeValues(attributeValues)
      .withIndexName(USER_INDEX_NAME)
    Try {
      dynamo.query(request) match {
        case result: QueryResult if result.getCount == 1 =>
          Some(DynamoDBUserAccountStore.fromItem(result.getItems.get(0), crypto))
        case result: QueryResult if result.getCount == 0 => None
        case result: QueryResult if result.getCount >= 2 =>
          val prefixString = "!!! INCONSISTENT DATA !!!"
          Logger.error(s"$prefixString ${result.getCount} user accounts for ntid $username found!")
          for {
            item <- result.getItems.asScala
          } {
            val user = DynamoDBUserAccountStore.fromItem(item, crypto)
            Logger.error(s"$prefixString ${user.username} has user ID of ${user.userId}")
          }
          Some(DynamoDBUserAccountStore.fromItem(result.getItems.get(0), crypto))
      }
    }
  }

  def getUserByKey(key: String): Try[Option[UserAccount]] = {
    val attributeNames = new util.HashMap[String, String]()
    attributeNames.put("#ukey", ACCESS_KEY)
    val attributeValues = new util.HashMap[String, AttributeValue]()
    attributeValues.put(":ukey", new AttributeValue().withS(key))
    val request = new QueryRequest()
      .withTableName(tableName)
      .withKeyConditionExpression("#ukey = :ukey")
      .withExpressionAttributeNames(attributeNames)
      .withExpressionAttributeValues(attributeValues)
      .withIndexName(ACCESS_KEY_INDEX_NAME)
    Try {
      dynamo.query(request) match {
        case result: QueryResult if result.getCount == 1 =>
          Some(DynamoDBUserAccountStore.fromItem(result.getItems.get(0), crypto))
        case result: QueryResult if result.getCount == 0 => None
        case result: QueryResult if result.getCount >= 2 =>
          val prefixString = "!!! INCONSISTENT DATA !!!"
          Logger.error(s"$prefixString ${result.getCount} user accounts for access key $key found!")
          for {
            item <- result.getItems.asScala
          } {
            val user = DynamoDBUserAccountStore.fromItem(item, crypto)
            Logger.error(s"$prefixString ${user.username} has key of ${user.accessKey}")
          }
          Some(DynamoDBUserAccountStore.fromItem(result.getItems.get(0), crypto))
      }
    }
  }

  def storeUser(user: UserAccount): Try[UserAccount] = {
    val item = DynamoDBUserAccountStore.toItem(user, crypto)
    val request = new PutItemRequest().withItem(item).withTableName(tableName)

    Try {
      dynamo.putItem(request) match {
        case _: PutItemResult => user
      }
    }
  }
}
