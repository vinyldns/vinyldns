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

import java.util.{Collections, HashMap}

import cats.effect._
import com.amazonaws.services.dynamodbv2.model._
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.domain.membership.MembershipRepository
import vinyldns.core.route.Monitored

import scala.collection.JavaConverters._

object DynamoDBMembershipRepository {

  private[repository] val USER_ID = "user_id"
  private[repository] val GROUP_ID = "group_id"

  def apply(
      config: DynamoDBRepositorySettings,
      dynamoConfig: DynamoDBDataStoreSettings
  ): IO[DynamoDBMembershipRepository] = {

    val dynamoDBHelper = new DynamoDBHelper(
      DynamoDBClient(dynamoConfig),
      LoggerFactory.getLogger("DynamoDBMembershipRepository")
    )

    val dynamoReads = config.provisionedReads
    val dynamoWrites = config.provisionedWrites
    val tableName = config.tableName

    val tableAttributes = Seq(
      new AttributeDefinition(USER_ID, "S"),
      new AttributeDefinition(GROUP_ID, "S")
    )

    val setup = dynamoDBHelper.setupTable(
      new CreateTableRequest()
        .withTableName(tableName)
        .withAttributeDefinitions(tableAttributes: _*)
        .withKeySchema(
          new KeySchemaElement(USER_ID, KeyType.HASH),
          new KeySchemaElement(GROUP_ID, KeyType.RANGE)
        )
        .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
    )

    setup.as(new DynamoDBMembershipRepository(tableName, dynamoDBHelper))
  }
}

class DynamoDBMembershipRepository private[repository] (
    membershipTable: String,
    dynamoDBHelper: DynamoDBHelper
) extends MembershipRepository
    with Monitored {

  import DynamoDBMembershipRepository._

  val log: Logger = LoggerFactory.getLogger("DynamoDBMembershipRepository")

  def getGroupsForUser(userId: String): IO[Set[String]] =
    monitor("repo.Membership.getGroupsForUser") {
      log.info(s"Getting groups by user id $userId")
      val expressionAttributeValues = new HashMap[String, AttributeValue]
      expressionAttributeValues.put(":userId", new AttributeValue(userId))

      val keyConditionExpression: String = "#user_id_attribute = :userId"

      val expressionAttributeNames = new HashMap[String, String]
      expressionAttributeNames.put("#user_id_attribute", USER_ID)

      val queryRequest = new QueryRequest()
        .withTableName(membershipTable)
        .withKeyConditionExpression(keyConditionExpression)
        .withExpressionAttributeNames(expressionAttributeNames)
        .withExpressionAttributeValues(expressionAttributeValues)

      dynamoDBHelper.query(queryRequest).map(result => result.getItems.asScala.map(fromItem).toSet)
    }

  def saveMembers(groupId: String, memberUserIds: Set[String], isAdmin: Boolean): IO[Set[String]] =
    monitor("repo.Membership.addMembers") {
      log.info(s"Saving members for group $groupId")

      val items = memberUserIds.toList
        .map(toItem(_, groupId))

      val result = executeBatch(items) { item =>
        new WriteRequest().withPutRequest(new PutRequest().withItem(item))
      }

      // Assuming we succeeded, then return user ids
      result.map(_ => memberUserIds)
    }

  def removeMembers(groupId: String, memberUserIds: Set[String]): IO[Set[String]] =
    monitor("repo.Membership.removeMembers") {
      log.info(s"Removing members for group $groupId")

      val items = memberUserIds.toList
        .map(toItem(_, groupId))

      val result = executeBatch(items) { item =>
        new WriteRequest().withDeleteRequest(new DeleteRequest().withKey(item))
      }

      // Assuming we succeeded, then return user ids
      result.map(_ => memberUserIds)
    }

  private def executeBatch(
      items: Iterable[java.util.Map[String, AttributeValue]]
  )(f: java.util.Map[String, AttributeValue] => WriteRequest): IO[List[BatchWriteItemResult]] = {
    val MaxDynamoBatchWriteSize = 25
    val batchWrites =
      items.toList
        .map(item => f(item))
        .grouped(MaxDynamoBatchWriteSize)
        .map(
          itemGroup =>
            new BatchWriteItemRequest()
              .withRequestItems(Collections.singletonMap(membershipTable, itemGroup.asJava))
              .withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL)
        )

    // Fold left will attempt each batch sequentially, and fail fast on error
    batchWrites.foldLeft(IO.pure(List.empty[BatchWriteItemResult])) {
      case (acc, batch) =>
        acc.flatMap { lst =>
          dynamoDBHelper.batchWriteItem(membershipTable, batch).map(result => result :: lst)
        }
    }
  }

  private[repository] def toItem(
      userId: String,
      groupId: String
  ): java.util.Map[String, AttributeValue] = {
    val item = new java.util.HashMap[String, AttributeValue]()
    item.put(USER_ID, new AttributeValue(userId))
    item.put(GROUP_ID, new AttributeValue(groupId))
    item
  }

  private[repository] def fromItem(item: java.util.Map[String, AttributeValue]): String =
    try {
      item.get(GROUP_ID).getS
    } catch {
      case ex: Throwable =>
        log.error("fromItem", ex)
        throw new UnexpectedDynamoResponseException(ex.getMessage, ex)
    }
}
