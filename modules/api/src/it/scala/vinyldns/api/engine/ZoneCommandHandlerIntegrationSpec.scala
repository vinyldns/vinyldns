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
import org.scalatest.time.{Millis, Seconds, Span}
import vinyldns.api.{DynamoDBApiIntegrationSpec, VinylDNSTestData}
import vinyldns.api.domain.record.RecordSetChangeGenerator
import vinyldns.core.domain.batch.BatchChangeRepository
import vinyldns.core.domain.record._
import vinyldns.api.domain.zone._
import vinyldns.api.engine.sqs.SqsConnection
import vinyldns.dynamodb.repository.{
  DynamoDBRecordChangeRepository,
  DynamoDBRecordSetRepository,
  DynamoDBRepositorySettings,
  DynamoDBZoneChangeRepository
}
import vinyldns.api.repository.mysql.TestMySqlInstance
import vinyldns.core.domain.zone._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class ZoneCommandHandlerIntegrationSpec
    extends DynamoDBApiIntegrationSpec
    with VinylDNSTestData
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

  private var recordChangeRepo: RecordChangeRepository = _
  private var recordSetRepo: RecordSetRepository = _
  private var zoneChangeRepo: ZoneChangeRepository = _
  private var zoneRepo: ZoneRepository = _
  private var batchChangeRepo: BatchChangeRepository = _
  private var sqsConn: SqsConnection = _
  private var str: Stream[IO, Unit] = _
  private val stopSignal = fs2.async.signalOf[IO, Boolean](false).unsafeRunSync()

  // Items to seed in DB
  private val testZone = Zone(
    zoneName,
    "test@test.com",
    ZoneStatus.Active,
    connection = Some(
      ZoneConnection(
        "vinyldns.",
        "vinyldns.",
        "wCZZS9lyRr77+jqfnkZ/92L9fD5ilmfrG0sslc3mgmTFsF1fRgmtJ0rj RkFITt8VHQ37wvM/nI9MAIWXYTvMqg==",
        "127.0.0.1:19001")),
    transferConnection = Some(
      ZoneConnection(
        "vinyldns.",
        "vinyldns.",
        "wCZZS9lyRr77+jqfnkZ/92L9fD5ilmfrG0sslc3mgmTFsF1fRgmtJ0rj RkFITt8VHQ37wvM/nI9MAIWXYTvMqg==",
        "127.0.0.1:19001"))
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
    val repos = (
      DynamoDBRecordChangeRepository(recordChangeStoreConfig, dynamoIntegrationConfig),
      DynamoDBRecordSetRepository(recordSetStoreConfig, dynamoIntegrationConfig),
      DynamoDBZoneChangeRepository(zoneChangeStoreConfig, dynamoIntegrationConfig)
    ).parTupled.unsafeRunSync()

    recordChangeRepo = repos._1
    recordSetRepo = repos._2
    zoneChangeRepo = repos._3
    zoneRepo = TestMySqlInstance.zoneRepository
    batchChangeRepo = TestMySqlInstance.batchChangeRepository
    sqsConn = SqsConnection()

    //seed items database
    waitForSuccess(zoneRepo.save(testZone))
    waitForSuccess(recordChangeRepo.save(inDbRecordChange))
    waitForSuccess(recordChangeRepo.save(inDbRecordChangeForSyncTest))
    waitForSuccess(recordSetRepo.apply(inDbRecordChange))
    waitForSuccess(recordSetRepo.apply(inDbRecordChangeForSyncTest))
    waitForSuccess(zoneChangeRepo.save(inDbZoneChange))
    // Run a noop query to make sure recordSetRepo is up
    waitForSuccess(recordSetRepo.listRecordSets("1", None, None, None))

    str = ZoneCommandHandler.mainFlow(
      zoneRepo,
      zoneChangeRepo,
      recordSetRepo,
      recordChangeRepo,
      batchChangeRepo,
      sqsConn,
      100.millis,
      stopSignal)
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
        val getZone = zoneRepo.getZone(testZone.id).unsafeToFuture()
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
        val getRs = recordSetRepo.getRecordSet(testZone.id, inDbRecordSet.id).unsafeToFuture()
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
          rs <- recordSetRepo.getRecordSet(testZone.id, inDbRecordSetForSyncTest.id)
          ch <- recordChangeRepo.listRecordSetChanges(testZone.id)
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

  private def waitForSuccess[T](f: => IO[T]): T = {
    val waiting = f.unsafeToFuture().recover { case _ => Thread.sleep(2000); waitForSuccess(f) }
    Await.result[T](waiting, 15.seconds)
  }
}
