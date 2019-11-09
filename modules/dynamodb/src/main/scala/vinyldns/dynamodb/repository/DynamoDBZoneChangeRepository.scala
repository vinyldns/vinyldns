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

import java.nio.ByteBuffer
import java.util.HashMap

import cats.effect._
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import vinyldns.core.domain.zone.{ListZoneChangesResults, ZoneChange, ZoneChangeRepository}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto

import scala.util.Try

object DynamoDBZoneChangeRepository extends ProtobufConversions {

  private[repository] val ZONE_ID = "zone_id"
  private[repository] val CHANGE_ID = "change_id"
  private[repository] val BLOB = "blob"
  private[repository] val CREATED = "created"

  private val ZONE_ID_CREATED_INDEX = "zone_id_created_index"

  def apply(
      config: DynamoDBRepositorySettings,
      dynamoConfig: DynamoDBDataStoreSettings
  ): IO[DynamoDBZoneChangeRepository] = {

    val dynamoDBHelper = new DynamoDBHelper(
      DynamoDBClient(dynamoConfig),
      LoggerFactory.getLogger("DynamoDBZoneChangeRepository")
    )

    val dynamoReads = config.provisionedReads
    val dynamoWrites = config.provisionedWrites
    val tableName = config.tableName

    val tableAttributes = Seq(
      new AttributeDefinition(CHANGE_ID, "S"),
      new AttributeDefinition(ZONE_ID, "S"),
      new AttributeDefinition(CREATED, "N")
    )

    val secondaryIndexes = Seq(
      new GlobalSecondaryIndex()
        .withIndexName(ZONE_ID_CREATED_INDEX)
        .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
        .withKeySchema(
          new KeySchemaElement(ZONE_ID, KeyType.HASH),
          new KeySchemaElement(CREATED, KeyType.RANGE)
        )
        .withProjection(new Projection().withProjectionType("ALL"))
    )

    val setup = dynamoDBHelper.setupTable(
      new CreateTableRequest()
        .withTableName(tableName)
        .withAttributeDefinitions(tableAttributes: _*)
        .withKeySchema(
          new KeySchemaElement(ZONE_ID, KeyType.HASH),
          new KeySchemaElement(CHANGE_ID, KeyType.RANGE)
        )
        .withGlobalSecondaryIndexes(secondaryIndexes: _*)
        .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
    )

    setup.as(new DynamoDBZoneChangeRepository(tableName, dynamoDBHelper))
  }
}

class DynamoDBZoneChangeRepository private[repository] (
    zoneChangeTable: String,
    val dynamoDBHelper: DynamoDBHelper
) extends ZoneChangeRepository
    with ProtobufConversions
    with Monitored {

  import scala.collection.JavaConverters._
  import DynamoDBZoneChangeRepository._

  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_.isAfter(_))

  val log = LoggerFactory.getLogger(classOf[DynamoDBZoneChangeRepository])

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
      maxItems: Int = 100
  ): IO[ListZoneChangesResults] =
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
    item.put(BLOB, new AttributeValue().withB(bb))
    item.put(CREATED, new AttributeValue().withN(zoneChange.created.getMillis.toString))
    item
  }
}
