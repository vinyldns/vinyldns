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
import com.typesafe.config.ConfigFactory
import fs2.{Scheduler, Stream}
import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import vinyldns.api.domain.batch.BatchChangeRepository
import vinyldns.api.domain.record._
import vinyldns.api.domain.zone._
import vinyldns.api.engine.sqs.SqsConnection
import vinyldns.api.repository.dynamodb.{
  DynamoDBIntegrationSpec,
  DynamoDBRecordChangeRepository,
  DynamoDBRecordSetRepository,
  DynamoDBZoneChangeRepository
}
import vinyldns.api.repository.mysql.VinylDNSJDBCTestDb

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class ZoneCommandHandlerIntegrationSpec extends DynamoDBIntegrationSpec with Eventually {

  import vinyldns.api.engine.sqs.SqsConverters._

  private implicit val sched: Scheduler =
    Scheduler.fromScheduledExecutorService(Executors.newScheduledThreadPool(2))

  private val zoneName = "vinyldns."
  private val zoneChangeTable = "zoneChangesTest"
  private val recordSetTable = "recordSetTest"
  private val recordChangeTable = "recordChangeTest"

  private val liveTestConfig = ConfigFactory.parseString(s"""
       |  zoneChanges {
       |    # use the dummy store, this should only be used local
       |    dummy = true
       |
       |    dynamo {
       |      tableName = "$zoneChangeTable"
       |      provisionedReads=30
       |      provisionedWrites=30
       |    }
       |  }
       |  recordSet {
       |    # use the dummy store, this should only be used local
       |    dummy = true
       |
       |    dynamo {
       |      tableName = "$recordSetTable"
       |      provisionedReads=30
       |      provisionedWrites=30
       |    }
       |  }
       |  recordChange {
       |    # use the dummy store, this should only be used local
       |    dummy = true
       |
       |    dynamo {
       |      tableName = "$recordChangeTable"
       |      provisionedReads=30
       |      provisionedWrites=30
       |    }
       |  }
    """.stripMargin)

  private val zoneChangeStoreConfig = liveTestConfig.getConfig("zoneChanges")
  private val recordSetStoreConfig = liveTestConfig.getConfig("recordSet")
  private val recordChangeStoreConfig = liveTestConfig.getConfig("recordChange")

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
  private val inDbRecordChange = ChangeSet(RecordSetChange.forSyncAdd(inDbRecordSet, testZone))
  private val inDbZoneChange =
    ZoneChange.forUpdate(testZone.copy(email = "new@test.com"), testZone, okUserAuth)

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
      okUserAuth.signedInUser.id,
      RecordSetChangeType.Create,
      RecordSetChangeStatus.Pending))

  override def anonymize(recordSet: RecordSet): RecordSet = {
    val fakeTime = new DateTime(2010, 1, 1, 0, 0)
    recordSet.copy(id = "a", created = fakeTime, updated = None)
  }

  def setup(): Unit = {
    recordChangeRepo = new DynamoDBRecordChangeRepository(recordChangeStoreConfig, dynamoDBHelper)
    recordSetRepo = new DynamoDBRecordSetRepository(recordSetStoreConfig, dynamoDBHelper)
    zoneChangeRepo = new DynamoDBZoneChangeRepository(zoneChangeStoreConfig, dynamoDBHelper)

    zoneRepo = VinylDNSJDBCTestDb.instance.zoneRepository
    batchChangeRepo = VinylDNSJDBCTestDb.instance.batchChangeRepository
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
        ZoneChange.forUpdate(testZone.copy(email = "updated@test.com"), testZone, okUserAuth)

      sendCommand(change, sqsConn).unsafeRunSync()
      eventually {
        val getZone = zoneRepo.getZone(testZone.id)
        whenReady(getZone) { zn =>
          zn.get.email shouldBe "updated@test.com"
        }
      }
    }

    "process a recordset change" in {
      val change =
        RecordSetChange.forUpdate(inDbRecordSet, inDbRecordSet.copy(ttl = 1234), testZone)
      sendCommand(change, sqsConn).unsafeRunSync()
      eventually {
        val getRs = recordSetRepo.getRecordSet(testZone.id, inDbRecordSet.id)
        whenReady(getRs) { rs =>
          rs.get.ttl shouldBe 1234
        }
      }
    }
    "process a zone sync" in {
      val change = ZoneChange.forSync(testZone, okUserAuth)

      sendCommand(change, sqsConn).unsafeRunSync()
      eventually {
        val validatingQueries = for {
          rs <- recordSetRepo.getRecordSet(testZone.id, inDbRecordSetForSyncTest.id)
          ch <- recordChangeRepo.listRecordSetChanges(testZone.id)
        } yield (rs, ch)

        whenReady(validatingQueries) { data =>
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

  private def waitForSuccess[T](f: => Future[T]): T = {
    val waiting = f.recover { case _ => Thread.sleep(2000); waitForSuccess(f) }
    Await.result[T](waiting, 15.seconds)
  }
}
