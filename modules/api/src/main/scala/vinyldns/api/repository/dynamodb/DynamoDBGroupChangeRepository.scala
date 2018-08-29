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

import java.nio.ByteBuffer
import java.util.HashMap

import cats.effect._
import com.amazonaws.services.dynamodbv2.model._
import com.typesafe.config.Config
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.VinylDNSConfig
import vinyldns.api.domain.membership.{GroupChange, GroupChangeRepository, ListGroupChangesResults}
import vinyldns.api.protobuf.GroupProtobufConversions
import vinyldns.api.route.Monitored
import vinyldns.proto.VinylDNSProto

import scala.collection.JavaConverters._

object DynamoDBGroupChangeRepository {

  def apply(
      config: Config = VinylDNSConfig.groupChangesStoreConfig,
      dynamoConfig: Config = VinylDNSConfig.dynamoConfig): DynamoDBGroupChangeRepository =
    new DynamoDBGroupChangeRepository(
      config,
      new DynamoDBHelper(
        DynamoDBClient(dynamoConfig),
        LoggerFactory.getLogger(classOf[DynamoDBGroupChangeRepository])))
}

class DynamoDBGroupChangeRepository(
    config: Config = VinylDNSConfig.groupChangesStoreConfig,
    dynamoDBHelper: DynamoDBHelper)
    extends GroupChangeRepository
    with Monitored
    with GroupProtobufConversions {

  val log: Logger = LoggerFactory.getLogger(classOf[DynamoDBGroupChangeRepository])

  private[repository] val GROUP_CHANGE_ID = "group_change_id"
  private[repository] val GROUP_ID = "group_id"
  private[repository] val CREATED = "created"
  private[repository] val GROUP_CHANGE_ATTR = "group_change_blob"

  private val GROUP_ID_AND_CREATED_INDEX = "GROUP_ID_AND_CREATED_INDEX"

  private val dynamoReads = config.getLong("dynamo.provisionedReads")
  private val dynamoWrites = config.getLong("dynamo.provisionedWrites")
  private[repository] val GROUP_CHANGE_TABLE = config.getString("dynamo.tableName")

  private[repository] val tableAttributes = Seq(
    new AttributeDefinition(GROUP_ID, "S"),
    new AttributeDefinition(CREATED, "N"),
    new AttributeDefinition(GROUP_CHANGE_ID, "S")
  )

  private[repository] val secondaryIndexes = Seq(
    new GlobalSecondaryIndex()
      .withIndexName(GROUP_ID_AND_CREATED_INDEX)
      .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
      .withKeySchema(
        new KeySchemaElement(GROUP_ID, KeyType.HASH),
        new KeySchemaElement(CREATED, KeyType.RANGE))
      .withProjection(new Projection().withProjectionType("ALL"))
  )

  dynamoDBHelper.setupTable(
    new CreateTableRequest()
      .withTableName(GROUP_CHANGE_TABLE)
      .withAttributeDefinitions(tableAttributes: _*)
      .withKeySchema(new KeySchemaElement(GROUP_CHANGE_ID, KeyType.HASH))
      .withGlobalSecondaryIndexes(secondaryIndexes: _*)
      .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
  )

  def save(groupChange: GroupChange): IO[GroupChange] =
    monitor("repo.GroupChange.save") {
      log.info(s"Saving groupChange ${groupChange.id}.")
      val item = toItem(groupChange)
      val request = new PutItemRequest().withTableName(GROUP_CHANGE_TABLE).withItem(item)
      dynamoDBHelper.putItem(request).map(_ => groupChange)
    }

  def getGroupChange(groupChangeId: String): IO[Option[GroupChange]] =
    monitor("repo.GroupChange.getGroupChange") {
      log.info(s"Getting groupChange $groupChangeId.")
      val key = new HashMap[String, AttributeValue]()
      key.put(GROUP_CHANGE_ID, new AttributeValue(groupChangeId))
      val request = new GetItemRequest().withTableName(GROUP_CHANGE_TABLE).withKey(key)

      dynamoDBHelper.getItem(request).map { result =>
        Option(result.getItem).map(fromItem)
      }
    }

  def getGroupChanges(
      groupId: String,
      startFrom: Option[String],
      maxItems: Int): IO[ListGroupChangesResults] =
    monitor("repo.GroupChange.getGroupChanges") {
      log.info("Getting groupChanges")

      // millisecond string
      val startTime = startFrom.getOrElse(DateTime.now.getMillis.toString)

      val expressionAttributeValues = new HashMap[String, AttributeValue]
      expressionAttributeValues.put(":group_id", new AttributeValue(groupId))
      expressionAttributeValues.put(":created", new AttributeValue().withN(startTime))

      val expressionAttributeNames = new HashMap[String, String]
      expressionAttributeNames.put("#group_id_attribute", GROUP_ID)
      expressionAttributeNames.put("#created_attribute", CREATED)

      val keyConditionExpression: String =
        "#group_id_attribute = :group_id AND #created_attribute < :created"

      val queryRequest = new QueryRequest()
        .withTableName(GROUP_CHANGE_TABLE)
        .withIndexName(GROUP_ID_AND_CREATED_INDEX)
        .withExpressionAttributeNames(expressionAttributeNames)
        .withExpressionAttributeValues(expressionAttributeValues)
        .withKeyConditionExpression(keyConditionExpression)
        .withScanIndexForward(false) // return in descending order by sort key
        .withLimit(maxItems)

      dynamoDBHelper.query(queryRequest).map { queryResult =>
        val items = queryResult.getItems().asScala.map(fromItem).toList
        val lastEvaluatedId = Option(queryResult.getLastEvaluatedKey).flatMap(key =>
          key.asScala.get(CREATED).map(_.getN))
        ListGroupChangesResults(items, lastEvaluatedId)
      }
    }

  private[repository] def toItem(groupChange: GroupChange) = {
    val item = new java.util.HashMap[String, AttributeValue]()
    item.put(GROUP_CHANGE_ID, new AttributeValue(groupChange.id))
    item.put(GROUP_ID, new AttributeValue(groupChange.newGroup.id))
    item.put(CREATED, new AttributeValue().withN(groupChange.created.getMillis.toString)) // # of millis from epoch

    val groupChangeBlob = toPB(groupChange).toByteArray
    val bb = ByteBuffer.allocate(groupChangeBlob.length) //convert byte array to byte buffer
    bb.put(groupChangeBlob)
    bb.position(0)
    item.put(GROUP_CHANGE_ATTR, new AttributeValue().withB(bb))

    item
  }

  private[repository] def fromItem(item: java.util.Map[String, AttributeValue]) =
    try {
      val groupChangeBlob = item.get(GROUP_CHANGE_ATTR)
      fromPB(VinylDNSProto.GroupChange.parseFrom(groupChangeBlob.getB.array()))
    } catch {
      case ex: Throwable =>
        log.error("fromItem", ex)
        throw new UnexpectedDynamoResponseException(ex.getMessage, ex)
    }
}
