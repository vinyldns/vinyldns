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

import java.util.Collections

import cats.effect._
import cats.syntax.all._
import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.util.TableUtils
import org.slf4j.Logger
import vinyldns.core.VinylDNSMetrics

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

private class RetryStateHolder(var retries: Int = 10, var backoff: FiniteDuration = 1.millis)

case class DynamoDBRetriesExhaustedException(msg: String) extends Throwable(msg)
case class UnsupportedDynamoDBRepoFunction(msg: String) extends Throwable
class UnexpectedDynamoResponseException(message: String, cause: Throwable)
    extends Exception(message: String, cause: Throwable)

trait DynamoUtils {

  def createTableIfNotExists(dynamoDB: AmazonDynamoDBClient, req: CreateTableRequest): IO[Boolean]
  def waitUntilActive(dynamoDB: AmazonDynamoDBClient, tableName: String): IO[Unit]
}

/* Used to provide an exponential backoff in the event of a Provisioned Throughput Exception */
class DynamoDBHelper(dynamoDB: AmazonDynamoDBClient, log: Logger) {

  private[repository] val retryCount: Int = 10
  private val retryBackoff: FiniteDuration = 1.millis
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  private[repository] val provisionedThroughputMeter =
    VinylDNSMetrics.metricsRegistry.meter("dynamo.provisionedThroughput")
  private[repository] val retriesExceededMeter =
    VinylDNSMetrics.metricsRegistry.meter("dynamo.retriesExceeded")
  private[repository] val dynamoUnexpectedFailuresMeter =
    VinylDNSMetrics.metricsRegistry.meter("dynamo.unexpectedFailure")
  private[repository] val callRateMeter = VinylDNSMetrics.metricsRegistry.meter("dynamo.callRate")
  private[repository] val dynamoUtils = new DynamoUtils {
    def waitUntilActive(dynamoDB: AmazonDynamoDBClient, tableName: String): IO[Unit] =
      IO(TableUtils.waitUntilActive(dynamoDB, tableName))

    def createTableIfNotExists(
        dynamoDB: AmazonDynamoDBClient,
        req: CreateTableRequest
    ): IO[Boolean] =
      IO(TableUtils.createTableIfNotExists(dynamoDB, req))
  }

  def shutdown(): Unit = dynamoDB.shutdown()

  private[repository] def send[In <: AmazonWebServiceRequest, Out](aws: In, func: In => Out)(
      implicit d: Describe[_ >: In]
  ): IO[Out] = {

    def name = d.desc(aws)

    def sendSingle(retryState: RetryStateHolder): IO[Out] =
      IO {
        callRateMeter.mark()
        func(aws)
      }.handleErrorWith {
        case _: ProvisionedThroughputExceededException if retryState.retries > 0 =>
          provisionedThroughputMeter.mark()
          val backoff = retryState.backoff
          retryState.retries -= 1
          retryState.backoff *= 2
          log.warn(s"provisioned throughput exceeded for aws request $name")
          IO.sleep(backoff) *> sendSingle(retryState)
        case _: ProvisionedThroughputExceededException if retryState.retries == 0 =>
          retriesExceededMeter.mark()
          log.error(s"exhausted retries for aws request $name")
          IO.raiseError(DynamoDBRetriesExhaustedException(s"Exhausted retries for $name"))
        case other =>
          dynamoUnexpectedFailuresMeter.mark()
          val n = name
          log.error(s"failure while executing $n", other)
          IO.raiseError(other)
      }

    val state = new RetryStateHolder(retryCount, retryBackoff)

    sendSingle(state)
  }

  /* Describe is used to stringify a web service request, very useful for loggging */
  trait Describe[T] {
    def desc(t: T): String
  }

  object Describe {

    implicit object GenericDescribe extends Describe[AmazonWebServiceRequest] {
      def desc(aws: AmazonWebServiceRequest): String = aws.getClass.getSimpleName
    }
  }

  implicit object DescribeDescribe extends Describe[DescribeTableRequest] {
    def desc(aws: DescribeTableRequest): String = s"DescribeTableRequest(${aws.getTableName})"
  }

  implicit object QueryDescribe extends Describe[QueryRequest] {
    def desc(aws: QueryRequest): String =
      s"QueryRequest(${aws.getTableName},${aws.getExpressionAttributeValues})"
  }

