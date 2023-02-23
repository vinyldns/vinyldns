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

package vinyldns.mysql.queue

import cats.data._
import cats.effect._
import cats.implicits._
import cats.scalatest.EitherMatchers
import com.typesafe.config.{Config, ConfigFactory}
import java.time.Instant
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pureconfig._
import pureconfig.generic.auto._
import scalikejdbc._
import vinyldns.core.domain.batch.BatchChangeCommand
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.{ZoneChange, ZoneCommand}
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue._
import vinyldns.mysql.queue.MessageType.{
  InvalidMessageType,
  RecordChangeMessageType,
  ZoneChangeMessageType
}
import vinyldns.mysql.queue.MySqlMessageQueue.{
  InvalidMessageTimeout,
  MessageAttemptsExceeded,
  QUEUE_CONNECTION_NAME
}
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._

final case class RowData(
    id: String,
    messageType: Int,
    inFlight: Boolean,
    createdTime: Instant,
    updatedTime: Instant,
    timeoutSecs: Int,
    attempts: Int
)

final case class InvalidMessage(command: ZoneCommand) extends CommandMessage {
  def id: MessageId = MessageId(command.id)
}

class MySqlMessageQueueIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterEach
    with EitherMatchers
    with EitherValues
    with ProtobufConversions {
  import vinyldns.core.TestRecordSetData._
  import vinyldns.core.TestZoneData._

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  private val config: Config = ConfigFactory.load().getConfig("queue")
  lazy val queueConfig: MessageQueueConfig =
    ConfigSource.fromConfig(config).loadOrThrow[MessageQueueConfig]

  private val underTest =
    MessageQueueLoader.load(queueConfig).map(_.asInstanceOf[MySqlMessageQueue]).unsafeRunSync()

  private val rsChange: RecordSetChange = pendingCreateAAAA

  private val rsChangeBytes = toPB(rsChange).toByteArray

  private val testMessage: MySqlMessage =
    MySqlMessage(MessageId(rsChange.id), 0, 20.seconds, rsChange)

  private val zoneChange: ZoneChange = zoneChangePending

  private def clear(): Unit = NamedDB(QUEUE_CONNECTION_NAME).localTx { implicit s =>
    sql"DELETE FROM message_queue".update().apply()
    ()
  }

  override protected def beforeEach(): Unit = clear()

  private def insert(
      id: String,
      messageType: Int,
      inFlight: Boolean,
      data: Array[Byte],
      created: Instant,
      updated: Instant,
      timeoutSeconds: Int,
      attempts: Int
  ): Unit = {
    NamedDB(QUEUE_CONNECTION_NAME).localTx { implicit s =>
      val inF = if (inFlight) 1 else 0
      val insertSql = sql"""
         |INSERT INTO message_queue(id, message_type, in_flight, created, updated, timeout_seconds, attempts, data)
         |     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """.stripMargin
      insertSql
        .bind(id, messageType, inF, created, updated, timeoutSeconds, attempts, data)
        .update()
        .apply()
    }
    ()
  }

  private def findMessage(id: String): Option[RowData] =
    NamedDB(QUEUE_CONNECTION_NAME).readOnly { implicit s =>
      val findSql =
        sql"""
          |SELECT id, message_type, in_flight, created, updated, timeout_seconds, attempts
          |  FROM message_queue
          | WhERE id = ?
        """.stripMargin

      findSql
        .bind(id)
        .map { rs =>
          RowData(
            rs.string(1),
            rs.int(2),
            rs.boolean(3),
            Instant.ofEpochMilli(rs.timestamp(4).getTime),
            Instant.ofEpochMilli(rs.timestamp(5).getTime),
            rs.int(6),
            rs.int(7)
          )
        }
        .toOption()
        .apply()
    }

  "send receive" should {
    "handle a record change" in {
      underTest.send(rsChange).unsafeRunSync()
      val r = underTest.receive(MessageCount(1).right.value).unsafeRunSync()

      r.headOption.map(_.command) shouldBe Some(rsChange)
    }
    "handle a zone change" in {
      underTest.send(zoneChange).unsafeRunSync()
      val r = underTest.receive(MessageCount(1).right.value).unsafeRunSync()

      r.headOption.map(_.command) shouldBe Some(zoneChange)
    }
    "handle a batch" in {
      val first = rsChange
      val second = rsChange.copy(id = "second")
      val s = underTest.sendBatch(NonEmptyList.of(first, second)).unsafeRunSync()
      s.successes should contain theSameElementsAs List(first, second)

      val r = underTest.receive(MessageCount(2).right.value).unsafeRunSync()
      r.map(_.command) should contain theSameElementsAs List(first, second)
    }
    "handle a batch command" in {
      val batchChangeCommand = BatchChangeCommand("some-id")
      underTest.send(batchChangeCommand).unsafeRunSync()
      val r = underTest.receive(MessageCount(1).right.value).unsafeRunSync()

      r.headOption.map(_.command) shouldBe Some(batchChangeCommand)
    }
    "be idempotent" in {
      // Put the same change in twice, get one out
      underTest.sendBatch(NonEmptyList.of(rsChange)).unsafeRunSync()
      underTest.sendBatch(NonEmptyList.of(rsChange)).unsafeRunSync()
      val r = underTest.receive(MessageCount(8).right.value).unsafeRunSync()
      (r should have).length(1)
      r.headOption.map(_.command) shouldBe Some(rsChange)
    }
    "be idempotent for a batch" in {
      // Send a batch
      val first = rsChange
      val second = rsChange.copy(id = "second")
      underTest.sendBatch(NonEmptyList.of(first, second)).unsafeRunSync()

      // Send another batch, with two new and two old
      val third = rsChange.copy(id = "third")
      val fourth = rsChange.copy(id = "fourth")
      val r = underTest.sendBatch(NonEmptyList.of(first, third, second, fourth)).unsafeRunSync()
      r.successes should contain theSameElementsAs List(first, third, second, fourth)

      // Receive a batch, make sure we only get 4 out, no duplicates
      val batch = underTest.receive(MessageCount(10).right.value).unsafeRunSync()
      (batch should have).length(4)
      batch.map(_.command) should contain theSameElementsAs List(first, second, third, fourth)
    }
    "work in parallel" in {
      // send multiple messages in parallel
      val changes = for { i <- 0 to 8 } yield rsChange.copy(id = s"chg$i")
      val sends = changes.map(rc => underTest.send(rc))

      // receive batches of 1 in parallel
      val gets = for { _ <- 0 to 8 } yield underTest.receive(MessageCount(1).right.value)

      // let's fire them both off, doesn't matter who finishes, as long as the IO does not fail
      val result =
        IO.race(sends.toList.parSequence, gets.toList.parSequence).attempt.unsafeRunSync()
      result shouldBe right
    }
  }

  "parseMessage" should {
    "fail on invalid message type" in {
      val result = underTest.parseMessage(MessageId("foo"), -2, rsChangeBytes, 1, 10)
      result.left.value shouldBe (InvalidMessageType(-2), MessageId("foo"))
    }
    "fail on invalid bytes" in {
      val result =
        underTest.parseMessage(MessageId("foo"), ZoneChangeMessageType.value, "bar".getBytes, 1, 10)
      val (err, id) = result.left.value
      id shouldBe MessageId("foo")
      err shouldBe an[Exception]
    }
    "fail if attempts exceeds 100" in {
      val result = underTest.parseMessage(
        MessageId("foo"),
        RecordChangeMessageType.value,
        rsChangeBytes,
        200,
        10
      )
      result.left.value shouldBe (MessageAttemptsExceeded("foo"), MessageId("foo"))
    }
    "fail on invalid timeout" in {
      val result = underTest.parseMessage(
        MessageId("foo"),
        RecordChangeMessageType.value,
        rsChangeBytes,
        1,
        -1
      )
      result.left.value shouldBe (InvalidMessageTimeout(-1), MessageId("foo"))
    }
  }

  "receive" should {
    "drop messages that have an invalid message type" in {
      insert("foo", 23, false, rsChangeBytes, Instant.now.truncatedTo(ChronoUnit.MILLIS), Instant.now.truncatedTo(ChronoUnit.MILLIS), 100, 0)
      underTest.receive(MessageCount(1).right.value).unsafeRunSync()

      findMessage("foo") should not be defined
    }
    "drop messages that have invalid message data" in {
      insert(
        rsChange.id,
        RecordChangeMessageType.value,
        false,
        "blah".getBytes,
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        100,
        0
      )
      underTest.receive(MessageCount(1).right.value).unsafeRunSync()

      findMessage(rsChange.id) should not be defined
    }
    "drop messages that have expired" in {
      insert(
        rsChange.id,
        RecordChangeMessageType.value,
        false,
        "blah".getBytes,
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        Instant.now.truncatedTo(ChronoUnit.MILLIS),
        100,
        101
      )
      underTest.receive(MessageCount(1).right.value).unsafeRunSync()

      findMessage(rsChange.id) should not be defined
    }
    "increment the attempt, timestamp, and in flight status" in {
      val initialAttempts = 0
      val initialTs = Instant.now.truncatedTo(ChronoUnit.MILLIS).minusSeconds(20)
      insert(
        rsChange.id,
        RecordChangeMessageType.value,
        false,
        rsChangeBytes,
        initialTs,
        initialTs,
        100,
        initialAttempts
      )

      val oldMsg = findMessage(rsChange.id).getOrElse(fail)
      underTest.receive(MessageCount(1).right.value).unsafeRunSync()

      val msg = findMessage(rsChange.id).getOrElse(fail)
      msg.attempts shouldBe oldMsg.attempts + 1
      msg.updatedTime.toEpochMilli should be > oldMsg.updatedTime.toEpochMilli
      msg.inFlight shouldBe true
    }
    "grab messages that are in flight but expired" in {
      // put a message in whose updated timestamp was 100 seconds ago, set the timeout to 30 seconds
      val initialTs = Instant.now.truncatedTo(ChronoUnit.MILLIS).minusSeconds(100)
      insert(
        rsChange.id,
        RecordChangeMessageType.value,
        true,
        rsChangeBytes,
        initialTs,
        initialTs,
        30,
        1
      )
      val msgs = underTest.receive(MessageCount(1).right.value).unsafeRunSync()
      (msgs should have).length(1)
    }
  }

  "changeMessageTimeout" should {
    "update the message time out" in {
      underTest.send(rsChange).unsafeRunSync()
      underTest.changeMessageTimeout(testMessage, 100.seconds).unsafeRunSync()
      val r = underTest.receive(MessageCount(1).right.value).unsafeRunSync()
      r.headOption.map(_.asInstanceOf[MySqlMessage].timeout) shouldBe Some(100.seconds)
    }
    "re-deliver the message after updating the timeout" in {
      // send and receive the message, receive a second time to ensure the message is delivered again
      underTest.send(rsChange).unsafeRunSync()
      val r = underTest.receive(MessageCount(1).right.value).unsafeRunSync()

      // second receive should not re-deliver
      underTest.receive(MessageCount(1).right.value).unsafeRunSync() shouldBe empty

      underTest.changeMessageTimeout(testMessage, 1.seconds).unsafeRunSync()
      Thread.sleep(2000)
      val r2 = underTest.receive(MessageCount(1).right.value).unsafeRunSync()
      (r2 should have).length(1)
      r.headOption.map(_.id) shouldBe r2.headOption.map(_.id)
    }
    "do nothing if the message does not exist" in {
      val r = underTest.changeMessageTimeout(testMessage, 100.seconds).attempt.unsafeRunSync()
      r shouldBe right
    }
  }

  "remove" should {
    "remove the message from the queue" in {
      underTest.send(rsChange).unsafeRunSync()
      val r = underTest.receive(MessageCount(1).right.value).unsafeRunSync()
      val msg = r.headOption.getOrElse(fail)
      underTest.remove(msg).unsafeRunSync()
      underTest.receive(MessageCount(1).right.value).unsafeRunSync() shouldBe empty
    }
    "do nothing if the message does not exist" in {
      underTest.remove(testMessage).attempt.unsafeRunSync() shouldBe right
    }
  }

  "requeue" should {
    "reset the message in the database" in {
      val initialTs = Instant.now.truncatedTo(ChronoUnit.MILLIS).minusSeconds(20)
      insert(
        rsChange.id,
        RecordChangeMessageType.value,
        false,
        rsChangeBytes,
        initialTs,
        initialTs,
        100,
        0
      )
      val r = underTest.receive(MessageCount(1).right.value).unsafeRunSync()
      val msg = r.headOption.getOrElse(fail)
      underTest.requeue(msg).unsafeRunSync()

      val req = findMessage(msg.command.id).getOrElse(fail)
      req.inFlight shouldBe false
    }
    "do nothing if the message is not in the database" in {
      underTest.requeue(testMessage).attempt.unsafeRunSync() shouldBe right
    }
  }

  "healthCheck" should {
    "succeed if connections are working" in {
      val check = underTest.healthCheck().unsafeRunSync()
      check shouldBe right
    }
  }
}
