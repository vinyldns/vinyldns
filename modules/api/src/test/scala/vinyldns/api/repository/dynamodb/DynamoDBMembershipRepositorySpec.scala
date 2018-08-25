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

import com.amazonaws.services.dynamodbv2.model.{BatchWriteItemResult, _}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import vinyldns.api.{GroupTestData, ResultHelpers, VinylDNSConfig}

import scala.collection.JavaConverters._
import cats.effect._, cats.effect.implicits._, cats.instances.future._
import scala.concurrent.duration.FiniteDuration

class DynamoDBMembershipRepositorySpec
    extends WordSpec
    with MockitoSugar
    with Matchers
    with GroupTestData
    with ResultHelpers
    with ScalaFutures
    with BeforeAndAfterEach {

  private implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))
  private val membershipStoreConfig = VinylDNSConfig.membershipStoreConfig
  private val membershipTable = VinylDNSConfig.membershipStoreConfig.getString("dynamo.tableName")
  private val dynamoDBHelper = mock[DynamoDBHelper]
  class TestDynamoDBMembershipRepository
      extends DynamoDBMembershipRepository(membershipStoreConfig, dynamoDBHelper) {
    override def loadData: IO[Set[Set[String]]] = IO.pure(Set(Set()))
  }

  private val underTest = new TestDynamoDBMembershipRepository

  override def beforeEach(): Unit = reset(dynamoDBHelper)

  "DynamoDBMembershipRepository.apply" should {

    "call setup table when it is built" in {
      val setupTableCaptor = ArgumentCaptor.forClass(classOf[CreateTableRequest])
      new TestDynamoDBMembershipRepository()
      verify(dynamoDBHelper).setupTable(setupTableCaptor.capture())

      val req = setupTableCaptor.getValue
      req.getTableName shouldBe membershipTable
    }

    "fail when an exception is thrown setting up the table" in {
      doThrow(new RuntimeException("fail")).when(dynamoDBHelper).setupTable(any[CreateTableRequest])
      a[RuntimeException] should be thrownBy new TestDynamoDBMembershipRepository()
    }
  }

  "DynamoDBMembershipRepository.addMembers" should {
    "add the members in batches and return the members that were added to a group" in {
      val members = (for (i <- 1 to 60) yield s"member-${i}").toSet
      val batchCaptor = ArgumentCaptor.forClass(classOf[BatchWriteItemRequest])
      val dynamoResponse = mock[BatchWriteItemResult]
      val unprocessed = mock[java.util.Map[String, AttributeValue]]
      doReturn(null).when(unprocessed).get(anyString())
      doReturn(unprocessed).when(dynamoResponse).getUnprocessedItems

      val store = new TestDynamoDBMembershipRepository
      doReturn(IO.pure(dynamoResponse))
        .when(dynamoDBHelper)
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])

      val response = await[Set[String]](store.addMembers(okGroup.id, members))

      verify(dynamoDBHelper, times(3)).batchWriteItem(
        any[String],
        batchCaptor.capture(),
        any[Int],
        any[FiniteDuration])

      // we should have 3 batches
      val batchWrites = batchCaptor.getAllValues

      batchWrites.get(0).getRequestItems.get(store.membershipTable).size() shouldBe 25
      batchWrites.get(1).getRequestItems.get(store.membershipTable).size() shouldBe 25
      batchWrites.get(2).getRequestItems.get(store.membershipTable).size() shouldBe 10

      response should contain theSameElementsAs members
    }

    "add the members in a single batch if there are less than 25 members to be added" in {
      val members = (for (i <- 1 to 20) yield s"member-${i}").toSet
      val batchCaptor = ArgumentCaptor.forClass(classOf[BatchWriteItemRequest])
      val dynamoResponse = mock[BatchWriteItemResult]
      val unprocessed = mock[java.util.Map[String, AttributeValue]]
      doReturn(null).when(unprocessed).get(anyString())
      doReturn(unprocessed).when(dynamoResponse).getUnprocessedItems

      val store = new TestDynamoDBMembershipRepository
      doReturn(IO.pure(dynamoResponse))
        .when(dynamoDBHelper)
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])

      val response = await[Set[String]](store.addMembers(okGroup.id, members))

      verify(dynamoDBHelper, times(1)).batchWriteItem(
        any[String],
        batchCaptor.capture(),
        any[Int],
        any[FiniteDuration])

      val batchWrites = batchCaptor.getAllValues
      batchWrites.get(0).getRequestItems.get(store.membershipTable).size() shouldBe 20
      response should contain theSameElementsAs members
    }

    "throw an exception if thrown by dynamo" in {
      val members = (for (i <- 1 to 30) yield s"member-${i}").toSet
      val dynamoResponse = mock[BatchWriteItemResult]
      val unprocessed = mock[java.util.Map[String, AttributeValue]]
      doReturn(null).when(unprocessed).get(anyString())
      doReturn(unprocessed).when(dynamoResponse).getUnprocessedItems

      val store = new TestDynamoDBMembershipRepository
      doReturn(IO.pure(dynamoResponse))
        .doThrow(new RuntimeException("failed"))
        .when(dynamoDBHelper)
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])

      val response = store.addMembers(okGroup.id, members)
      whenReady(response.failed)(_ shouldBe a[RuntimeException])
    }
  }

  "DynamoDBMembershipRepository.removeMembers" should {
    "remove the members in batches and return the members that were removed from the group" in {
      val members = (for (i <- 1 to 60) yield s"member-${i}").toSet
      val batchCaptor = ArgumentCaptor.forClass(classOf[BatchWriteItemRequest])
      val dynamoResponse = mock[BatchWriteItemResult]
      val unprocessed = mock[java.util.Map[String, AttributeValue]]
      doReturn(null).when(unprocessed).get(anyString())
      doReturn(unprocessed).when(dynamoResponse).getUnprocessedItems

      val store = new TestDynamoDBMembershipRepository
      doReturn(IO.pure(dynamoResponse))
        .when(dynamoDBHelper)
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])

      val response = await[Set[String]](store.removeMembers(okGroup.id, members))

      verify(dynamoDBHelper, times(3)).batchWriteItem(
        any[String],
        batchCaptor.capture(),
        any[Int],
        any[FiniteDuration])

      // we should have 3 batches
      val batchWrites = batchCaptor.getAllValues

      batchWrites.get(0).getRequestItems.get(store.membershipTable).size() shouldBe 25
      batchWrites.get(1).getRequestItems.get(store.membershipTable).size() shouldBe 25
      batchWrites.get(2).getRequestItems.get(store.membershipTable).size() shouldBe 10

      response should contain theSameElementsAs members
    }

    "remove the members in a single batch if there are less than 25 members to be removed" in {
      val members = (for (i <- 1 to 20) yield s"member-${i}").toSet
      val batchCaptor = ArgumentCaptor.forClass(classOf[BatchWriteItemRequest])
      val dynamoResponse = mock[BatchWriteItemResult]
      val unprocessed = mock[java.util.Map[String, AttributeValue]]
      doReturn(null).when(unprocessed).get(anyString())
      doReturn(unprocessed).when(dynamoResponse).getUnprocessedItems

      val store = new TestDynamoDBMembershipRepository
      doReturn(IO.pure(dynamoResponse))
        .when(dynamoDBHelper)
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])

      val response = await[Set[String]](store.removeMembers(okGroup.id, members))

      verify(dynamoDBHelper, times(1)).batchWriteItem(
        any[String],
        batchCaptor.capture(),
        any[Int],
        any[FiniteDuration])

      val batchWrites = batchCaptor.getAllValues
      batchWrites.get(0).getRequestItems.get(store.membershipTable).size() shouldBe 20
      response should contain theSameElementsAs members
    }

    "throw an exception if thrown by dynamo" in {
      val members = (for (i <- 1 to 30) yield s"member-${i}").toSet
      val dynamoResponse = mock[BatchWriteItemResult]
      val unprocessed = mock[java.util.Map[String, AttributeValue]]
      doReturn(null).when(unprocessed).get(anyString())
      doReturn(unprocessed).when(dynamoResponse).getUnprocessedItems

      val store = new TestDynamoDBMembershipRepository
      doReturn(IO.pure(dynamoResponse))
        .doThrow(new RuntimeException("failed"))
        .when(dynamoDBHelper)
        .batchWriteItem(any[String], any[BatchWriteItemRequest], any[Int], any[FiniteDuration])

      val response = store.removeMembers(okGroup.id, members)
      whenReady(response.failed)(_ shouldBe a[RuntimeException])
    }
  }

  "DynamoDBMembershipRepository.getGroupsForUser" should {
    "returns empty if no groups exist" in {
      val dynamoResponse = mock[QueryResult]
      when(dynamoResponse.getItems)
        .thenReturn(new java.util.ArrayList[java.util.Map[String, AttributeValue]]())
      when(dynamoDBHelper.query(any[QueryRequest])).thenReturn(IO.pure(dynamoResponse))

      val store = new TestDynamoDBMembershipRepository
      val response = await[Set[String]](store.getGroupsForUser(okUser.id))
      verify(dynamoDBHelper).query(any[QueryRequest])
      response shouldBe empty
    }
    "returns groups found for user" in {
      val dynamoResponse = mock[QueryResult]

      val expected = for (i <- 1 to 30) yield s"group-$i"
      val resultList = expected.map(underTest.toItem(okUser.id, _)).asJava
      when(dynamoResponse.getItems).thenReturn(resultList)
      when(dynamoDBHelper.query(any[QueryRequest])).thenReturn(IO.pure(dynamoResponse))

      val store = new TestDynamoDBMembershipRepository
      val response = await[Set[String]](store.getGroupsForUser(okUser.id))
      verify(dynamoDBHelper).query(any[QueryRequest])
      response should contain theSameElementsAs expected
    }
    "throw exception when query returns an unexpected response" in {
      val store = new TestDynamoDBMembershipRepository
      when(dynamoDBHelper.query(any[QueryRequest])).thenThrow(new ResourceNotFoundException("foo"))
      a[RuntimeException] should be thrownBy store.getGroupsForUser(okUser.id)
    }
  }
}
