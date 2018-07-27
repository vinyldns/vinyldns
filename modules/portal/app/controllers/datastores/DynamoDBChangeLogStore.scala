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

import java.util

import com.amazonaws.AmazonClientException
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model._
import controllers.{ChangeLogMessage, ChangeLogStore, UserChangeMessage}
import play.api.Configuration
import vinyldns.core.crypto.CryptoAlgebra

import scala.util.Try

class DynamoDBChangeLogStore(
    dynamo: AmazonDynamoDBClient,
    config: Configuration,
    crypto: CryptoAlgebra)
    extends ChangeLogStore {

  private val tableName = config.get[String]("changelog.tablename")
  private val readThroughput =
    config.getOptional[Long]("changelog.provisionedReadThroughput").getOrElse(1L)
  private val writeThroughput =
    config.getOptional[Long]("changelog.provisionedWriteThroughput").getOrElse(1L)

  private val TIME_STAMP = "timestamp"

  private val tableAttributes = Seq(
    new AttributeDefinition(TIME_STAMP, "S")
  )

  try {
    dynamo.describeTable(new DescribeTableRequest(tableName))
  } catch {
    case _: AmazonClientException =>
      dynamo.createTable(
        new CreateTableRequest()
          .withTableName(tableName)
          .withAttributeDefinitions(tableAttributes: _*)
          .withKeySchema(new KeySchemaElement(TIME_STAMP, KeyType.HASH))
          .withProvisionedThroughput(new ProvisionedThroughput(readThroughput, writeThroughput)))
      dynamo.describeTable(new DescribeTableRequest(tableName))
  }

  def log(change: ChangeLogMessage): Try[ChangeLogMessage] =
    Try {
      change match {
        case ucm: UserChangeMessage =>
          dynamo.putItem(tableName, toDynamoItem(ucm))
          ucm
      }
    }

  def toDynamoItem(message: UserChangeMessage): java.util.HashMap[String, AttributeValue] = {
    val item = new util.HashMap[String, AttributeValue]()
    item.put("timestamp", new AttributeValue(message.timeStamp.toString))
    item.put("userId", new AttributeValue(message.userId))
    item.put("username", new AttributeValue(message.username))
    item.put("changeType", new AttributeValue(message.changeType.toString))
    item.put(
      "updatedUser",
      new AttributeValue().withM(DynamoDBUserAccountStore.toItem(message.updatedUser, crypto)))
    message.previousUser match {
      case Some(user) =>
        item.put(
          "previousUser",
          new AttributeValue().withM(DynamoDBUserAccountStore.toItem(user, crypto)))
      case None => ()
    }
    item
  }
}
