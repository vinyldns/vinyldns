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

import java.util
import java.util.Collections

import com.amazonaws.services.dynamodbv2.model._
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.{Seconds, Span}
import vinyldns.core.domain.membership.{Group, GroupStatus}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class DynamoDBGroupRepositoryIntegrationSpec extends DynamoDBIntegrationSpec {

  private implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val GROUP_TABLE = "groups-live"

  private val tableConfig = ConfigFactory.parseString(s"""
       | dynamo {
       |   tableName = "$GROUP_TABLE"
       |   provisionedReads=30
       |   provisionedWrites=30
       | }
    """.stripMargin).withFallback(ConfigFactory.load())

  private var repo: DynamoDBGroupRepository = _

  private val activeGroups =
    for (i <- 1 to 10)
      yield
        Group(
          s"live-test-group$i",
          s"test$i@test.com",
          Some(s"description$i"),
          memberIds = Set(s"member$i", s"member2$i"),
          adminUserIds = Set(s"member$i", s"member2$i"),
          id = "id-%03d".format(i)
        )

  private val inDbDeletedGroup = Group(
    s"live-test-group-deleted",
    s"test@test.com",
    Some(s"description"),
    memberIds = Set("member1"),
    adminUserIds = Set("member1"),
    id = "id-deleted-group",
    status = GroupStatus.Deleted
  )
  private val groups = activeGroups ++ List(inDbDeletedGroup)

  private val timeout = PatienceConfiguration.Timeout(Span(10, Seconds))

  def setup(): Unit = {
    repo = new DynamoDBGroupRepository(tableConfig, dynamoDBHelper)

    // wait until the repo is ready, could take time if the table has to be created
    var notReady = true
    while (notReady) {
      val result = Await.ready(repo.getGroup("any").unsafeToFuture(), 5.seconds)
      notReady = result.value.get.isFailure
      Thread.sleep(2000)
    }

    clearGroups()

    // Create all the zones
    val savedGroups = Future.sequence(groups.map(repo.save(_).unsafeToFuture()))

    // Wait until all of the zones are done
    Await.result(savedGroups, 5.minutes)
  }

  def tearDown(): Unit = {
    val request = new DeleteTableRequest().withTableName(GROUP_TABLE)
    val deleteTables = dynamoDBHelper.deleteTable(request)
    Await.ready(deleteTables.unsafeToFuture(), 100.seconds)
  }

  private def clearGroups(): Unit = {

    import scala.collection.JavaConverters._

    val scanRequest = new ScanRequest().withTableName(GROUP_TABLE)

    val allGroups = dynamoClient.scan(scanRequest).getItems.asScala.map(repo.fromItem)

    val batchWrites = allGroups
      .map { group =>
        val key = new util.HashMap[String, AttributeValue]()
        key.put("group_id", new AttributeValue(group.id))
        new WriteRequest().withDeleteRequest(new DeleteRequest().withKey(key))
      }
      .grouped(25)
      .map { deleteRequests =>
        new BatchWriteItemRequest()
          .withRequestItems(Collections.singletonMap(GROUP_TABLE, deleteRequests.asJava))
      }
      .toList

    batchWrites.foreach { batch =>
      dynamoClient.batchWriteItem(batch)
    }
  }

  "DynamoDBGroupRepository" should {
    "get a group by id" in {
      val targetGroup = groups.head
      whenReady(repo.getGroup(targetGroup.id).unsafeToFuture(), timeout) { retrieved =>
        retrieved.get shouldBe targetGroup
      }
    }

    "get all active groups" in {
      whenReady(repo.getAllGroups().unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe activeGroups.toSet
      }
    }

    "not return a deleted group when getting group by id" in {
      val deleted = deletedGroup.copy(memberIds = Set("foo"), adminUserIds = Set("foo"))
      val f =
        for {
          _ <- repo.save(deleted)
          retrieved <- repo.getGroup(deleted.id)
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe None
      }
    }

    "not return a deleted group when getting group by name" in {
      val deleted = deletedGroup.copy(memberIds = Set("foo"), adminUserIds = Set("foo"))
      val f =
        for {
          _ <- repo.save(deleted)
          retrieved <- repo.getGroupByName(deleted.name)
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved shouldBe None
      }
    }

    "get groups should omit non existing groups" in {
      val f = repo.getGroups(Set(activeGroups.head.id, "thisdoesnotexist"))
      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved.map(_.id) should contain theSameElementsAs Set(activeGroups.head.id)
      }
    }

    "returns all the groups" in {
      val f = repo.getGroups(groups.map(_.id).toSet)

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved should contain theSameElementsAs activeGroups
      }
    }

    "only return requested groups" in {
      val evenGroups = activeGroups.filter(_.id.takeRight(1).toInt % 2 == 0)
      val f = repo.getGroups(evenGroups.map(_.id).toSet)

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved should contain theSameElementsAs evenGroups
      }
    }

    "return an Empty set if nothing found" in {
      val f = repo.getGroups(Set("notFound"))

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved should contain theSameElementsAs Set()
      }
    }

    "not return deleted groups" in {
      val deleted = deletedGroup.copy(
        id = "test-deleted-group-get-groups",
        memberIds = Set("foo"),
        adminUserIds = Set("foo"))
      val f =
        for {
          _ <- repo.save(deleted)
          retrieved <- repo.getGroups(Set(deleted.id, groups.head.id))
        } yield retrieved

      whenReady(f.unsafeToFuture(), timeout) { retrieved =>
        retrieved.map(_.id) shouldBe Set(groups.head.id)
      }
    }

    "get a group by name" in {
      val targetGroup = groups.head
      whenReady(repo.getGroupByName(targetGroup.name).unsafeToFuture(), timeout) { retrieved =>
        retrieved.get shouldBe targetGroup
      }
    }

    "save a group with no description" in {
      val group = Group(
        "null-description",
        "test@test.com",
        None,
        memberIds = Set("foo"),
        adminUserIds = Set("bar"))

      val test =
        for {
          saved <- repo.save(group)
          retrieved <- repo.getGroup(saved.id)
        } yield retrieved

      whenReady(test.unsafeToFuture(), timeout) { saved =>
        saved.get.description shouldBe None
      }
    }
  }
}
