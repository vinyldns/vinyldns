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

import com.amazonaws.services.dynamodbv2.model.{AttributeValue, _}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}

import scala.collection.JavaConverters._
import cats.effect._

class QueryHelperSpec
    extends WordSpec
    with MockitoSugar
    with Matchers
    with ScalaFutures
    with BeforeAndAfterEach {

  private val dynamoDBHelper = mock[DynamoDBHelper]

  class TestQueryHelper extends QueryHelper
  private val underTest = new TestQueryHelper

  override def beforeEach(): Unit = reset(dynamoDBHelper)

  def makeJavaItem(value: String): util.HashMap[String, AttributeValue] = {
    val item = new util.HashMap[String, AttributeValue]()
    item.put("key", new AttributeValue(value))
    item
  }

  def await[T](f: => IO[_]): T =
    f.map(_.asInstanceOf[T]).unsafeRunSync()

  "QueryHelper" should {
    "run a query with no filter where there is no continuation" in {
      val keyConditions = Map[String, String]("key" -> "value")

      val dynamoResponse = mock[QueryResult]
      val expectedItems = new util.ArrayList[util.HashMap[String, AttributeValue]]()
      expectedItems.add(makeJavaItem("item1"))
      expectedItems.add(makeJavaItem("item2"))

      doReturn(expectedItems).when(dynamoResponse).getItems
      doReturn(null).when(dynamoResponse).getLastEvaluatedKey
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val result = await[QueryResponseItems](
        underTest.doQuery("testName", "testIndex", keyConditions, None, None, Some(3))(
          dynamoDBHelper
        )
      )

      result.lastEvaluatedKey shouldBe None
      result.items shouldBe expectedItems.asScala
    }

    "run a query with no filter where there is a continuation key" in {
      val keyConditions = Map[String, String]("key" -> "value")

      val dynamoResponse = mock[QueryResult]
      val expectedItems = new util.ArrayList[util.HashMap[String, AttributeValue]]()
      expectedItems.add(makeJavaItem("item1"))
      expectedItems.add(makeJavaItem("item2"))
      expectedItems.add(makeJavaItem("item3"))
      doReturn(expectedItems).when(dynamoResponse).getItems
      val key = makeJavaItem("item3")
      doReturn(key).when(dynamoResponse).getLastEvaluatedKey

      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val result = await[QueryResponseItems](
        underTest.doQuery("testName", "testIndex", keyConditions, None, None, Some(3))(
          dynamoDBHelper
        )
      )

      result.lastEvaluatedKey shouldBe Some(key)
      result.items shouldBe expectedItems.asScala
    }

    "run a query with a filter requiring multiple query requests" in {
      val keyConditions = Map[String, String]("key" -> "value")
      val filterExpression = Some(ContainsFilter("filterkey", "filtervalue"))

      val firstQuery =
        QueryManager("testName", "testIndex", keyConditions, filterExpression, None, None, false)
          .build()
      val secondQuery = QueryManager(
        "testName",
        "testIndex",
        keyConditions,
        filterExpression,
        Some(Map("key" -> "item3")),
        None,
        false
      ).build()

      val firstResponse = mock[QueryResult]
      val items1 = new util.ArrayList[util.HashMap[String, AttributeValue]]()
      items1.add(makeJavaItem("item1"))
      items1.add(makeJavaItem("item2"))
      items1.add(makeJavaItem("item3"))
      doReturn(items1).when(firstResponse).getItems
      doReturn(makeJavaItem("item3")).when(firstResponse).getLastEvaluatedKey

      val secondResponse = mock[QueryResult]
      val items2 = new util.ArrayList[util.HashMap[String, AttributeValue]]()
      items2.add(makeJavaItem("item4"))
      items2.add(makeJavaItem("item5"))
      items2.add(makeJavaItem("item6"))
      doReturn(items2).when(secondResponse).getItems

      doReturn(IO.pure(firstResponse)).when(dynamoDBHelper).query(firstQuery)
      doReturn(IO.pure(secondResponse)).when(dynamoDBHelper).query(secondQuery)

      val result = await[QueryResponseItems](
        underTest.doQuery("testName", "testIndex", keyConditions, filterExpression, None, Some(4))(
          dynamoDBHelper
        )
      )

      result.lastEvaluatedKey shouldBe Some(makeJavaItem("item4"))
      result.items shouldBe (items1.asScala ++ items2.asScala).take(4)
    }

    "run a query with a filter requiring multiple query requests where no key at end" in {
      val keyConditions = Map[String, String]("key" -> "value")
      val filterExpression = Some(ContainsFilter("filterkey", "filtervalue"))

      val firstQuery =
        QueryManager("testName", "testIndex", keyConditions, filterExpression, None, None, false)
          .build()
      val secondQuery = QueryManager(
        "testName",
        "testIndex",
        keyConditions,
        filterExpression,
        Some(Map("key" -> "item3")),
        None,
        false
      ).build()

      val firstResponse = mock[QueryResult]
      val items1 = new util.ArrayList[util.HashMap[String, AttributeValue]]()
      items1.add(makeJavaItem("item1"))
      items1.add(makeJavaItem("item2"))
      items1.add(makeJavaItem("item3"))
      doReturn(items1).when(firstResponse).getItems
      doReturn(makeJavaItem("item3")).when(firstResponse).getLastEvaluatedKey

      val secondResponse = mock[QueryResult]
      val items2 = new util.ArrayList[util.HashMap[String, AttributeValue]]()
      items2.add(makeJavaItem("item4"))
      items2.add(makeJavaItem("item5"))
      items2.add(makeJavaItem("item6"))
      doReturn(items2).when(secondResponse).getItems
      doReturn(null).when(secondResponse).getLastEvaluatedKey

      doReturn(IO.pure(firstResponse)).when(dynamoDBHelper).query(firstQuery)
      doReturn(IO.pure(secondResponse)).when(dynamoDBHelper).query(secondQuery)

      val result = await[QueryResponseItems](
        underTest.doQuery("testName", "testIndex", keyConditions, filterExpression, None, Some(6))(
          dynamoDBHelper
        )
      )

      result.lastEvaluatedKey shouldBe None
      result.items shouldBe items1.asScala ++ items2.asScala
    }

    "run a query with count returns QueryResponseCount" in {
      val keyConditions = Map[String, String]("key" -> "value")
      val dynamoResponse = mock[QueryResult]

      doReturn(5).when(dynamoResponse).getCount
      doReturn(null).when(dynamoResponse).getLastEvaluatedKey
      doReturn(IO.pure(dynamoResponse)).when(dynamoDBHelper).query(any[QueryRequest])

      val result = await[QueryResponseCount](
        underTest
          .doQuery("testName", "testIndex", keyConditions, None, None, None, isCountQuery = true)(
            dynamoDBHelper
          )
      )

      result.count shouldBe 5
    }

    "run a query with count works when there are multiple pages" in {
      val keyConditions = Map[String, String]("key" -> "value")
      val dynamoResponse1 = mock[QueryResult]
      val dynamoResponse2 = mock[QueryResult]

      doReturn(makeJavaItem("item")).when(dynamoResponse1).getLastEvaluatedKey
      doReturn(5).when(dynamoResponse1).getCount

      doReturn(null).when(dynamoResponse2).getLastEvaluatedKey
      doReturn(2).when(dynamoResponse2).getCount

      doReturn(IO.pure(dynamoResponse1))
        .doReturn(IO.pure(dynamoResponse2))
        .when(dynamoDBHelper)
        .query(any[QueryRequest])

      val result = await[QueryResponseCount](
        underTest
          .doQuery("testName", "testIndex", keyConditions, None, None, None, isCountQuery = true)(
            dynamoDBHelper
          )
      )

      result.count shouldBe 7
    }
  }
}