  implicit object PutItemDescribe extends Describe[PutItemRequest] {
    def desc(aws: PutItemRequest): String = s"PutItemRequest(${aws.getTableName})"
  }

  implicit object DeleteDescribe extends Describe[DeleteItemRequest] {
    def desc(aws: DeleteItemRequest): String = s"DeleteItemRequest(${aws.getTableName}})"
  }

  implicit object BatchGetItemDescribe extends Describe[BatchGetItemRequest] {
    def desc(aws: BatchGetItemRequest): String = {
      val table = aws.getRequestItems.asScala.headOption.getOrElse("unknown table")
      s"BatchGetItemRequest($table, ${aws.getRequestItems.size})"
    }
  }

  implicit object BatchWriteItemDescribe extends Describe[BatchWriteItemRequest] {
    def desc(aws: BatchWriteItemRequest): String = {
      val table = aws.getRequestItems.asScala.headOption.getOrElse("unknown table")
      s"BatchWriteItemRequest($table, ${aws.getRequestItems.size})"
    }
  }

  def setupTable(createTableRequest: CreateTableRequest): IO[Unit] =
    for {
      tableCreated <- dynamoUtils.createTableIfNotExists(dynamoDB, createTableRequest)
      _ = if (!tableCreated) {
        log.info(s"Table ${createTableRequest.getTableName} already exists")
      }
      _ <- dynamoUtils.waitUntilActive(dynamoDB, createTableRequest.getTableName)
    } yield ()

  def listTables(aws: ListTablesRequest): IO[ListTablesResult] =
    send[ListTablesRequest, ListTablesResult](aws, dynamoDB.listTables)

  def describeTable(aws: DescribeTableRequest): IO[DescribeTableResult] =
    send[DescribeTableRequest, DescribeTableResult](aws, dynamoDB.describeTable)

  def createTable(aws: CreateTableRequest): IO[CreateTableResult] =
    send[CreateTableRequest, CreateTableResult](aws, dynamoDB.createTable)

  def updateTable(aws: UpdateTableRequest): IO[UpdateTableResult] =
    send[UpdateTableRequest, UpdateTableResult](aws, dynamoDB.updateTable)

  def deleteTable(aws: DeleteTableRequest): IO[DeleteTableResult] =
    send[DeleteTableRequest, DeleteTableResult](aws, dynamoDB.deleteTable)

  def query(aws: QueryRequest): IO[QueryResult] =
    send[QueryRequest, QueryResult](aws, dynamoDB.query)

  def scan(aws: ScanRequest): IO[ScanResult] =
    send[ScanRequest, ScanResult](aws, dynamoDB.scan)

  def putItem(aws: PutItemRequest): IO[PutItemResult] =
    send[PutItemRequest, PutItemResult](aws, dynamoDB.putItem)

  def getItem(aws: GetItemRequest): IO[GetItemResult] =
    send[GetItemRequest, GetItemResult](aws, dynamoDB.getItem)

  def updateItem(aws: UpdateItemRequest): IO[UpdateItemResult] =
    send[UpdateItemRequest, UpdateItemResult](aws, dynamoDB.updateItem)

  def deleteItem(aws: DeleteItemRequest): IO[DeleteItemResult] =
    send[DeleteItemRequest, DeleteItemResult](aws, dynamoDB.deleteItem)

  def scanAll(aws: ScanRequest): IO[List[ScanResult]] =
    scan(aws).flatMap(result => continueScanning(aws, result, (List(result), 1))).map {
      case (lst, scanNum) =>
        log.debug(s"Completed $scanNum scans in scanAll on table: [${aws.getTableName}]")
        lst
    }

  private def continueScanning(
      request: ScanRequest,
      result: ScanResult,
      acc: (List[ScanResult], Int)
  ): IO[(List[ScanResult], Int)] =
    result.getLastEvaluatedKey match {

      case lastEvaluatedKey if lastEvaluatedKey == null || lastEvaluatedKey.isEmpty =>
        // there is no last evaluated key, that means we are done querying
        IO.pure(acc)

      case lastEvaluatedKey =>
        // set the exclusive start key to the last evaluated key
        val continuedQuery = request
        continuedQuery.setExclusiveStartKey(lastEvaluatedKey)

        // re-run the query, continue querying if need be, be sure to accumulate the result
        scan(continuedQuery)
          .flatMap { continuedResult =>
            val accumulated = acc match {
              case (lst, num) => (lst :+ continuedResult, num + 1)
            }
            continueScanning(continuedQuery, continuedResult, accumulated)
          }
    }

