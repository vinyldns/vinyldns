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
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.domain.record._
import vinyldns.core.domain.record.RecordChangeRepository
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored
import vinyldns.proto.VinylDNSProto

import scala.collection.JavaConverters._
import scala.util.Try

object DynamoDBRecordChangeRepository {

  private val CHANGE_SET_ID = "change_set_id"
  private[repository] val RECORD_SET_CHANGE_ID = "record_set_change_id"
  private val CHANGE_SET_STATUS = "change_set_status"
  private val ZONE_ID = "zone_id"
  private val CREATED_TIMESTAMP = "created_timestamp"
  private val RECORD_SET_CHANGE_CREATED_TIMESTAMP = "record_set_change_created_timestamp"
  private val PROCESSING_TIMESTAMP = "processing_timestamp"
  private val RECORD_SET_CHANGE_BLOB = "record_set_change_blob"
  private val ZONE_ID_RECORD_SET_CHANGE_ID_INDEX = "zone_id_record_set_change_id_index"
  private val ZONE_ID_CREATED_INDEX = "zone_id_created_index"

  def apply(
      config: DynamoDBRepositorySettings,
      dynamoConfig: DynamoDBDataStoreSettings
  ): IO[DynamoDBRecordChangeRepository] = {

    val dynamoDBHelper = new DynamoDBHelper(
      DynamoDBClient(dynamoConfig),
      LoggerFactory.getLogger("DynamoDBRecordChangeRepository")
    )

    val dynamoReads = config.provisionedReads
    val dynamoWrites = config.provisionedWrites
    val tableName = config.tableName

    val tableAttributes =
      Seq(
        new AttributeDefinition(RECORD_SET_CHANGE_ID, "S"),
        new AttributeDefinition(ZONE_ID, "S"),
        new AttributeDefinition(RECORD_SET_CHANGE_CREATED_TIMESTAMP, "N")
      )

    val secondaryIndexes =
      Seq(
        new GlobalSecondaryIndex()
          .withIndexName(ZONE_ID_RECORD_SET_CHANGE_ID_INDEX)
          .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
          .withKeySchema(
            new KeySchemaElement(ZONE_ID, KeyType.HASH),
            new KeySchemaElement(RECORD_SET_CHANGE_ID, KeyType.RANGE)
          )
          .withProjection(new Projection().withProjectionType("ALL")),
        new GlobalSecondaryIndex()
          .withIndexName(ZONE_ID_CREATED_INDEX)
          .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
          .withKeySchema(
            new KeySchemaElement(ZONE_ID, KeyType.HASH),
            new KeySchemaElement(RECORD_SET_CHANGE_CREATED_TIMESTAMP, KeyType.RANGE)
          )
          .withProjection(new Projection().withProjectionType("ALL"))
      )

    val setup = dynamoDBHelper.setupTable(
      new CreateTableRequest()
        .withTableName(tableName)
        .withAttributeDefinitions(tableAttributes: _*)
        .withKeySchema(new KeySchemaElement(RECORD_SET_CHANGE_ID, KeyType.HASH))
        .withGlobalSecondaryIndexes(secondaryIndexes: _*)
        .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
    )

    setup.as(new DynamoDBRecordChangeRepository(tableName, dynamoDBHelper))
  }
}

