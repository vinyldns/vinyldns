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

package vinyldns.api.engine

import java.util.concurrent.Executors

import cats.effect.IO
import cats.implicits._
import fs2.{Scheduler, Stream}
import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}
import vinyldns.api.{DynamoDBApiIntegrationSpec, VinylDNSTestData}
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.core.domain.record._
import vinyldns.api.domain.zone._
import vinyldns.api.engine.sqs.SqsConnection
import vinyldns.api.repository.ApiDataAccessor
import vinyldns.dynamodb.repository.{
  DynamoDBRecordChangeRepository,
  DynamoDBRecordSetRepository,
  DynamoDBRepositorySettings,
  DynamoDBZoneChangeRepository
}
import vinyldns.mysql.repository.TestMySqlInstance
import vinyldns.core.domain.membership.{
  GroupChangeRepository,
  GroupRepository,
  MembershipRepository,
  UserRepository
}
import vinyldns.core.domain.zone._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class ZoneCommandHandlerIntegrationSpec
    extends DynamoDBApiIntegrationSpec
    with VinylDNSTestData
    with MockitoSugar
    with Eventually {

  import vinyldns.api.engine.sqs.SqsConverters._

  private implicit val sched: Scheduler =
    Scheduler.fromScheduledExecutorService(Executors.newScheduledThreadPool(2))

  private val zoneName = "vinyldns."
  private val zoneChangeTable = "zoneChangesTest"
  private val recordSetTable = "recordSetTest"
  private val recordChangeTable = "recordChangeTest"

  private val zoneChangeStoreConfig = DynamoDBRepositorySettings(s"$zoneChangeTable", 30, 30)
  private val recordSetStoreConfig = DynamoDBRepositorySettings(s"$recordSetTable", 30, 30)
  private val recordChangeStoreConfig = DynamoDBRepositorySettings(s"$recordChangeTable", 30, 30)

  private implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))
  private implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private var repositories: ApiDataAccessor = _
  private var sqsConn: SqsConnection = _
  private var str: Stream[IO, Unit] = _
  private val stopSignal = fs2.async.signalOf[IO, Boolean](false).unsafeRunSync()

  // Items to seed in DB
  private val testZone = Zone(
    zoneName,
    "test@test.com",
    ZoneStatus.Active,
    connection =
      Some(ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "127.0.0.1:19001")),
    transferConnection =
      Some(ZoneConnection("vinyldns.", "vinyldns.", "nzisn+4G2ldMn0q1CV3vsg==", "127.0.0.1:19001"))
  )
  private val inDbRecordSet = RecordSet(
    zoneId = testZone.id,
    name = "inDb",
    typ = RecordType.A,
    ttl = 38400,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(AData("1.2.3.4")))
  private val inDbRecordChange = ChangeSet(
    RecordSetChangeGenerator.forSyncAdd(inDbRecordSet, testZone))
  private val inDbZoneChange =
    ZoneChangeGenerator.forUpdate(testZone.copy(email = "new@test.com"), testZone, okAuth)

  private val inDbRecordSetForSyncTest = RecordSet(
    zoneId = testZone.id,
    name = "vinyldns",
    typ = RecordType.A,
    ttl = 38400,
    status = RecordSetStatus.Active,
    created = DateTime.now,
    records = List(AData("5.5.5.5")))
  private val inDbRecordChangeForSyncTest = ChangeSet(
    RecordSetChange(
      testZone,
      inDbRecordSetForSyncTest,
      okAuth.signedInUser.id,
      RecordSetChangeType.Create,
      RecordSetChangeStatus.Pending))

  override def anonymize(recordSet: RecordSet): RecordSet = {
    val fakeTime = new DateTime(2010, 1, 1, 0, 0)
    recordSet.copy(id = "a", created = fakeTime, updated = None)
  }

  def setup(): Unit = {
    val dynamoRepos = (
      DynamoDBRecordSetRepository(recordSetStoreConfig, dynamoIntegrationConfig),
      DynamoDBRecordChangeRepository(recordChangeStoreConfig, dynamoIntegrationConfig),
      DynamoDBZoneChangeRepository(zoneChangeStoreConfig, dynamoIntegrationConfig)
    ).parTupled.unsafeRunSync()

    repositories = ApiDataAccessor(
      mock[UserRepository],
      mock[GroupRepository],
      mock[MembershipRepository],
      mock[GroupChangeRepository],
      dynamoRepos._1,
      dynamoRepos._2,
      dynamoRepos._3,
      TestMySqlInstance.zoneRepository,
      TestMySqlInstance.batchChangeRepository
    )

    sqsConn = SqsConnection()

    //seed items database
    (
      repositories.zoneRepository.save(testZone),
      repositories.recordChangeRepository.save(inDbRecordChange),
      repositories.recordChangeRepository.save(inDbRecordChangeForSyncTest),
      repositories.recordSetRepository.apply(inDbRecordChange),
      repositories.recordSetRepository.apply(inDbRecordChangeForSyncTest),
      repositories.zoneChangeRepository.save(inDbZoneChange)).parTupled.unsafeRunSync()

    str = ZoneCommandHandler.mainFlow(repositories, sqsConn, 100.millis, stopSignal)
    str.compile.drain.unsafeRunAsync { _ =>
      ()
    }
  }

  def tearDown(): Unit = {
    stopSignal.set(true).unsafeRunSync()
    Thread.sleep(2000)
  }

  "ZoneCommandHandler" should {
    "process a zone change" in {
      val change =
        ZoneChangeGenerator.forUpdate(testZone.copy(email = "updated@test.com"), testZone, okAuth)

      sendCommand(change, sqsConn).unsafeRunSync()
      eventually {
        val getZone = repositories.zoneRepository.getZone(testZone.id).unsafeToFuture()
        whenReady(getZone) { zn =>
          zn.get.email shouldBe "updated@test.com"
        }
      }
    }

    "process a recordset change" in {
      val change =
        RecordSetChangeGenerator.forUpdate(inDbRecordSet, inDbRecordSet.copy(ttl = 1234), testZone)
      sendCommand(change, sqsConn).unsafeRunSync()
      eventually {
        val getRs = repositories.recordSetRepository
          .getRecordSet(testZone.id, inDbRecordSet.id)
          .unsafeToFuture()
        whenReady(getRs) { rs =>
          rs.get.ttl shouldBe 1234
        }
      }
    }
    "process a zone sync" in {
      val change = ZoneChangeGenerator.forSync(testZone, okAuth)

      sendCommand(change, sqsConn).unsafeRunSync()
      eventually {
        val validatingQueries = for {
          rs <- repositories.recordSetRepository
            .getRecordSet(testZone.id, inDbRecordSetForSyncTest.id)
          ch <- repositories.recordChangeRepository.listRecordSetChanges(testZone.id)
        } yield (rs, ch)

        whenReady(validatingQueries.unsafeToFuture()) { data =>
          val rs = data._1
          rs.get.name shouldBe "vinyldns."

          val updates = data._2
          val forThisRecord = updates.items.filter(_.recordSet.id == inDbRecordSetForSyncTest.id)

          forThisRecord.length shouldBe 2
          forThisRecord.exists(_.changeType == RecordSetChangeType.Create) shouldBe true
          forThisRecord.exists(_.changeType == RecordSetChangeType.Update) shouldBe true
        }
      }
    }
  }
}