  def queryAll(aws: QueryRequest): IO[List[QueryResult]] =
    query(aws).flatMap(result => continueQuerying(aws, result, List(result)))

  /* Supports query all by continuing to query until there is no last evaluated key */
  private def continueQuerying(
      request: QueryRequest,
      result: QueryResult,
      acc: List[QueryResult]
  ): IO[List[QueryResult]] = {

    val lastCount = result.getCount
    val limit =
      if (request.getLimit == null || request.getLimit == 0) None else Some(request.getLimit)

    result.getLastEvaluatedKey match {

      case lastEvaluatedKey if lastEvaluatedKey == null || lastEvaluatedKey.isEmpty =>
        // there is no last evaluated key, that means we are done querying
        IO.pure(acc)

      case _ if limit.exists(_ <= lastCount) =>
        //maxItems from limit has been achieved
        IO.pure(acc)

      case lastEvaluatedKey =>
        // set the exclusive start key to the last evaluated key
        val continuedQuery = request
        continuedQuery.setExclusiveStartKey(lastEvaluatedKey)

        //adjust limit
        limit.foreach(old => continuedQuery.setLimit(old - lastCount))

        // re-run the query, continue querying if need be, be sure to accumulate the result
        query(continuedQuery)
          .flatMap(
            continuedResult =>
              continueQuerying(continuedQuery, continuedResult, acc :+ continuedResult)
          )
    }
  }

  /**
    * Does a batch write item, but will attempt to continue processing unwritten items a number of times with backoff
    */
  def batchWriteItem(
      table: String,
      aws: BatchWriteItemRequest,
      retries: Int = 10,
      backoff: FiniteDuration = 1.millis
  ): IO[BatchWriteItemResult] =
    send[BatchWriteItemRequest, BatchWriteItemResult](aws, dynamoDB.batchWriteItem)
      .flatMap(r => sendUnprocessedBatchWriteItems(table, r, retries, backoff))

  def toBatchWriteItemRequest(writes: Seq[WriteRequest], tableName: String): BatchWriteItemRequest =
    toBatchWriteItemRequest(Collections.singletonMap(tableName, writes.asJava))

  def toBatchWriteItemRequest(
      writes: java.util.Map[String, java.util.List[WriteRequest]]
  ): BatchWriteItemRequest =
    new BatchWriteItemRequest()
      .withRequestItems(writes)
      .withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL)

  def batchGetItem(aws: BatchGetItemRequest): IO[BatchGetItemResult] =
    send[BatchGetItemRequest, BatchGetItemResult](aws, dynamoDB.batchGetItem)

  /* sends unprocessed items back to dynamo in a retry loop with backoff */
  private def sendUnprocessedBatchWriteItems(
      tableName: String,
      result: BatchWriteItemResult,
      retriesRemaining: Int,
      backoff: FiniteDuration
  ): IO[BatchWriteItemResult] = {

    // calculate how many items were not processed yet, we need to re-submit those
    val unprocessed: Int = result.getUnprocessedItems.get(tableName) match {
      case null => 0
      case items => items.size
    }

    if (unprocessed == 0) {
      // if there are no items left to process, let's indicate that we are good!
      IO.pure(result)
    } else if (retriesRemaining == 0) {
      // there are unprocessed items still remaining, but we have exhausted our retries, consider this FAILED
      log.error("Exhausted retries while sending batch write")
      throw DynamoDBRetriesExhaustedException(
        s"Unable to batch write for table $tableName after retries"
      )
    } else {
      // there are unprocessed items and we have retries left, let's retry those items we haven't yet processed
      log.warn(
        s"Unable to process all items in batch for table $tableName, resubmitting new batch with $unprocessed " +
          s"items remaining"
      )
      val nextBatch = toBatchWriteItemRequest(result.getUnprocessedItems)
      IO.sleep(backoff) *> batchWriteItem(tableName, nextBatch, retriesRemaining - 1, backoff * 2)
    }
  }
}
