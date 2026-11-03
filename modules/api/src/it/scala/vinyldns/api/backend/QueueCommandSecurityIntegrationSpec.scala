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

package vinyldns.api.backend

import java.util.UUID

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO, Timer}
import cats.effect.concurrent.Ref
import fs2.Stream
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import scalikejdbc._
import vinyldns.core.TestRecordSetData.pendingCreateAAAA
import vinyldns.core.TestZoneData.zoneChangePending
import vinyldns.core.domain.backend.{Backend, BackendResolver}
import vinyldns.core.domain.batch.BatchChangeCommand
import vinyldns.core.domain.zone._
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue.{CommandExecutionGuard, CommandMessage, MessageCount}
import vinyldns.mysql.queue.IsolatedQueueDatabase

import scala.concurrent.ExecutionContext

class QueueCommandSecurityIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with EitherValues
    with MockitoSugar
    with IsolatedQueueDatabase
    with ProtobufConversions {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  private val count = MessageCount(10).right.value

  private def processor(calls: Ref[IO, List[String]]) = {
    val zones = mock[ZoneRepository]
    doReturn(IO.pure(Some(zoneChangePending.zone))).when(zones).getZone(anyString())
    val resolver = mock[BackendResolver]
    doReturn(mock[Backend]).when(resolver).resolve(any[Zone])
    CommandHandler.processChangeRequests(
      change => calls.update(change.id :: _).as(change),
      (_, change) => calls.update(change.id :: _).as(change),
      change => calls.update(change.id :: _).as(change),
      change => calls.update(change.id :: _).as(None),
      resolver,
      zones,
      new CommandExecutionGuard(executions)
    )
  }

  private def commands(): List[ZoneCommand] =
    List(
      pendingCreateAAAA.copy(id = UUID.randomUUID().toString),
      BatchChangeCommand(UUID.randomUUID().toString)
    ) ++
      List(
        ZoneChangeType.Create,
        ZoneChangeType.Update,
        ZoneChangeType.Delete,
        ZoneChangeType.Sync,
        ZoneChangeType.AutomatedSync
      ).map(typ => zoneChangePending.copy(changeType = typ, id = UUID.randomUUID().toString))

  "Queue authentication at the worker boundary" should {
    "dispatch every authorized command once and suppress a completed replay after worker restart" in {
      val sent = commands()
      val calls = Ref.of[IO, List[String]](Nil).unsafeRunSync()
      queue.sendBatch(NonEmptyList.fromListUnsafe(sent)).unsafeRunSync()
      val received = queue.receive(count).unsafeRunSync()
      received.size shouldBe sent.size
      Stream
        .emits(received)
        .covary[IO]
        .through(processor(calls))
        .through(CommandHandler.messageSink(queue))
        .compile
        .drain
        .unsafeRunSync()
      calls.get.unsafeRunSync() should contain theSameElementsAs sent.map(_.id)

      queue.sendBatch(NonEmptyList.fromListUnsafe(sent)).unsafeRunSync()
      Stream
        .emits(queue.receive(count).unsafeRunSync())
        .covary[IO]
        .through(processor(calls))
        .through(CommandHandler.messageSink(queue))
        .compile
        .drain
        .unsafeRunSync()
      calls.get.unsafeRunSync() should contain theSameElementsAs sent.map(_.id)
    }

    "reject forged record, create, update and delete commands before business side effects" in {
      val sent = commands().filter {
        case _: BatchChangeCommand => false
        case _ => true
      }
      val calls = Ref.of[IO, List[String]](Nil).unsafeRunSync()
      queue.sendBatch(NonEmptyList.fromListUnsafe(sent)).unsafeRunSync()
      NamedDB(poolName).localTx { implicit session =>
        sent.foreach { command =>
          val unsigned = queue.getBytes(command).dropRight(32)
          sql"UPDATE message_queue SET data=$unsigned WHERE id=${command.id}".update().apply()
        }
      }
      Stream
        .emits(queue.receive(count).unsafeRunSync())
        .covary[IO]
        .through(processor(calls))
        .through(CommandHandler.messageSink(queue))
        .compile
        .drain
        .unsafeRunSync()
      calls.get.unsafeRunSync() shouldBe empty
    }

    "reject an unsigned custom-provider message before zone lookup or backend dispatch" in {
      val calls = Ref.of[IO, List[String]](Nil).unsafeRunSync()
      val message = new CommandMessage {
        val command: ZoneCommand = zoneChangePending
        val id = vinyldns.core.queue.MessageId("unsigned-provider-message")
      }
      val results =
        Stream.emit(message).covary[IO].through(processor(calls)).compile.toList.unsafeRunSync()
      results shouldBe List(CommandHandler.DeleteMessage(message))
      calls.get.unsafeRunSync() shouldBe empty
    }

    "suppress a redelivery when completion was persisted but transport acknowledgement was lost" in {
      val calls = Ref.of[IO, List[String]](Nil).unsafeRunSync()
      val command = pendingCreateAAAA.copy(id = UUID.randomUUID().toString)
      queue.send(command).unsafeRunSync()
      val message = queue.receive(count).unsafeRunSync().head
      Stream.emit(message).covary[IO].through(processor(calls)).compile.drain.unsafeRunSync()
      queue.requeue(message).unsafeRunSync()
      Stream
        .emits(queue.receive(count).unsafeRunSync())
        .covary[IO]
        .through(processor(calls))
        .through(CommandHandler.messageSink(queue))
        .compile
        .drain
        .unsafeRunSync()
      calls.get.unsafeRunSync() shouldBe List(command.id)
    }
  }
}
