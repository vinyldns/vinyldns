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

import java.util.HashMap

import cats.effect._
import cats.implicits._
import com.amazonaws.services.dynamodbv2.model._
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.core.domain.DomainHelpers.omitTrailingDot
import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.{ChangeSet, ListRecordSetResults, RecordSet, RecordSetRepository}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.route.Monitored

object DynamoDBRecordSetRepository extends ProtobufConversions {

  private[repository] val ZONE_ID = "zone_id"
  private[repository] val RECORD_SET_ID = "record_set_id"
  private[repository] val RECORD_SET_TYPE = "record_set_type"
  private[repository] val RECORD_SET_NAME = "record_set_name"
  private[repository] val RECORD_SET_SORT = "record_set_sort"
  private[repository] val RECORD_SET_BLOB = "record_set_blob"
  private val ZONE_ID_RECORD_SET_NAME_INDEX = "zone_id_record_set_name_index"
  private val ZONE_ID_RECORD_SET_SORT_INDEX = "zone_id_record_set_sort_index"

  def apply(
      config: DynamoDBRepositorySettings,
      dynamoConfig: DynamoDBDataStoreSettings
  ): IO[DynamoDBRecordSetRepository] = {

    val dynamoDBHelper = new DynamoDBHelper(
      DynamoDBClient(dynamoConfig),
      LoggerFactory.getLogger("DynamoDBRecordSetRepository")
    )

    val dynamoReads = config.provisionedReads
    val dynamoWrites = config.provisionedWrites
    val tableName = config.tableName

    val tableAttributes = Seq(
      new AttributeDefinition(ZONE_ID, "S"),
      new AttributeDefinition(RECORD_SET_NAME, "S"),
      new AttributeDefinition(RECORD_SET_ID, "S"),
      new AttributeDefinition(RECORD_SET_SORT, "S")
    )

    val secondaryIndexes = Seq(
      new GlobalSecondaryIndex()
        .withIndexName(ZONE_ID_RECORD_SET_NAME_INDEX)
        .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
        .withKeySchema(
          new KeySchemaElement(ZONE_ID, KeyType.HASH),
          new KeySchemaElement(RECORD_SET_NAME, KeyType.RANGE)
        )
        .withProjection(new Projection().withProjectionType("ALL")),
      new GlobalSecondaryIndex()
        .withIndexName(ZONE_ID_RECORD_SET_SORT_INDEX)
        .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
        .withKeySchema(
          new KeySchemaElement(ZONE_ID, KeyType.HASH),
          new KeySchemaElement(RECORD_SET_SORT, KeyType.RANGE)
        )
        .withProjection(new Projection().withProjectionType("ALL"))
    )

    val setup = dynamoDBHelper.setupTable(
      new CreateTableRequest()
        .withTableName(tableName)
        .withAttributeDefinitions(tableAttributes: _*)
        .withKeySchema(new KeySchemaElement(RECORD_SET_ID, KeyType.HASH))
        .withGlobalSecondaryIndexes(secondaryIndexes: _*)
        .withProvisionedThroughput(new ProvisionedThroughput(dynamoReads, dynamoWrites))
    )

    setup.as(new DynamoDBRecordSetRepository(tableName, dynamoDBHelper))
  }
}

