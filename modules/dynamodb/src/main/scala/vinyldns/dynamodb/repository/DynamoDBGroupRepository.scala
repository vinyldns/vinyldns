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

import cats.effect._
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model.{CreateTableRequest, Projection, _}
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.domain.membership.GroupStatus.GroupStatus
import vinyldns.core.domain.membership.{Group, GroupRepository, GroupStatus}
import vinyldns.core.route.Monitored

import scala.collection.JavaConverters._

object DynamoDBGroupRepository {

  private[repository] val GROUP_ID = "group_id"
  private val NAME = "name"
  private val EMAIL = "email"
  private val DESCRIPTION = "desc"
  private val CREATED = "created"
  private val STATUS = "status"
  private val MEMBER_IDS = "member_ids"
  private val ADMIN_IDS = "admin_ids"
  private val GROUP_NAME_INDEX = "group_name_index"

  def apply(
      config: DynamoDBRepositorySettings,
      dynamoConfig: DynamoDBDataStoreSettings
  ): IO[DynamoDBGroupRepository] = {

    val dynamoDBHelper = new DynamoDBHelper(
      DynamoDBClient(dynamoConfig),
      LoggerFactory.getLogger(classOf[DynamoDBGroupRepository])
    )

    val dynamoReads = config.provisionedReads
    val dynamoWrites = config.provisionedWrites
    val tableName = config.tableName

    val tableAttributes = Seq(
      new AttributeDefinition(GROUP_ID, "S"),
      new AttributeDefinition(NAME, "S")
    )

    val secondaryIndexes = Seq(
      new GlobalSecondaryIndex()
        .withIndexName(GROUP_NAME_INDEX)
        .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
        .withKeySchema(new KeySchemaElement(NAME, KeyType.HASH))
        .withProjection(new Projection().withProjectionType("ALL"))
    )

    val setup = dynamoDBHelper.setupTable(
      new CreateTableRequest()
        .withTableName(tableName)
        .withAttributeDefinitions(tableAttributes: _*)
        .withKeySchema(new KeySchemaElement(GROUP_ID, KeyType.HASH))
        .withGlobalSecondaryIndexes(secondaryIndexes: _*)
        .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
    )

    setup.as(new DynamoDBGroupRepository(tableName, dynamoDBHelper))
  }
}