class DynamoDBRecordChangeRepository private[repository] (
    recordChangeTable: String,
    val dynamoDBHelper: DynamoDBHelper
) extends RecordChangeRepository
    with ProtobufConversions
    with Monitored {

  import DynamoDBRecordChangeRepository._
  val log: Logger = LoggerFactory.getLogger("DynamoDBRecordChangeRepository")

  def toWriteRequest(changeSet: ChangeSet, change: RecordSetChange): WriteRequest =
    new WriteRequest().withPutRequest(new PutRequest().withItem(toItem(changeSet, change)))

  def save(changeSet: ChangeSet): IO[ChangeSet] =
    monitor("repo.RecordChange.save") {
      log.info(s"Saving change set ${changeSet.id} with size ${changeSet.changes.size}")
      val MaxBatchWriteGroup = 25
      val writeItems = changeSet.changes.map(change => toWriteRequest(changeSet, change))
      val batchWrites = writeItems
        .grouped(MaxBatchWriteGroup)
        .map { group =>
          dynamoDBHelper.toBatchWriteItemRequest(group, recordChangeTable)
        }
        .toList

      // Fold left will attempt each batch sequentially, and fail fast on error
      val result = batchWrites.foldLeft(IO.pure(List.empty[BatchWriteItemResult])) {
        case (acc, req) =>
          acc.flatMap { lst =>
            dynamoDBHelper
              .batchWriteItem(recordChangeTable, req)
              .map(result => result :: lst)
          }
      }

      result.map(_ => changeSet)
    }

  def listRecordSetChanges(
      zoneId: String,
      startFrom: Option[String] = None,
      maxItems: Int = 100
  ): IO[ListRecordSetChangesResults] =
    monitor("repo.RecordChange.getRecordSetChanges") {
      log.info(s"Getting record set changes for zone $zoneId")

      // millisecond string
      val startTime = startFrom.getOrElse(DateTime.now.getMillis.toString)

      val expressionAttributeValues = new HashMap[String, AttributeValue]
      expressionAttributeValues.put(":zone_id", new AttributeValue(zoneId))
      expressionAttributeValues.put(":created", new AttributeValue().withN(startTime))

      val expressionAttributeNames = new HashMap[String, String]
      expressionAttributeNames.put("#zone_id_attribute", ZONE_ID)
      expressionAttributeNames.put("#created_attribute", RECORD_SET_CHANGE_CREATED_TIMESTAMP)

      val keyConditionExpression: String =
        "#zone_id_attribute = :zone_id AND #created_attribute < :created"

      val queryRequest = new QueryRequest()
        .withTableName(recordChangeTable)
        .withIndexName(ZONE_ID_CREATED_INDEX)
        .withExpressionAttributeNames(expressionAttributeNames)
        .withExpressionAttributeValues(expressionAttributeValues)
        .withKeyConditionExpression(keyConditionExpression)
        .withScanIndexForward(false) // return in descending order by sort key
        .withLimit(maxItems)

      dynamoDBHelper.queryAll(queryRequest).map { resultList =>
        val items = resultList.flatMap { result =>
          result.getItems.asScala.map(toRecordSetChange)
        }
        val nextId = Try(
          resultList.last.getLastEvaluatedKey
            .get("record_set_change_created_timestamp")
            .getN
        ).toOption
        ListRecordSetChangesResults(items, nextId, startFrom, maxItems)
      }
    }

  def getRecordSetChange(zoneId: String, changeId: String): IO[Option[RecordSetChange]] =
    monitor("repo.RecordChange.getRecordSetChange") {
      log.info(s"Getting record set change for zone $zoneId and changeId $changeId")
      val expressionAttributeValues = new HashMap[String, AttributeValue]
      expressionAttributeValues.put(":record_set_change_id", new AttributeValue(changeId))
      expressionAttributeValues.put(":zone_id", new AttributeValue(zoneId))

      val expressionAttributeNames = new HashMap[String, String]
      expressionAttributeNames.put("#record_set_change_id_attribute", RECORD_SET_CHANGE_ID)
      expressionAttributeNames.put("#zone_id_attribute", ZONE_ID)

      val keyConditionExpression: String =
        "#record_set_change_id_attribute = :record_set_change_id and #zone_id_attribute = :zone_id"

      val queryRequest = new QueryRequest()
        .withTableName(recordChangeTable)
        .withIndexName(ZONE_ID_RECORD_SET_CHANGE_ID_INDEX)
        .withExpressionAttributeNames(expressionAttributeNames)
        .withExpressionAttributeValues(expressionAttributeValues)
        .withKeyConditionExpression(keyConditionExpression)

      dynamoDBHelper
        .query(queryRequest)
        .map(_.getItems.asScala.toList.headOption.map(toRecordSetChange))
    }

  def toRecordSetChange(item: java.util.Map[String, AttributeValue]): RecordSetChange =
    try {
      val recordSetChangeBlob = item.get(RECORD_SET_CHANGE_BLOB)
      fromPB(VinylDNSProto.RecordSetChange.parseFrom(recordSetChangeBlob.getB.array()))
    } catch {
      case ex: Throwable =>
        log.error("fromItem", ex)
        throw new UnexpectedDynamoResponseException(ex.getMessage, ex)
    }

  def toItem(
      changeSet: ChangeSet,
      change: RecordSetChange
  ): java.util.HashMap[String, AttributeValue] = {
    val item = new java.util.HashMap[String, AttributeValue]()
    item.put(CHANGE_SET_ID, new AttributeValue(changeSet.id))
    item.put(ZONE_ID, new AttributeValue(changeSet.zoneId))
    item.put(CHANGE_SET_STATUS, new AttributeValue().withN(changeSet.status.intValue.toString))
    item.put(CREATED_TIMESTAMP, new AttributeValue(changeSet.createdTimestamp.toString))
    item.put(
      RECORD_SET_CHANGE_CREATED_TIMESTAMP,
      new AttributeValue().withN(change.created.getMillis.toString)
    )
    item.put(PROCESSING_TIMESTAMP, new AttributeValue(changeSet.processingTimestamp.toString))

    val recordSetChangeBlob = toPB(change).toByteArray

    val recordSetChangeBB = ByteBuffer.allocate(recordSetChangeBlob.length)
    recordSetChangeBB.put(recordSetChangeBlob)
    recordSetChangeBB.position(0)

    item.put(RECORD_SET_CHANGE_ID, new AttributeValue(change.id))
    item.put(RECORD_SET_CHANGE_BLOB, new AttributeValue().withB(recordSetChangeBB))

    item
  }
}
