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

package vinyldns.mysql.repository

import cats.data._
import cats.effect._
import cats.implicits._
import cats.scalatest.EitherMatchers
import org.joda.time.DateTime
import org.scalatest._
import scalikejdbc._
import vinyldns.core.domain.record._
import vinyldns.core.domain.zone._
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue.{CommandMessage, MessageCount}
import vinyldns.mysql.queue.MessageType.{InvalidMessageType, RecordChangeMessageType, ZoneChangeMessageType}
import vinyldns.mysql.queue.MySQLMessageQueue.{InvalidMessageTimeout, MessageAttemptsExceeded, MessageId}
import vinyldns.mysql.queue.{MySQLMessage, MySQLMessageQueue}

import scala.concurrent.duration._

final case class RowData(
  id: String,
  messageType: Int,
  inFlight: Boolean,
  createdTime: DateTime,
  updatedTime: DateTime,
  timeoutSecs: Int,
  attempts: Int)

final case class InvalidMessage(command: ZoneCommand) extends CommandMessage

class MySQLMessageQueueIntegrationSpec extends WordSpec with Matchers
  with BeforeAndAfterEach with EitherMatchers with EitherValues with BeforeAndAfterAll with ProtobufConversions {

  private val underTest = new MySQLMessageQueue()

  private val okZone: Zone = Zone("ok.zone.recordsets.", "test@test.com", adminGroupId = "group")

  private val recordSet: RecordSet = RecordSet(
    okZone.id,
    "aaaa",
    RecordType.AAAA,
    200,
    RecordSetStatus.Pending,
    DateTime.now,
    None,
    List(AAAAData("1:2:3:4:5:6:7:8")))

  private val rsChange: RecordSetChange = RecordSetChange(okZone, recordSet, "user", RecordSetChangeType.Create)

  private val rsChangeBytes = toPB(rsChange).toByteArray

  private val testMessage: MySQLMessage = MySQLMessage(MessageId(rsChange.id), 0, 20.seconds, rsChange)

  private val zoneChange: ZoneChange = ZoneChange(
    okZone,
    "ok",
    ZoneChangeType.Create,
    ZoneChangeStatus.Complete,
    created = DateTime.now.minus(1000))

  private def clear(): Unit = DB.localTx { implicit s =>
    sql"DELETE FROM message_queue".update().apply()
    ()
  }
  override protected def beforeEach(): Unit = clear()

  override protected def beforeAll(): Unit = {
    // load the data store
    TestMySqlInstance.instance
    ()
  }

  private def insert(id: String, messageType: Int, inFlight: Boolean,
                     data: Array[Byte], created: DateTime, updated: DateTime,
                     timeoutSeconds: Int, attempts: Int): Unit = {
    DB.localTx { implicit s =>
      val inF = if (inFlight) 1 else 0
      val insertSql = sql"""
         |INSERT INTO message_queue(id, message_type, in_flight, created, updated, timeout_seconds, attempts, data)
         |     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """.stripMargin
      insertSql.bind(
          id,
          messageType,
          inF,
          created,
          updated,
          timeoutSeconds,
          attempts,
          data)
        .update()
        .apply()
    }
    ()
  }

  private def findMessage(id: String): Option[RowData] =
    DB.readOnly { implicit s =>
      val findSql =
        sql"""
          |SELECT id, message_type, in_flight, created, updated, timeout_seconds, attempts
          |  FROM message_queue
          | WhERE id = ?
        """.stripMargin

      findSql.bind(id).map { rs =>
        RowData(
          rs.string(1),
          rs.int(2),
          rs.boolean(3),
          new DateTime(rs.timestamp(4).getTime),
          new DateTime(rs.timestamp(5).getTime),
          rs.int(6),
          rs.int(7)
        )
      }.toOption().apply()
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
      val s = underTest.send(NonEmptyList.of(first, second)).unsafeRunSync()
      s.successes should contain theSameElementsAs List(first, second)

      val r = underTest.receive(MessageCount(2).right.value).unsafeRunSync()
      r.map(_.command) should contain theSameElementsAs List(first, second)
    }
    "be idempotent" in {
      // Put the same change in twice, get one out
      underTest.send(NonEmptyList.of(rsChange)).unsafeRunSync()
      underTest.send(NonEmptyList.of(rsChange)).unsafeRunSync()
      val r = underTest.receive(MessageCount(8).right.value).unsafeRunSync()
      r should have length 1
      r.headOption.map(_.command) shouldBe Some(rsChange)
    }
    "be idempotent for a batch" in {
      // Send a batch
      val first = rsChange
      val second = rsChange.copy(id = "second")
      underTest.send(NonEmptyList.of(first, second)).unsafeRunSync()

      // Send another batch, with two new and two old
      val third = rsChange.copy(id = "third")
      val fourth = rsChange.copy(id = "fourth")
      val r = underTest.send(NonEmptyList.of(first, third, second, fourth)).unsafeRunSync()
      r.successes should contain theSameElementsAs List(first, third, second, fourth)

      // Receive a batch, make sure we only get 4 out, no duplicates
      val batch = underTest.receive(MessageCount(10).right.value).unsafeRunSync()
      batch should have length 4
      batch.map(_.command) should contain theSameElementsAs List(first, second, third, fourth)
    }
    "work in parallel" in {
      // send 20 messages in parallel
      val changes = for { i <- 0 to 8 } yield rsChange.copy(id = s"chg$i")
      val sends = changes.map(rc => underTest.send(rc))

      // receive 20 batches of 1 in parallel
      val rcvs = for { _ <- 0 to 8 } yield underTest.receive(MessageCount(1).right.value)

      // let's fire them both off, doesn't matter who finishes, as long as the IO does not fail
      val result = IO.race(sends.toList.parSequence, rcvs.toList.parSequence).attempt.unsafeRunSync()
      result shouldBe right
    }
  }

  "parseMessage" should {
    "fail on invalid message type" in {
      val result = underTest.parseMessage(MessageId("foo"), -2, rsChangeBytes, 1, 10)
      result.left.value shouldBe (InvalidMessageType(-2), MessageId("foo"))
    }
    "fail on invalid bytes" in {
      val result = underTest.parseMessage(MessageId("foo"), ZoneChangeMessageType.value, "bar".getBytes, 1, 10)
      val (err, id) = result.left.value
      id shouldBe MessageId("foo")
      err shouldBe an[Exception]
    }
    "fail if attempts exceeds 100" in {
      val result = underTest.parseMessage(MessageId("foo"), RecordChangeMessageType.value, rsChangeBytes, 200, 10)
      result.left.value shouldBe (MessageAttemptsExceeded("foo"), MessageId("foo"))
    }
    "fail on invalid timeout" in {
      val result = underTest.parseMessage(MessageId("foo"), RecordChangeMessageType.value, rsChangeBytes, 1, -1)
      result.left.value shouldBe (InvalidMessageTimeout(-1), MessageId("foo"))
    }
  }

  "receive" should {
    "drop messages that have an invalid message type" in {
      insert("foo", 23, false, rsChangeBytes, DateTime.now, DateTime.now, 100, 0)
      underTest.receive(MessageCount(1).right.value).unsafeRunSync()

      findMessage("foo") should not be defined
    }
    "drop messages that have invalid message data" in {
      insert(rsChange.id, RecordChangeMessageType.value, false, "blah".getBytes, DateTime.now, DateTime.now, 100, 0)
      underTest.receive(MessageCount(1).right.value).unsafeRunSync()

      findMessage(rsChange.id) should not be defined
    }
    "drop messages that have expired" in {
      insert(rsChange.id, RecordChangeMessageType.value, false, "blah".getBytes, DateTime.now, DateTime.now, 100, 101)
      underTest.receive(MessageCount(1).right.value).unsafeRunSync()

      findMessage(rsChange.id) should not be defined
    }
    "increment the attempt, timestamp, and in flight status" in {
      val initialAttempts = 0
      val initialTs = DateTime.now.minusSeconds(20)
      insert(rsChange.id, RecordChangeMessageType.value, false, rsChangeBytes, initialTs, initialTs, 100, initialAttempts)

      val oldMsg = findMessage(rsChange.id).getOrElse(fail)
      underTest.receive(MessageCount(1).right.value).unsafeRunSync()

      val msg = findMessage(rsChange.id).getOrElse(fail)
      msg.attempts shouldBe oldMsg.attempts + 1
      msg.updatedTime.getMillis should be > oldMsg.updatedTime.getMillis
      msg.inFlight shouldBe true
    }
  }

  "changeMessageTimeout" should {
    "update the message time out" in {
      underTest.send(rsChange).unsafeRunSync()
      underTest.changeMessageTimeout(testMessage, 100.seconds).unsafeRunSync()
      val r = underTest.receive(MessageCount(1).right.value).unsafeRunSync()
      r.headOption.map(_.asInstanceOf[MySQLMessage].timeout) shouldBe Some(100.seconds)
    }
    "do nothing if the message does not exist" in {
      val r = underTest.changeMessageTimeout(testMessage, 100.seconds).attempt.unsafeRunSync()
      r shouldBe right
    }
    "fail if the message provided is not a MySQLMessage" in {
      underTest.changeMessageTimeout(InvalidMessage(rsChange), 100.seconds).attempt.unsafeRunSync() shouldBe left
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
    "fail if the message provided is not a MySQLMessage" in {
      underTest.remove(InvalidMessage(rsChange)).attempt.unsafeRunSync() shouldBe left
    }
  }

  "requeue" should {
    "reset the message in the database" in {
      val initialTs = DateTime.now.minusSeconds(20)
      insert(rsChange.id, RecordChangeMessageType.value, false, rsChangeBytes, initialTs, initialTs, 100, 0)
      val raw = findMessage(rsChange.id)
      val rawMsg = raw.getOrElse(fail)
      val oldUpdated = rawMsg.updatedTime

      val r = underTest.receive(MessageCount(1).right.value).unsafeRunSync()
      val msg = r.headOption.getOrElse(fail)
      underTest.requeue(msg).unsafeRunSync()

      val req = findMessage(msg.command.id).getOrElse(fail)
      req.inFlight shouldBe false
      req.updatedTime.getMillis should be > oldUpdated.getMillis
    }
    "do nothing if the message is not in the database" in {
      underTest.requeue(testMessage).attempt.unsafeRunSync() shouldBe right
    }
    "fail if the message provided is not a MySQLMessage" in {
      underTest.requeue(InvalidMessage(rsChange)).attempt.unsafeRunSync() shouldBe left
    }
  }
}
