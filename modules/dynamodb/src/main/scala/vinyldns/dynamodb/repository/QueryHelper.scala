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
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, QueryRequest, QueryResult, Select}

import scala.collection.JavaConverters._

trait ResponseItems {
  def addResult(newResult: QueryResult): ResponseItems
  def isComplete(limit: Option[Int]): Boolean
  def trimTo(limit: Option[Int]): ResponseItems
}

case class QueryResponseItems(
    items: List[java.util.Map[String, AttributeValue]] = List(),
    lastEvaluatedKey: Option[java.util.Map[String, AttributeValue]] = None
) extends ResponseItems {

  override def addResult(newResult: QueryResult): QueryResponseItems =
    QueryResponseItems(items ++ newResult.getItems.asScala, Option(newResult.getLastEvaluatedKey))

  override def isComplete(limit: Option[Int]): Boolean =
    (limit, lastEvaluatedKey) match {
      case (_, None) => true
      case (Some(lim), _) if lim <= items.length => true
      case _ => false
    }

  override def trimTo(limit: Option[Int]): QueryResponseItems =
    limit match {
      case Some(lim) if items.length > lim =>
        val trimmedItems = items.take(lim)
        val last = trimmedItems.last
        QueryResponseItems(trimmedItems, Some(last))

      case _ => this
    }
}

case class QueryResponseCount(
    count: Int = 0,
    lastEvaluatedKey: Option[java.util.Map[String, AttributeValue]] = None
) extends ResponseItems {

  override def addResult(newResult: QueryResult): QueryResponseCount =
    QueryResponseCount(count + newResult.getCount, Option(newResult.getLastEvaluatedKey))

  override def isComplete(limit: Option[Int]): Boolean =
    lastEvaluatedKey match {
      case None => true
      case _ => false
    }

  override def trimTo(limit: Option[Int]): QueryResponseCount = this
}

trait FilterType {
  val attributeName: String
  val attributeValue: String
  def getFilterString(name: String, value: String): String
}
case class ContainsFilter(attributeName: String, attributeValue: String) extends FilterType {
  override def getFilterString(name: String, value: String) = s"contains ($name, $value)"
}
case class EqualsFilter(attributeName: String, attributeValue: String) extends FilterType {
  override def getFilterString(name: String, value: String) = s"$name = $value"
}

object QueryManager {
  def apply(
      tableName: String,
      index: String,
      keyConditions: Map[String, String],
      filter: Option[FilterType],
      initialStartKey: Option[Map[String, String]],
      maxItems: Option[Int],
      isCountQuery: Boolean
  ): QueryManager = {
    val expressionAttributeValues = new HashMap[String, AttributeValue]
    val expressionAttributeNames = new HashMap[String, String]

    val expression = keyConditions.zipWithIndex.map { item =>
      val ((attrName, attrValue), count) = item
      expressionAttributeValues.put(s":attrVal$count", new AttributeValue(attrValue))
      expressionAttributeNames.put(s"#attr_name$count", attrName)
      s"#attr_name$count = :attrVal$count"
    }

    val keyConditionExpression = expression.reduce(_ + " AND " + _)

    // set filter expression if applicable
    val filterExpression = filter.map { f =>
      expressionAttributeNames.put("#filter_name", f.attributeName)
      expressionAttributeValues.put(":filterVal", new AttributeValue(f.attributeValue))
      f.getFilterString("#filter_name", ":filterVal")
    }

    val start: Option[util.Map[String, AttributeValue]] = initialStartKey.map {
      _.map {
        case (key, value) =>
          (key, new AttributeValue(value))
      }.asJava
    }

    QueryManager(
      tableName,
      index,
      expressionAttributeNames,
      expressionAttributeValues,
      keyConditionExpression,
      start,
      filterExpression,
      maxItems,
      isCountQuery
    )
  }
}

case class QueryManager(
    tableName: String,
    index: String,
    expressionAttributeNames: util.HashMap[String, String],
    expressionAttributeValues: util.HashMap[String, AttributeValue],
    keyConditionExpression: String,
    startKey: Option[util.Map[String, AttributeValue]],
    filterExpression: Option[String],
    maxItems: Option[Int],
    isCountQuery: Boolean
) {

  def build(): QueryRequest = {
    val request = new QueryRequest()
      .withTableName(tableName)
      .withIndexName(index)
      .withExpressionAttributeNames(expressionAttributeNames)
      .withExpressionAttributeValues(expressionAttributeValues)
      .withKeyConditionExpression(keyConditionExpression)

    filterExpression.foreach(request.withFilterExpression(_))
    maxItems.foreach(request.withLimit(_))
    startKey.foreach(request.withExclusiveStartKey(_))
    if (isCountQuery) request.withSelect(Select.COUNT)

    request
  }
}

trait QueryHelper {

  def doQuery(
      tableName: String,
      index: String,
      keyConditions: Map[String, String],
      nameFilter: Option[FilterType] = None,
      startKey: Option[Map[String, String]] = None,
      maxItems: Option[Int] = None,
      isCountQuery: Boolean = false
  ): DynamoDBHelper => IO[ResponseItems] = dynamoDbHelper => {
    // do not limit items when there is a filter - filters are applied after limits
    val itemsToRetrieve = nameFilter match {
      case Some(_) => None
      case None => maxItems
    }

    val response =
      if (isCountQuery) QueryResponseCount()
      else QueryResponseItems()

    val queryManager =
      QueryManager(
        tableName,
        index,
        keyConditions,
        nameFilter,
        startKey,
        itemsToRetrieve,
        isCountQuery
      )
    completeQuery(dynamoDbHelper, queryManager, response, maxItems)
  }

  private def completeQuery(
      dynamoDbHelper: DynamoDBHelper,
      dynamoQuery: QueryManager,
      acc: ResponseItems,
      limit: Option[Int]
  ): IO[ResponseItems] =
    dynamoDbHelper.query(dynamoQuery.build()).flatMap { queryResult =>
      val accumulatedResults = acc.addResult(queryResult)
      if (accumulatedResults.isComplete(limit))
        IO(accumulatedResults.trimTo(limit))
      else
        completeQuery(
          dynamoDbHelper,
          dynamoQuery.copy(startKey = Some(queryResult.getLastEvaluatedKey)),
          accumulatedResults,
          limit
        )
    }
}
