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
import org.slf4j.LoggerFactory
import vinyldns.api.VinylDNSConfig
import vinyldns.core.domain.zone.ZoneChangeStatus.ZoneChangeStatus
import vinyldns.core.domain.zone.{
  ListZoneChangesResults,
  ZoneChange,
  ZoneChangeRepository,
  ZoneChangeStatus
}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto

import scala.util.Try

object DynamoDBZoneChangeRepository extends ProtobufConversions {

  def apply(
      config: Config = VinylDNSConfig.zoneChangeStoreConfig,
      dynamoConfig: Config = VinylDNSConfig.dynamoConfig): DynamoDBZoneChangeRepository =
    new DynamoDBZoneChangeRepository(
      config,
      new DynamoDBHelper(
        DynamoDBClient(dynamoConfig),
        LoggerFactory.getLogger("DynamoDBZoneChangeRepository")))
}

class DynamoDBZoneChangeRepository(
    config: Config = VinylDNSConfig.zoneChangeStoreConfig,
    dynamoDBHelper: DynamoDBHelper)
    extends ZoneChangeRepository
    with ProtobufConversions
    with Monitored {

  import scala.collection.JavaConverters._

  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_.isAfter(_))

  private[repository] val ZONE_ID = "zone_id"
  private[repository] val CHANGE_ID = "change_id"
  private[repository] val STATUS = "status"
  private[repository] val BLOB = "blob"
  private[repository] val CREATED = "created"

  private val ZONE_ID_STATUS_INDEX_NAME = "zone_id_status_index"
  private val STATUS_INDEX_NAME = "status_zone_id_index"
  private val ZONE_ID_CREATED_INDEX = "zone_id_created_index"

  val log = LoggerFactory.getLogger(classOf[DynamoDBZoneChangeRepository])
  private val dynamoReads = config.getLong("dynamo.provisionedReads")
  private val dynamoWrites = config.getLong("dynamo.provisionedWrites")
  private[repository] val zoneChangeTable = config.getString("dynamo.tableName")

  private[repository] val tableAttributes = Seq(
    new AttributeDefinition(CHANGE_ID, "S"),
    new AttributeDefinition(ZONE_ID, "S"),
    new AttributeDefinition(STATUS, "S"),
    new AttributeDefinition(CREATED, "N")
  )

  private[repository] val secondaryIndexes = Seq(
    new GlobalSecondaryIndex()
      .withIndexName(ZONE_ID_STATUS_INDEX_NAME)
      .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
      .withKeySchema(
        new KeySchemaElement(ZONE_ID, KeyType.HASH),
        new KeySchemaElement(STATUS, KeyType.RANGE))
      .withProjection(new Projection().withProjectionType("ALL")),
    new GlobalSecondaryIndex()
      .withIndexName(STATUS_INDEX_NAME)
      .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
      .withKeySchema(
        new KeySchemaElement(STATUS, KeyType.HASH),
        new KeySchemaElement(ZONE_ID, KeyType.RANGE))
      .withProjection(new Projection().withProjectionType("KEYS_ONLY")),
    new GlobalSecondaryIndex()
      .withIndexName(ZONE_ID_CREATED_INDEX)
      .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
      .withKeySchema(
        new KeySchemaElement(ZONE_ID, KeyType.HASH),
        new KeySchemaElement(CREATED, KeyType.RANGE))
      .withProjection(new Projection().withProjectionType("ALL"))
  )

  dynamoDBHelper.setupTable(
    new CreateTableRequest()
      .withTableName(zoneChangeTable)
      .withAttributeDefinitions(tableAttributes: _*)
      .withKeySchema(
        new KeySchemaElement(ZONE_ID, KeyType.HASH),
        new KeySchemaElement(CHANGE_ID, KeyType.RANGE))
      .withGlobalSecondaryIndexes(secondaryIndexes: _*)
      .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
  )

  def save(zoneChange: ZoneChange): IO[ZoneChange] =
    monitor("repo.ZoneChange.save") {
      log.info(s"Saving zone change ${zoneChange.id}")
      val item = toItem(zoneChange)
      val request = new PutItemRequest().withTableName(zoneChangeTable).withItem(item)

      dynamoDBHelper.putItem(request).map(_ => zoneChange)
    }

  def listZoneChanges(
      zoneId: String,
      startFrom: Option[String] = None,
      maxItems: Int = 100): IO[ListZoneChangesResults] =
    monitor("repo.ZoneChange.getChanges") {
      log.info(s"Getting zone changes for zone $zoneId")

      // millisecond string
      val startTime = startFrom.getOrElse(DateTime.now.getMillis.toString)

      val expressionAttributeValues = new HashMap[String, AttributeValue]
      expressionAttributeValues.put(":zone_id", new AttributeValue(zoneId))
      expressionAttributeValues.put(":created", new AttributeValue().withN(startTime))

      val expressionAttributeNames = new HashMap[String, String]
      expressionAttributeNames.put("#zone_id_attribute", ZONE_ID)
      expressionAttributeNames.put("#created_attribute", CREATED)

      val keyConditionExpression: String =
        "#zone_id_attribute = :zone_id AND #created_attribute < :created"

      val queryRequest = new QueryRequest()
        .withTableName(zoneChangeTable)
        .withIndexName(ZONE_ID_CREATED_INDEX)
        .withExpressionAttributeNames(expressionAttributeNames)
        .withExpressionAttributeValues(expressionAttributeValues)
        .withKeyConditionExpression(keyConditionExpression)
        .withScanIndexForward(false) // return in descending order by sort key
        .withLimit(maxItems)

      dynamoDBHelper.queryAll(queryRequest).map { resultList =>
        val items = resultList.flatMap { result =>
          result.getItems.asScala.map(fromItem).distinct
        }
        val nextId = Try(resultList.last.getLastEvaluatedKey.get("created").getN).toOption
        ListZoneChangesResults(items, nextId, startFrom, maxItems)
      }
    }

  def getPending(zoneId: String): IO[List[ZoneChange]] =
    monitor("repo.ZoneChange.getPending") {
      log.info(s"Getting pending zone changes for zone $zoneId")
      for {
        pending <- getChangesByStatus(zoneId, ZoneChangeStatus.Pending)
        notSynced <- getChangesByStatus(zoneId, ZoneChangeStatus.Complete)
      } yield (pending ++ notSynced).sortBy(_.created)
    }

  def getAllPendingZoneIds: IO[List[String]] = {
    val expressionAttributeValues = new HashMap[String, AttributeValue]
    val expressionAttributeNames = new HashMap[String, String]
    expressionAttributeNames.put("#status_attribute", STATUS)
    expressionAttributeValues.put(":status", new AttributeValue("Pending"))
    val keyConditionExpression: String = "#status_attribute = :status"
    val queryRequest = new QueryRequest()
      .withTableName(zoneChangeTable)
      .withIndexName(STATUS_INDEX_NAME)
      .withExpressionAttributeNames(expressionAttributeNames)
      .withExpressionAttributeValues(expressionAttributeValues)
      .withKeyConditionExpression(keyConditionExpression)

    dynamoDBHelper.queryAll(queryRequest).map { queryResults =>
      queryResults.flatMap { queryResult =>
        queryResult.getItems.asScala.toList.map { item =>
          item.get(ZONE_ID).getS()
        }
      }.distinct
    }
  }

  private def getChangesByStatus(
      zoneId: String,
      changeStatus: ZoneChangeStatus): IO[List[ZoneChange]] = {
    val expressionAttributeValues = new HashMap[String, AttributeValue]
    expressionAttributeValues.put(":status", new AttributeValue(changeStatus.toString))
    expressionAttributeValues.put(":zone_id", new AttributeValue(zoneId))

    val expressionAttributeNames = new HashMap[String, String]
    expressionAttributeNames.put("#status_attribute", STATUS)
    expressionAttributeNames.put("#zone_id_attribute", ZONE_ID)

    val keyConditionExpression: String =
      "#status_attribute = :status and #zone_id_attribute = :zone_id"

    val queryRequest = new QueryRequest()
      .withTableName(zoneChangeTable)
      .withIndexName(ZONE_ID_STATUS_INDEX_NAME)
      .withExpressionAttributeNames(expressionAttributeNames)
      .withExpressionAttributeValues(expressionAttributeValues)
      .withKeyConditionExpression(keyConditionExpression)

    dynamoDBHelper
      .query(queryRequest)
      .map(result => result.getItems.asScala.toList.map(fromItem).distinct)
  }

  def fromItem(item: java.util.Map[String, AttributeValue]): ZoneChange =
    try {
      val blob = item.get(BLOB)
      fromPB(VinylDNSProto.ZoneChange.parseFrom(blob.getB.array()))
    } catch {
      case ex: Throwable =>
        log.error("fromItem", ex)
        throw new UnexpectedDynamoResponseException(ex.getMessage, ex)
    }

  def toItem(zoneChange: ZoneChange): java.util.HashMap[String, AttributeValue] = {
    val blob = toPB(zoneChange).toByteArray

    val bb = ByteBuffer.allocate(blob.length)
    bb.put(blob)
    bb.position(0)

    val item = new java.util.HashMap[String, AttributeValue]()

    item.put(CHANGE_ID, new AttributeValue(zoneChange.id))
    item.put(ZONE_ID, new AttributeValue(zoneChange.zoneId))
    item.put(STATUS, new AttributeValue(zoneChange.status.toString))
    item.put(BLOB, new AttributeValue().withB(bb))
    item.put(CREATED, new AttributeValue().withN(zoneChange.created.getMillis.toString))
    item
  }
}