class DynamoDBGroupRepository private[repository] (
    groupTableName: String,
    val dynamoDBHelper: DynamoDBHelper
) extends GroupRepository
    with Monitored {

  import DynamoDBGroupRepository._

  val log: Logger = LoggerFactory.getLogger(classOf[DynamoDBGroupRepository])

  def save(group: Group): IO[Group] =
    monitor("repo.Group.save") {
      log.info(s"Saving group ${group.id} ${group.name}.")
      val item = toItem(group)
      val request = new PutItemRequest().withTableName(groupTableName).withItem(item)
      dynamoDBHelper.putItem(request).map(_ => group)
    }

  def delete(group: Group): IO[Group] =
    monitor("repo.Group.delete") {
      log.info(s"Deleting group ${group.id} ${group.name}.")
      val key = new HashMap[String, AttributeValue]()
      key.put(GROUP_ID, new AttributeValue(group.id))
      val request = new DeleteItemRequest().withTableName(groupTableName).withKey(key)
      dynamoDBHelper.deleteItem(request).map(_ => group)
    }

  /*Looks up a group.  If the group is not found, or if the group's status is Deleted, will return None */
  def getGroup(groupId: String): IO[Option[Group]] =
    monitor("repo.Group.getGroup") {
      log.info(s"Getting group $groupId.")
      val key = new HashMap[String, AttributeValue]()
      key.put(GROUP_ID, new AttributeValue(groupId))
      val request = new GetItemRequest().withTableName(groupTableName).withKey(key)

      dynamoDBHelper
        .getItem(request)
        .map { result =>
          Option(result.getItem)
            .map(fromItem)
            .filter(_.status != GroupStatus.Deleted)
        }
    }

  def getGroups(groupIds: Set[String]): IO[Set[Group]] = {

    def toBatchGetItemRequest(groupIds: Set[String]): BatchGetItemRequest = {
      val allKeys = new util.ArrayList[util.Map[String, AttributeValue]]()

      for {
        groupId <- groupIds
      } {
        val key = new util.HashMap[String, AttributeValue]()
        key.put(GROUP_ID, new AttributeValue(groupId))
        allKeys.add(key)
      }

      val keysAndAttributes = new KeysAndAttributes().withKeys(allKeys)

      val request = new util.HashMap[String, KeysAndAttributes]()
      request.put(groupTableName, keysAndAttributes)

      new BatchGetItemRequest().withRequestItems(request)
    }

    def parseGroups(result: BatchGetItemResult): Set[Group] = {
      val groupAttributes = result.getResponses.asScala.get(groupTableName)
      groupAttributes match {
        case None =>
          Set()
        case Some(items) =>
          items.asScala.toSet.map(fromItem).filter(_.status != GroupStatus.Deleted)
      }
    }

    monitor("repo.Group.getGroups") {
      log.info(s"Getting groups by id $groupIds")

      // Group the group ids into batches of 100, that is the max size of the BatchGetItemRequest
      val batches = groupIds.grouped(100).toSet

      val batchGets = batches.map(toBatchGetItemRequest)

      // run the batches in parallel
      val batchGetIo = batchGets.map(dynamoDBHelper.batchGetItem)

      val allBatches: IO[List[BatchGetItemResult]] = batchGetIo.toList.sequence

      val allGroups = allBatches.map { batchGetItemResults =>
        batchGetItemResults.flatMap(parseGroups)
      }

      allGroups.map(_.toSet)
    }
  }

  def getAllGroups(): IO[Set[Group]] =
    monitor("repo.Group.getAllGroups") {
      log.info(s"getting all group IDs")

      // filtering NOT Deleted because there is no case insensitive filter. we later filter
      // the response in case anything got through
      val scanRequest = new ScanRequest()
        .withTableName(groupTableName)
        .withFilterExpression(s"NOT (#filtername = :del)")
        .withExpressionAttributeNames(Map("#filtername" -> STATUS).asJava)
        .withExpressionAttributeValues(Map(":del" -> new AttributeValue("Deleted")).asJava)

      val scan = for {
        start <- IO(System.currentTimeMillis())
        groupsScan <- dynamoDBHelper.scanAll(scanRequest)
        end <- IO(System.currentTimeMillis())
        _ <- IO(log.debug(s"getAllGroups groups scan time: ${end - start} millis"))
      } yield groupsScan

      scan.map { results =>
        val startTime = System.currentTimeMillis()
        val groups = results
          .flatMap(_.getItems.asScala.map(fromItem))
          .filter(_.status == GroupStatus.Active)
          .toSet
        val duration = System.currentTimeMillis() - startTime
        log.debug(s"getAllGroups fromItem duration = $duration millis")

        groups
      }
    }

  def getGroupByName(groupName: String): IO[Option[Group]] =
    monitor("repo.Group.getGroupByName") {
      log.info(s"Getting group by name $groupName")
      val expressionAttributeValues = new HashMap[String, AttributeValue]
      expressionAttributeValues.put(":name", new AttributeValue(groupName))

      val expressionAttributeNames = new HashMap[String, String]
      expressionAttributeNames.put("#name_attribute", NAME)

      val keyConditionExpression: String = "#name_attribute = :name"

      val queryRequest = new QueryRequest()
        .withTableName(groupTableName)
        .withIndexName(GROUP_NAME_INDEX)
        .withExpressionAttributeNames(expressionAttributeNames)
        .withExpressionAttributeValues(expressionAttributeValues)
        .withKeyConditionExpression(keyConditionExpression)

      dynamoDBHelper.query(queryRequest).map(firstAvailableGroup)
    }

  /* Filters the results from the query so we don't return Deleted groups */
  private def toAvailableGroups(queryResult: QueryResult): List[Group] =
    queryResult.getItems.asScala.map(fromItem).filter(_.status != GroupStatus.Deleted).toList

  /* Filters the results from the query so we don't return Deleted groups */
  private def firstAvailableGroup(queryResult: QueryResult): Option[Group] =
    toAvailableGroups(queryResult).headOption

  private[repository] def toItem(group: Group) = {
    val item = new java.util.HashMap[String, AttributeValue]()
    item.put(GROUP_ID, new AttributeValue(group.id))
    item.put(NAME, new AttributeValue(group.name))
    item.put(EMAIL, new AttributeValue(group.email))
    item.put(CREATED, new AttributeValue().withN(group.created.getMillis.toString))

    val descAttr =
      group.description.map(new AttributeValue(_)).getOrElse(new AttributeValue().withNULL(true))
    item.put(DESCRIPTION, descAttr)

    item.put(STATUS, new AttributeValue(group.status.toString))
    item.put(MEMBER_IDS, new AttributeValue().withSS(group.memberIds.asJavaCollection))
    item.put(ADMIN_IDS, new AttributeValue().withSS(group.adminUserIds.asJavaCollection))
    item.put(STATUS, new AttributeValue(group.status.toString))
    item
  }

  private[repository] def fromItem(item: java.util.Map[String, AttributeValue]) = {
    val ActiveStatus = "active"
    def groupStatus(str: String): GroupStatus =
      if (str.toLowerCase == ActiveStatus) GroupStatus.Active else GroupStatus.Deleted
    try {
      Group(
        item.get(NAME).getS,
        item.get(EMAIL).getS,
        if (item.get(DESCRIPTION) == null) None else Option(item.get(DESCRIPTION).getS),
        item.get(GROUP_ID).getS,
        new DateTime(item.get(CREATED).getN.toLong),
        groupStatus(item.get(STATUS).getS),
        item.get(MEMBER_IDS).getSS.asScala.toSet,
        item.get(ADMIN_IDS).getSS.asScala.toSet
      )
    } catch {
      case ex: Throwable =>
        log.error("fromItem", ex)
        throw new UnexpectedDynamoResponseException(ex.getMessage, ex)
    }
  }
}
