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

import cats.effect.IO
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model._
import com.codahale.metrics.Meter
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.{Eventually, PatienceConfiguration, ScalaFutures}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.time.{Seconds, Span}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.Logger

class DynamoDBHelperSpec
    extends AnyWordSpecLike
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with Eventually
    with BeforeAndAfterEach {

  private val mockLogger = mock[Logger]
  private val mockDynamo = mock[AmazonDynamoDBClient]
  private val mockProvisionedThroughputMeter = mock[Meter]
  private val mockRetriesExceededMeter = mock[Meter]
  private val mockDynamoUnexpectedFailuresMeter = mock[Meter]
  private val mockCallRateMeter = mock[Meter]
  private val mockDynamoUtils = mock[DynamoUtils]

  private val ptee: Throwable = new ProvisionedThroughputExceededException(
    "provisioned throughput exceeded test"
  )
  private val testTableName = "test-table"
  private def excessivelyFail =
    doThrow(ptee)
      .doThrow(ptee)
      .doThrow(ptee)
      .doThrow(ptee)
      .doThrow(ptee)
      .doThrow(ptee)
      .doThrow(ptee)
      .doThrow(ptee)
      .doThrow(ptee)
      .doThrow(ptee)
      .doThrow(ptee)
      .doThrow(ptee)
      .doThrow(ptee)

  private implicit val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  private val log: Logger = mockLogger
  private val dynamoDB: AmazonDynamoDBClient = mockDynamo

  class TestDynamoDBHelper extends DynamoDBHelper(dynamoDB, log) {

    // override the retry count to speed up the tests
    override val retryCount: Int = 4
    override val provisionedThroughputMeter: Meter = mockProvisionedThroughputMeter
    override val retriesExceededMeter: Meter = mockRetriesExceededMeter
    override val dynamoUnexpectedFailuresMeter: Meter = mockDynamoUnexpectedFailuresMeter
    override val callRateMeter: Meter = mockCallRateMeter

    override val dynamoUtils: DynamoUtils = mockDynamoUtils
  }

  override protected def beforeEach(): Unit =
    reset(
      mockDynamo,
      mockProvisionedThroughputMeter,
      mockRetriesExceededMeter,
      mockDynamoUnexpectedFailuresMeter,
      mockCallRateMeter,
      mockDynamoUtils
    )

  "DynamoDBHelper" should {
    "Using Monitoring" should {
      "increment the call rate for every call" in {
        val req = mock[CreateTableRequest]
        val res = mock[CreateTableResult]

        doReturn(res).when(mockDynamo).createTable(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.createTable(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ => verify(mockCallRateMeter).mark())
      }

      "increment the provisioned throughput exceeded meter" in {
        val lstTableReq = new ListTablesRequest()
        val lstTableRes = mock[ListTablesResult]

        // Throw PTEE twice
        doThrow(ptee).doThrow(ptee).doReturn(lstTableRes).when(mockDynamo).listTables(lstTableReq)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.listTables(lstTableReq).unsafeToFuture()

        whenReady(testResult, timeout) { _ =>
          verify(mockProvisionedThroughputMeter, times(2)).mark()
          verify(mockCallRateMeter, times(3)).mark()
        }
      }

      "increment the retries exhausted meter" in {
        val lstTableReq = new ListTablesRequest()
        excessivelyFail.when(mockDynamo).listTables(lstTableReq)

        val underTest = new TestDynamoDBHelper

        underTest
          .listTables(lstTableReq)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockRetriesExceededMeter).mark()
      }

      "increment the unexpected error meter" in {
        val lstTableReq = new ListTablesRequest()
        doThrow(new RuntimeException("fail")).when(mockDynamo).listTables(lstTableReq)

        val underTest = new TestDynamoDBHelper

        underTest
          .listTables(lstTableReq)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
        verify(mockDynamoUnexpectedFailuresMeter).mark()
      }
    }

    "Setup Table" should {
      "return normally when no errors occur" in {
        val req = new CreateTableRequest().withTableName(testTableName)

        doReturn(IO.pure(true)).when(mockDynamoUtils).createTableIfNotExists(mockDynamo, req)
        doReturn(IO.unit).when(mockDynamoUtils).waitUntilActive(mockDynamo, testTableName)

        val underTest = new TestDynamoDBHelper
        underTest.setupTable(req).unsafeRunSync()

        verify(mockDynamoUtils).createTableIfNotExists(mockDynamo, req)
        verify(mockDynamoUtils).waitUntilActive(mockDynamo, testTableName)
      }
    }

    "List Tables" should {
      "return normally when no errors occur" in {
        val lstTableReq = new ListTablesRequest()
        val lstTableRes = mock[ListTablesResult]
        doReturn(lstTableRes).when(mockDynamo).listTables(lstTableReq)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.listTables(lstTableReq).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe lstTableRes)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {

        val lstTableReq = new ListTablesRequest()
        val lstTableRes = mock[ListTablesResult]

        doThrow(ptee).doThrow(ptee).doReturn(lstTableRes).when(mockDynamo).listTables(lstTableReq)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.listTables(lstTableReq).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe lstTableRes)
      }

      "throw the error up after exceeding retries" in {
        val lstTableReq = new ListTablesRequest()
        excessivelyFail.when(mockDynamo).listTables(lstTableReq)

        val underTest = new TestDynamoDBHelper

        underTest
          .listTables(lstTableReq)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).listTables(lstTableReq)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val lstTableReq = new ListTablesRequest()
        doThrow(new RuntimeException("fail")).when(mockDynamo).listTables(lstTableReq)

        val underTest = new TestDynamoDBHelper

        underTest
          .listTables(lstTableReq)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }
    }

    "Describe Table" should {
      "return normally when no errors occur" in {
        val req = new DescribeTableRequest("test-table")
        val res = mock[DescribeTableResult]

        doReturn(res).when(mockDynamo).describeTable(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.describeTable(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {
        val req = new DescribeTableRequest("test-table")
        val res = mock[DescribeTableResult]

        doThrow(ptee).doThrow(ptee).doReturn(res).when(mockDynamo).describeTable(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.describeTable(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "throw the error up after exceeding retries" in {
        val req = new DescribeTableRequest("test-table")

        excessivelyFail.when(mockDynamo).describeTable(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .describeTable(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).describeTable(req)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val req = new DescribeTableRequest("test-table")
        doThrow(new RuntimeException("fail")).when(mockDynamo).describeTable(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .describeTable(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }
    }

    "Create Table" should {
      "return normally when no errors occur" in {
        val req = mock[CreateTableRequest]
        val res = mock[CreateTableResult]

        doReturn(res).when(mockDynamo).createTable(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.createTable(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {
        val req = mock[CreateTableRequest]
        val res = mock[CreateTableResult]

        doThrow(ptee).doThrow(ptee).doReturn(res).when(mockDynamo).createTable(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.createTable(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "throw the error up after exceeding retries" in {
        val req = mock[CreateTableRequest]

        excessivelyFail.when(mockDynamo).createTable(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .createTable(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).createTable(req)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val req = mock[CreateTableRequest]

        doThrow(new RuntimeException("fail")).when(mockDynamo).createTable(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .createTable(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }
    }

    "Update Table" should {
      "return normally when no errors occur" in {
        val req = mock[UpdateTableRequest]
        val res = mock[UpdateTableResult]

        doReturn(res).when(mockDynamo).updateTable(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.updateTable(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {
        val req = mock[UpdateTableRequest]
        val res = mock[UpdateTableResult]

        doThrow(ptee).doThrow(ptee).doReturn(res).when(mockDynamo).updateTable(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.updateTable(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "throw the error up after exceeding retries" in {
        val req = mock[UpdateTableRequest]

        excessivelyFail.when(mockDynamo).updateTable(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .updateTable(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).updateTable(req)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val req = mock[UpdateTableRequest]

        doThrow(new RuntimeException("fail")).when(mockDynamo).updateTable(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .updateTable(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }
    }

    "Delete Table" should {
      "return normally when no errors occur" in {
        val req = mock[DeleteTableRequest]
        val res = mock[DeleteTableResult]

        doReturn(res).when(mockDynamo).deleteTable(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.deleteTable(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {
        val req = mock[DeleteTableRequest]
        val res = mock[DeleteTableResult]

        doThrow(ptee).doThrow(ptee).doReturn(res).when(mockDynamo).deleteTable(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.deleteTable(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "throw the error up after exceeding retries" in {
        val req = mock[DeleteTableRequest]

        excessivelyFail.when(mockDynamo).deleteTable(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .deleteTable(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).deleteTable(req)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val req = mock[DeleteTableRequest]

        doThrow(new RuntimeException("fail")).when(mockDynamo).deleteTable(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .deleteTable(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }
    }

    "Query Table" should {
      "return normally when no errors occur" in {
        val req = mock[QueryRequest]
        val res = mock[QueryResult]

        doReturn(res).when(mockDynamo).query(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.query(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {
        val req = mock[QueryRequest]
        val res = mock[QueryResult]

        doThrow(ptee).doThrow(ptee).doReturn(res).when(mockDynamo).query(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.query(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "throw the error up after exceeding retries" in {
        val req = mock[QueryRequest]

        excessivelyFail.when(mockDynamo).query(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .query(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).query(req)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val req = mock[QueryRequest]

        doThrow(new RuntimeException("fail")).when(mockDynamo).query(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .query(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }
    }

    "Query All" should {
      "query only once if last evaluated key is null" in {
        val req = mock[QueryRequest]
        val res = mock[QueryResult]

        doReturn(null).when(res).getLastEvaluatedKey
        doReturn(res).when(mockDynamo).query(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.queryAll(req).unsafeToFuture()

        whenReady(testResult, timeout) { actualResult =>
          actualResult.size shouldBe 1
          actualResult.head shouldBe res
          verify(mockDynamo, times(1)).query(req)
        }
      }

      "query only once if last evaluated key is empty" in {
        val req = mock[QueryRequest]
        val res = mock[QueryResult]
        val emptyLastKey = new java.util.HashMap[String, AttributeValue]()

        doReturn(emptyLastKey).when(res).getLastEvaluatedKey
        doReturn(res).when(mockDynamo).query(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.queryAll(req).unsafeToFuture()

        whenReady(testResult, timeout) { actualResult =>
          actualResult.size shouldBe 1
          actualResult.head shouldBe res
          verify(mockDynamo, times(1)).query(req)
        }
      }

      "query multiple times until last evaluated key is not present" in {
        val req = mock[QueryRequest]
        val res1 = mock[QueryResult]
        val res2 = mock[QueryResult]
        val res3 = mock[QueryResult]

        val presentLastKey = new java.util.HashMap[String, AttributeValue]()
        presentLastKey.put("someTable", new AttributeValue("foo"))

        doReturn(presentLastKey).when(res1).getLastEvaluatedKey
        doReturn(presentLastKey).when(res2).getLastEvaluatedKey
        doReturn(null).when(res3).getLastEvaluatedKey

        doReturn(res1).doReturn(res2).doReturn(res3).when(mockDynamo).query(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.queryAll(req).unsafeToFuture()

        whenReady(testResult, timeout) { actualResult =>
          actualResult.size shouldBe 3
          actualResult(0) shouldBe res1
          actualResult(1) shouldBe res2
          actualResult(2) shouldBe res3
          verify(mockDynamo, times(3)).query(req)
        }
      }
    }

    "Scan Table" should {
      "return normally when no errors occur" in {
        val req = mock[ScanRequest]
        val res = mock[ScanResult]

        doReturn(res).when(mockDynamo).scan(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.scan(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {
        val req = mock[ScanRequest]
        val res = mock[ScanResult]

        doThrow(ptee).doThrow(ptee).doReturn(res).when(mockDynamo).scan(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.scan(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "throw the error up after exceeding retries" in {
        val req = mock[ScanRequest]

        excessivelyFail.when(mockDynamo).scan(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .scan(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).scan(req)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val req = mock[ScanRequest]

        doThrow(new RuntimeException("fail")).when(mockDynamo).scan(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .scan(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }
    }

    "Scan All Table" should {
      "continue scanning if the last seen key is present" in {
        val req = mock[ScanRequest]
        val res = mock[ScanResult]
        val res2 = mock[ScanResult]
        val lastSeenKey = new util.HashMap[String, AttributeValue]()
        lastSeenKey.put("foo", new AttributeValue("bar"))

        // we have to put a last seen key in order to force 2 scans
        doReturn(lastSeenKey).when(res).getLastEvaluatedKey
        doReturn(res)
          .doReturn(res2)
          .when(mockDynamo)
          .scan(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.scanAll(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ should contain theSameElementsAs List(res, res2))
      }

      "stop scanning if the last seen key is not present" in {
        val req = mock[ScanRequest]
        val res = mock[ScanResult]
        val res2 = mock[ScanResult]
        val lastSeenKey = new util.HashMap[String, AttributeValue]()

        // we return an empty map so we do not scan more than once
        doReturn(lastSeenKey).when(res).getLastEvaluatedKey

        // stage both responses, but we will only get one back because no last evaluated key
        doReturn(res)
          .doReturn(res2)
          .when(mockDynamo)
          .scan(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.scanAll(req).unsafeToFuture()

        whenReady(testResult, timeout)(r => (r should contain).only(res))
      }
    }

    "Put Item" should {
      "return normally when no errors occur" in {
        val req = mock[PutItemRequest]
        val res = mock[PutItemResult]

        doReturn(res).when(mockDynamo).putItem(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.putItem(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {
        val req = mock[PutItemRequest]
        val res = mock[PutItemResult]

        doThrow(ptee).doThrow(ptee).doReturn(res).when(mockDynamo).putItem(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.putItem(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "throw the error up after exceeding retries" in {
        val req = mock[PutItemRequest]

        excessivelyFail.when(mockDynamo).putItem(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .putItem(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).putItem(req)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val req = mock[PutItemRequest]

        doThrow(new RuntimeException("fail")).when(mockDynamo).putItem(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .putItem(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }
    }

    "Get Item" should {
      "return normally when no errors occur" in {
        val req = mock[GetItemRequest]
        val res = mock[GetItemResult]

        doReturn(res).when(mockDynamo).getItem(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.getItem(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {
        val req = mock[GetItemRequest]
        val res = mock[GetItemResult]

        doThrow(ptee).doThrow(ptee).doReturn(res).when(mockDynamo).getItem(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.getItem(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "throw the error up after exceeding retries" in {
        val req = mock[GetItemRequest]

        excessivelyFail.when(mockDynamo).getItem(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .getItem(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).getItem(req)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val req = mock[GetItemRequest]

        doThrow(new RuntimeException("fail")).when(mockDynamo).getItem(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .getItem(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }
    }

    "Update Item" should {
      "return normally when no errors occur" in {
        val req = mock[UpdateItemRequest]
        val res = mock[UpdateItemResult]

        doReturn(res).when(mockDynamo).updateItem(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.updateItem(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {
        val req = mock[UpdateItemRequest]
        val res = mock[UpdateItemResult]

        doThrow(ptee).doThrow(ptee).doReturn(res).when(mockDynamo).updateItem(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.updateItem(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "throw the error up after exceeding retries" in {
        val req = mock[UpdateItemRequest]

        excessivelyFail.when(mockDynamo).updateItem(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .updateItem(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).updateItem(req)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val req = mock[UpdateItemRequest]

        doThrow(new RuntimeException("fail")).when(mockDynamo).updateItem(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .updateItem(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }
    }

    "Delete Item" should {
      "return normally when no errors occur" in {
        val req = mock[DeleteItemRequest]
        val res = mock[DeleteItemResult]

        doReturn(res).when(mockDynamo).deleteItem(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.deleteItem(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {
        val req = mock[DeleteItemRequest]
        val res = mock[DeleteItemResult]

        doThrow(ptee).doThrow(ptee).doReturn(res).when(mockDynamo).deleteItem(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.deleteItem(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "throw the error up after exceeding retries" in {
        val req = mock[DeleteItemRequest]

        excessivelyFail.when(mockDynamo).deleteItem(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .deleteItem(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).deleteItem(req)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val req = mock[DeleteItemRequest]

        doThrow(new RuntimeException("fail")).when(mockDynamo).deleteItem(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .deleteItem(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }
    }

    "Batch Get Item" should {
      "return normally when no errors occur" in {
        val req = mock[BatchGetItemRequest]
        val res = mock[BatchGetItemResult]

        doReturn(res).when(mockDynamo).batchGetItem(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.batchGetItem(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {
        val req = mock[BatchGetItemRequest]
        val res = mock[BatchGetItemResult]

        doThrow(ptee).doThrow(ptee).doReturn(res).when(mockDynamo).batchGetItem(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.batchGetItem(req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "throw the error up after exceeding retries" in {
        val req = mock[BatchGetItemRequest]

        doReturn(new util.HashMap[String, KeysAndAttributes]()).when(req).getRequestItems

        excessivelyFail.when(mockDynamo).batchGetItem(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .batchGetItem(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).batchGetItem(req)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val req = mock[BatchGetItemRequest]

        doThrow(new RuntimeException("fail")).when(mockDynamo).batchGetItem(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .batchGetItem(req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }
    }

    "Batch Write Item" should {
      "return normally when no errors occur" in {
        val req = mock[BatchWriteItemRequest]
        val res = mock[BatchWriteItemResult]

        doReturn(res).when(mockDynamo).batchWriteItem(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.batchWriteItem("table", req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "continue retrying after multiple Provisioned Throughput failures" in {
        val req = mock[BatchWriteItemRequest]
        val res = mock[BatchWriteItemResult]

        doThrow(ptee).doThrow(ptee).doReturn(res).when(mockDynamo).batchWriteItem(req)

        val underTest = new TestDynamoDBHelper
        val testResult = underTest.batchWriteItem("table", req).unsafeToFuture()

        whenReady(testResult, timeout)(_ shouldBe res)
      }

      "throw the error up after exceeding retries" in {
        val req = mock[BatchWriteItemRequest]

        doReturn(new util.HashMap[String, KeysAndAttributes]()).when(req).getRequestItems

        excessivelyFail.when(mockDynamo).batchWriteItem(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .batchWriteItem("table", req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
        verify(mockDynamo, times(underTest.retryCount + 1)).batchWriteItem(req)
      }

      "throw an error that is not a Provisioned Throughput Exception" in {
        val req = mock[BatchWriteItemRequest]

        doThrow(new RuntimeException("fail")).when(mockDynamo).batchWriteItem(req)

        val underTest = new TestDynamoDBHelper

        underTest
          .batchWriteItem("table", req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[RuntimeException]
      }

      "continue to fetch unprocessed items until complete" in {
        val req = mock[BatchWriteItemRequest]
        val res1 = mock[BatchWriteItemResult]
        val res2 = mock[BatchWriteItemResult]
        val res3 = mock[BatchWriteItemResult]
        val wr = mock[WriteRequest]

        val allProcessed = new java.util.HashMap[String, java.util.List[WriteRequest]]()
        val unprocessed1 = new java.util.HashMap[String, java.util.List[WriteRequest]]()
        val unprocessedLst = new util.ArrayList[WriteRequest]()
        unprocessedLst.add(wr)
        unprocessed1.put("table", unprocessedLst)

        // return unprocessed items 2 times, they will all be retried
        doReturn(unprocessed1).when(res1).getUnprocessedItems
        doReturn(unprocessed1).when(res2).getUnprocessedItems
        doReturn(allProcessed).when(res3).getUnprocessedItems
        doReturn(new util.HashMap[String, KeysAndAttributes]()).when(req).getRequestItems

        // setup the order of returns for our dynamo client
        doReturn(res1)
          .doReturn(res2)
          .doReturn(res3)
          .when(mockDynamo)
          .batchWriteItem(any(classOf[BatchWriteItemRequest]))

        val underTest = new TestDynamoDBHelper
        underTest.batchWriteItem("table", req).unsafeToFuture().futureValue(timeout) shouldBe res3
      }

      "fail if the number of retries for items is exceeded" in {
        val req = mock[BatchWriteItemRequest]
        val res1 = mock[BatchWriteItemResult]
        val res2 = mock[BatchWriteItemResult]
        val res3 = mock[BatchWriteItemResult]
        val wr = mock[WriteRequest]

        val allProcessed = new java.util.HashMap[String, java.util.List[WriteRequest]]()
        val unprocessed1 = new java.util.HashMap[String, java.util.List[WriteRequest]]()
        val unprocessedLst = new util.ArrayList[WriteRequest]()
        unprocessedLst.add(wr)
        unprocessed1.put("table", unprocessedLst)

        // return unprocessed items 2 times
        doReturn(unprocessed1).when(res1).getUnprocessedItems
        doReturn(unprocessed1).when(res2).getUnprocessedItems
        doReturn(allProcessed).when(res3).getUnprocessedItems
        doReturn(new util.HashMap[String, KeysAndAttributes]()).when(req).getRequestItems

        // stage 11 results that have unprocessed items
        doReturn(res1)
          .doReturn(res2)
          .doReturn(res2)
          .doReturn(res2)
          .doReturn(res2)
          .doReturn(res2)
          .doReturn(res2)
          .doReturn(res2)
          .doReturn(res2)
          .doReturn(res2)
          .doReturn(res2)
          .doReturn(res2)
          .when(mockDynamo)
          .batchWriteItem(any(classOf[BatchWriteItemRequest]))

        val underTest = new TestDynamoDBHelper
        underTest
          .batchWriteItem("table", req)
          .unsafeToFuture()
          .failed
          .futureValue(timeout) shouldBe a[DynamoDBRetriesExhaustedException]
      }
    }

    "convert a sequence of write requests to a BatchWriteItemRequest" in {
      val writeRequests =
        for {
          i <- 1 to 25
        } yield {
          val item = new java.util.HashMap[String, AttributeValue]()
          item.put("ID", new AttributeValue(s"$i"))
          new WriteRequest().withPutRequest(new PutRequest().withItem(item))
        }

      val underTest = new TestDynamoDBHelper
      val result = underTest.toBatchWriteItemRequest(writeRequests, "someTable")

      val writes: java.util.List[WriteRequest] = result.getRequestItems.get("someTable")
      writes.size shouldBe writeRequests.size

      for {
        i <- 0 to 24
      } yield {
        val put = writes.get(i).getPutRequest
        val change = writeRequests(i)
        put.getItem.get("ID").getS shouldBe change.getPutRequest.getItem.get("ID").getS
      }
    }
  }
}
