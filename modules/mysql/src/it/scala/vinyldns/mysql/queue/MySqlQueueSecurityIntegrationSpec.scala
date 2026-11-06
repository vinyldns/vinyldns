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

import java.util.UUID

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import cats.implicits._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalikejdbc._
import vinyldns.core.TestRecordSetData.pendingCreateAAAA
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue.{CommandIdentity, MessageCount}
import vinyldns.core.queue.CommandExecutionRepository._
import vinyldns.mysql.repository.MySqlCommandExecutionRepository

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class MySqlQueueSecurityIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with EitherValues
    with IsolatedQueueDatabase
    with ProtobufConversions {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private val count = MessageCount(10).right.value

  private def newIdentity(): CommandIdentity = {
    val id = UUID.randomUUID().toString.replace("-", "")
    CommandIdentity(id, id + id, "a" * 64)
  }

  "Authenticated MySQL delivery" should {
    "drop multiple forged rows while delivering an unrelated valid command" in {
      val forged = (1 to 2).map(_ => pendingCreateAAAA.copy(id = UUID.randomUUID().toString)).toList
      val valid = pendingCreateAAAA.copy(id = UUID.randomUUID().toString)
      queue.sendBatch(NonEmptyList.fromListUnsafe(forged :+ valid)).unsafeRunSync()
      NamedDB(poolName).localTx { implicit session =>
        forged.foreach { command =>
          sql"UPDATE message_queue SET data=${toPB(command).toByteArray} WHERE id=${command.id}"
            .update()
            .apply()
        }
      }
      val received = queue.receive(count).unsafeRunSync()
      received.map(_.command) shouldBe List(valid)
      NamedDB(poolName).readOnly { implicit session =>
        sql"SELECT COUNT(*) FROM message_queue WHERE id IN (${forged.map(_.id)})"
          .map(_.int(1))
          .single()
          .apply()
          .get shouldBe 0
      }
      queue.remove(received.head).unsafeRunSync()
    }

    "retain signature identity across transport retries and block replay after completion" in {
      val command = pendingCreateAAAA.copy(id = UUID.randomUUID().toString)
      queue.send(command).unsafeRunSync()
      val first = queue.receive(count).unsafeRunSync().head
      val identity = first.authenticatedIdentity.get
      executions.claim(identity, "first", 1.minute).unsafeRunSync() shouldBe Acquired
      executions.release(identity, "first").unsafeRunSync()
      queue.requeue(first).unsafeRunSync()
      val retry = queue.receive(count).unsafeRunSync().head
      retry.authenticatedIdentity shouldBe first.authenticatedIdentity
      executions.claim(identity, "second", 1.minute).unsafeRunSync() shouldBe Acquired
      executions.complete(identity, "second").unsafeRunSync()
      queue.remove(retry).unsafeRunSync()
      queue.send(command).unsafeRunSync()
      val replay = queue.receive(count).unsafeRunSync().head
      new MySqlCommandExecutionRepository(poolName)
        .claim(replay.authenticatedIdentity.get, "restarted", 1.minute)
        .unsafeRunSync() shouldBe Completed
      queue.remove(replay).unsafeRunSync()
    }
  }

  "Durable execution claims" should {
    "allow exactly one concurrent owner and reject a changed payload for the same ID" in {
      val identity = newIdentity()
      val results = List(
        executions.claim(identity, "one", 1.minute),
        executions.claim(identity, "two", 1.minute)
      ).parSequence.unsafeRunSync()
      results should contain theSameElementsAs List(Acquired, Busy)
      executions
        .claim(identity.copy(digest = "b" * 64), "other", 1.minute)
        .unsafeRunSync() shouldBe Conflict
    }

    "recover an expired lease and fence out the previous owner" in {
      val identity = newIdentity()
      executions.claim(identity, "old", 1.minute).unsafeRunSync() shouldBe Acquired
      NamedDB(poolName).localTx { implicit session =>
        sql"""UPDATE queue_command_execution SET lease_until=TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(3))
              WHERE command_key=${identity.key}""".update().apply()
      }
      executions.claim(identity, "new", 1.minute).unsafeRunSync() shouldBe Acquired
      executions.complete(identity, "old").attempt.unsafeRunSync() shouldBe Left(
        LostLease(identity.commandId)
      )
      executions.release(identity, "old").unsafeRunSync()
      executions.renew(identity, "new", 1.minute).unsafeRunSync()
      executions.complete(identity, "new").unsafeRunSync()
      executions.claim(identity, "replay", 1.minute).unsafeRunSync() shouldBe Completed
    }
  }
}