class DynamoDBRecordSetRepository private[repository] (
    val recordSetTableName: String,
    val dynamoDBHelper: DynamoDBHelper
) extends RecordSetRepository
    with DynamoDBRecordSetConversions
    with Monitored
    with QueryHelper {

  import DynamoDBRecordSetRepository._

  val log: Logger = LoggerFactory.getLogger("DynamoDBRecordSetRepository")

  def apply(changeSet: ChangeSet): IO[ChangeSet] =
    monitor("repo.RecordSet.apply") {
      log.info(
        s"Applying change set for zone ${changeSet.zoneId} with size ${changeSet.changes.size}"
      )

      // The BatchWriteItem max size is 25, so we need to group by that number
      val MaxBatchWriteGroup = 25
      val writeItems = changeSet.changes.map(toWriteRequest)
      val batchWrites = writeItems
        .grouped(MaxBatchWriteGroup)
        .map(group => dynamoDBHelper.toBatchWriteItemRequest(group, recordSetTableName))

      // Fold left will attempt each batch sequentially, and fail fast on error
      val result = batchWrites.foldLeft(IO.pure(List.empty[BatchWriteItemResult])) {
        case (acc, req) =>
          acc.flatMap { lst =>
            dynamoDBHelper.batchWriteItem(recordSetTableName, req).map(result => result :: lst)
          }
      }

      // Assuming we succeeded, then return the change set with a status of applied
      result.map(_ => changeSet)
    }

  def putRecordSet(recordSet: RecordSet): IO[RecordSet] = { //TODO remove me
    val item = toItem(recordSet)
    val request = new PutItemRequest().withTableName(recordSetTableName).withItem(item)
    dynamoDBHelper.putItem(request).map(_ => recordSet)
  }

  def listRecordSets(
      zoneId: Option[String],
      startFrom: Option[String],
      maxItems: Option[Int],
      recordNameFilter: Option[String],
      recordTypeFilter: Option[Set[RecordType]],
      recordOwnerGroupFilter: Option[String],
      nameSort: NameSort
  ): IO[ListRecordSetResults] =
    monitor("repo.RecordSet.listRecordSets") {
      zoneId match {
        case None =>
          IO.raiseError(
            UnsupportedDynamoDBRepoFunction(
              "listRecordSets without zoneId is not supported by VinylDNS DynamoDB RecordSetRepository"
            )
          )
        case Some(id) =>
          log.info(s"Getting recordSets for zone $zoneId")

          val keyConditions = Map[String, String](ZONE_ID -> id)
          val filterExpression = recordNameFilter.map(
            filter => ContainsFilter(RECORD_SET_SORT, omitTrailingDot(filter.toLowerCase))
          )

          val startKey = startFrom.map { inputString =>
            val attributes = inputString.split('~')
            Map(
              ZONE_ID -> attributes(0),
              RECORD_SET_NAME -> attributes(1),
              RECORD_SET_ID -> attributes(2)
            )
          }
          val responseFuture = doQuery(
            recordSetTableName,
            ZONE_ID_RECORD_SET_NAME_INDEX,
            keyConditions,
            filterExpression,
            startKey,
            maxItems
          )(dynamoDBHelper)

          for {
            resp <- responseFuture
            queryResp = resp.asInstanceOf[QueryResponseItems]
            rs = queryResp.items.map(fromItem)
            nextId = queryResp.lastEvaluatedKey.map { keyMap =>
              List(
                keyMap.get(ZONE_ID).getS,
                keyMap.get(RECORD_SET_NAME).getS,
                keyMap.get(RECORD_SET_ID).getS
              ).mkString("~")
            }
          } yield ListRecordSetResults(
            rs,
            nextId,
            startFrom,
            maxItems,
            recordNameFilter,
            recordTypeFilter,
            recordOwnerGroupFilter,
            nameSort
          )
      }
    }

  def getRecordSetsByName(zoneId: String, name: String): IO[List[RecordSet]] =
    monitor("repo.RecordSet.getRecordSetByName") {
      log.info(s"Getting recordSet $name from zone $zoneId")

      val keyConditions = Map[String, String](
        ZONE_ID -> zoneId,
        RECORD_SET_SORT -> omitTrailingDot(name.toLowerCase())
      )
      val responseFuture =
        doQuery(recordSetTableName, ZONE_ID_RECORD_SET_SORT_INDEX, keyConditions)(dynamoDBHelper)

      for {
        resp <- responseFuture
        rs = resp.asInstanceOf[QueryResponseItems].items.map(fromItem)
      } yield rs
    }

  def getRecordSets(zoneId: String, name: String, typ: RecordType): IO[List[RecordSet]] =
    monitor("repo.RecordSet.getRecordSetsByNameAndType") {
      log.info(s"Getting recordSet $name, zone $zoneId, type $typ")

      val keyConditions = Map[String, String](
        ZONE_ID -> zoneId,
        RECORD_SET_SORT -> omitTrailingDot(name.toLowerCase())
      )
      val filterExpression = Some(EqualsFilter(RECORD_SET_TYPE, typ.toString))
      val responseFuture =
        doQuery(recordSetTableName, ZONE_ID_RECORD_SET_SORT_INDEX, keyConditions, filterExpression)(
          dynamoDBHelper
        )

      for {
        resp <- responseFuture
        rs = resp.asInstanceOf[QueryResponseItems].items.map(fromItem)
      } yield rs
    }

  def getRecordSet(recordSetId: String): IO[Option[RecordSet]] =
    monitor("repo.RecordSet.getRecordSetById") {
      //Do not need ZoneId, recordSetId is unique
      log.info(s"Getting recordSet $recordSetId")
      val key = new HashMap[String, AttributeValue]()
      key.put(RECORD_SET_ID, new AttributeValue(recordSetId))
      val request = new GetItemRequest().withTableName(recordSetTableName).withKey(key)

      dynamoDBHelper.getItem(request).map { result =>
        if (result != null && result.getItem != null && !result.getItem.isEmpty)
          Some(fromItem(result.getItem))
        else
          None
      }
    }

  def getRecordSetCount(zoneId: String): IO[Int] =
    monitor("repo.RecordSet.getRecordSetCount") {
      log.info(s"Getting record set count zone $zoneId")

      val keyConditions = Map[String, String](ZONE_ID -> zoneId)
      // set isCountQuery to true to ignore items
      val responseFuture = doQuery(
        recordSetTableName,
        ZONE_ID_RECORD_SET_NAME_INDEX,
        keyConditions,
        isCountQuery = true
      )(dynamoDBHelper)

      responseFuture.map(resp => resp.asInstanceOf[QueryResponseCount].count)
    }

  def getRecordSetsByFQDNs(names: Set[String]): IO[List[RecordSet]] =
    monitor("repo.RecordSet.getRecordSetsByFQDNs") {
      IO.raiseError(
        UnsupportedDynamoDBRepoFunction(
          "getRecordSetsByFQDNs is not supported by VinylDNS DynamoDB RecordSetRepository"
        )
      )
    }

  def getFirstOwnedRecordByGroup(ownerGroupId: String): IO[Option[String]] =
    monitor("repo.RecordSet.getFirstOwnedRecordByGroup") {
      IO.raiseError(
        UnsupportedDynamoDBRepoFunction(
          s"getFirstOwnedRecordByGroup is not supported by VinylDNS DynamoDB RecordSetRepository id=$ownerGroupId"
        )
      )
    }

  def deleteRecordSetsInZone(zoneId: String, zoneName: String): IO[Unit] =
    monitor("repo.RecordSet.deleteRecordSetsInZone") {
      IO.raiseError(
        UnsupportedDynamoDBRepoFunction(
          s"""deleteRecordSetsInZone(zoneid=$zoneId, zoneName=$zoneName)
             |is not supported by VinylDNS DynamoDB RecordSetRepository""".stripMargin
            .replaceAll("\n", " ")
        )
      )
    }
}
