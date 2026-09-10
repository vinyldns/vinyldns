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
import cats.effect.{ContextShift, IO, Timer}
import cats.scalatest.EitherMatchers
import fs2._
import org.mockito
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.VinylDNSTestHelpers
import vinyldns.api.backend.CommandHandler.{DeleteMessage, RetryMessage}
import vinyldns.api.backend.dns.DnsBackend
import vinyldns.core.domain.batch.{BatchChange, BatchChangeCommand, BatchChangeRepository}
import vinyldns.core.domain.record.{
  RecordChangeRepository,
  RecordSetChange,
  RecordSetCacheRepository,
  RecordSetRepository
}
import vinyldns.core.domain.zone._
import vinyldns.core.queue.{CommandMessage, MessageCount, MessageId, MessageQueue}
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.backend.{Backend, BackendResolver}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import vinyldns.core.notifier.AllNotifiers

class CommandHandlerSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with VinylDNSTestHelpers
    with EitherValues
    with EitherMatchers {

  private case class TestCommandMessage(command: ZoneCommand, value: String)
      extends CommandMessage {
    def id: MessageId = MessageId(value)
  }

  private val mq = mock[MessageQueue]
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val messages = for { i <- 0 to 10 } yield TestCommandMessage(
    pendingCreateAAAA,
    i.toString
  )
  private val count = MessageCount(10).right.value

  private val mockZoneChangeProcessor = mock[ZoneChange => IO[ZoneChange]]
  private val mockRecordChangeProcessor =
    mock[(Backend, RecordSetChange) => IO[RecordSetChange]]
  private val mockZoneSyncProcessor = mock[ZoneChange => IO[ZoneChange]]
  private val mockBatchChangeProcessor = mock[BatchChangeCommand => IO[Option[BatchChange]]]
  private val mockBackendResolver = mock[BackendResolver]
  private val mockZoneRepo = mock[ZoneRepository]
  private val processor =
    CommandHandler.processChangeRequests(
      mockZoneChangeProcessor,
      mockRecordChangeProcessor,
      mockZoneSyncProcessor,
      mockBatchChangeProcessor,
      mockBackendResolver,
      mockZoneRepo
    )

  override protected def beforeEach(): Unit =
    Mockito.reset(mq, mockZoneChangeProcessor, mockRecordChangeProcessor, mockZoneSyncProcessor, mockZoneRepo, mockBackendResolver)

  "polling" should {
    "poll for messages" in {
      doReturn(IO.pure(messages.toList)).when(mq).receive(count)
      val run = CommandHandler.startPolling(mq, count, 50.millis).take(1)
      val result = run.compile.toVector.unsafeRunSync()
      val read = result.head.compile.toList.unsafeRunSync()
      read should contain theSameElementsAs messages
    }
    "continue polling for messages on failure" in {
      doReturn(IO.raiseError(new RuntimeException("fail")))
        .doReturn(IO.raiseError(new RuntimeException("fail")))
        .doReturn(IO.pure(messages.toList))
        .when(mq)
        .receive(count)

      val run = CommandHandler.startPolling(mq, count, 10.millis).take(1)
      val result = run.compile.toVector.unsafeRunSync()
      val read = result.head.compile.toList.unsafeRunSync()
      read should contain theSameElementsAs messages
    }
  }
  "change message visibility" should {
    "update the timeout for zone syncs" in {
      doReturn(IO.unit).when(mq).changeMessageTimeout(any[CommandMessage], any[FiniteDuration])
      val sync = zoneCreate.copy(changeType = ZoneChangeType.Sync)
      val msg = TestCommandMessage(sync, "foo")
      Stream
        .emit(msg)
        .covary[IO]
        .through(CommandHandler.changeVisibilityTimeoutWhenSyncing(mq))
        .compile
        .drain
        .unsafeRunSync()

      verify(mq).changeMessageTimeout(msg, 1.hour)
    }
    "update the timeout for zone creates" in {
      doReturn(IO.unit).when(mq).changeMessageTimeout(any[CommandMessage], any[FiniteDuration])
      val msg = TestCommandMessage(zoneCreate, "foo")
      Stream
        .emit(msg)
        .covary[IO]
        .through(CommandHandler.changeVisibilityTimeoutWhenSyncing(mq))
        .compile
        .drain
        .unsafeRunSync()

      verify(mq).changeMessageTimeout(msg, 1.hour)
    }
    "not update the timeout for zone deletes" in {
      val del = zoneCreate.copy(changeType = ZoneChangeType.Delete)
      val msg = TestCommandMessage(del, "foo")
      Stream
        .emit(msg)
        .covary[IO]
        .through(CommandHandler.changeVisibilityTimeoutWhenSyncing(mq))
        .compile
        .drain
        .unsafeRunSync()

      verifyZeroInteractions(mq)
    }
    "not update the timeout for record changes" in {
      val msg = TestCommandMessage(pendingCreateAAAA, "foo")
      Stream
        .emit(msg)
        .covary[IO]
        .through(CommandHandler.changeVisibilityTimeoutWhenSyncing(mq))
        .compile
        .drain
        .unsafeRunSync()

      verifyZeroInteractions(mq)
    }

    "not update the timeout for batch changes" in {
      val msg = TestCommandMessage(BatchChangeCommand("someId"), "foo")
      Stream
        .emit(msg)
        .covary[IO]
        .through(CommandHandler.changeVisibilityTimeoutWhenSyncing(mq))
        .compile
        .drain
        .unsafeRunSync()

      verifyZeroInteractions(mq)
    }
  }

  "determining outcome" should {
    "generate a retry for failures" in {
      val msg = TestCommandMessage(pendingCreateAAAA, "foo")
      val result =
        CommandHandler.outcomeOf(msg)(IO.raiseError(new RuntimeException("fail"))).unsafeRunSync()
      result shouldBe RetryMessage(msg)
    }
    "generate delete for successes" in {
      val msg = TestCommandMessage(pendingCreateAAAA, "foo")
      val result = CommandHandler.outcomeOf(msg)(IO.unit).unsafeRunSync()
      result shouldBe DeleteMessage(msg)
    }
  }

  "message sink" should {
    "retry messages" in {
      val msg = RetryMessage(TestCommandMessage(pendingCreateAAAA, "foo"))
      doReturn(IO.unit).when(mq).requeue(msg.message)
      Stream
        .emit(msg)
        .covary[IO]
        .through(CommandHandler.messageSink(mq))
        .compile
        .drain
        .unsafeRunSync()
      verify(mq).requeue(msg.message)
    }
    "remove messages" in {
      val msg = DeleteMessage(TestCommandMessage(pendingCreateAAAA, "foo"))
      doReturn(IO.unit).when(mq).remove(msg.message)
      Stream
        .emit(msg)
        .covary[IO]
        .through(CommandHandler.messageSink(mq))
        .compile
        .drain
        .unsafeRunSync()
      verify(mq).remove(msg.message)
    }
  }

  "processing change requests" should {
    "handle record changes" in {
      val change = TestCommandMessage(pendingCreateAAAA, "foo")
      doReturn(IO.pure(Some(pendingCreateAAAA.zone))).when(mockZoneRepo).getZone(pendingCreateAAAA.zone.id)
      doReturn(mock[Backend]).when(mockBackendResolver).resolve(any[Zone])
      doReturn(IO.pure(change))
        .when(mockRecordChangeProcessor)
        .apply(any[DnsBackend], any[RecordSetChange])
      Stream.emit(change).covary[IO].through(processor).compile.drain.unsafeRunSync()
      verify(mockRecordChangeProcessor).apply(any[DnsBackend], any[RecordSetChange])
      verifyZeroInteractions(mockZoneSyncProcessor)
      verifyZeroInteractions(mockZoneChangeProcessor)
    }
    "handle zone creates" in {
      val change = TestCommandMessage(zoneCreate, "foo")
      doReturn(IO.pure(zoneCreate))
        .doReturn(IO.pure(change))
        .when(mockZoneChangeProcessor)
        .apply(zoneCreate)
      doReturn(IO.pure(zoneCreate)).when(mockZoneSyncProcessor).apply(zoneCreate)
      Stream.emit(change).covary[IO].through(processor).compile.drain.unsafeRunSync()
      verify(mockZoneSyncProcessor).apply(zoneCreate)
      verifyZeroInteractions(mockRecordChangeProcessor)
    }
    "handle zone syncs" in {
      val sync = zoneCreate.copy(changeType = ZoneChangeType.Sync)
      val change = TestCommandMessage(sync, "foo")
      doReturn(IO.pure(Some(sync.zone))).when(mockZoneRepo).getZone(sync.zone.id)
      doReturn(IO.pure(sync)).doReturn(IO.pure(change)).when(mockZoneChangeProcessor).apply(sync)
      doReturn(IO.pure(sync)).when(mockZoneSyncProcessor).apply(sync)
      Stream.emit(change).covary[IO].through(processor).compile.drain.unsafeRunSync()
      verify(mockZoneSyncProcessor).apply(sync)
      verifyZeroInteractions(mockRecordChangeProcessor)
    }
    "handle zone deletes" in {
      val del = zoneCreate.copy(changeType = ZoneChangeType.Delete)
      val change = TestCommandMessage(del, "foo")
      doReturn(IO.pure(del)).doReturn(IO.pure(change)).when(mockZoneChangeProcessor).apply(del)
      Stream.emit(change).covary[IO].through(processor).compile.drain.unsafeRunSync()
      verify(mockZoneChangeProcessor).apply(del)
      verifyZeroInteractions(mockZoneSyncProcessor)
      verifyZeroInteractions(mockRecordChangeProcessor)
    }
    "handle batch changes" in {
      val batchChange = BatchChangeCommand("someId")
      val change = TestCommandMessage(batchChange, "foo")
      doReturn(IO.pure(batchChange))
        .doReturn(IO.pure(change))
        .when(mockBatchChangeProcessor)
        .apply(batchChange)
      Stream.emit(change).covary[IO].through(processor).compile.drain.unsafeRunSync()
      verify(mockBatchChangeProcessor).apply(batchChange)
      verifyZeroInteractions(mockZoneChangeProcessor)
      verifyZeroInteractions(mockZoneSyncProcessor)
      verifyZeroInteractions(mockRecordChangeProcessor)
    }

    "discard a RecordSetChange whose zone id is absent from the repository" in {
      val change = TestCommandMessage(pendingCreateAAAA, "foo")
      doReturn(IO.pure(None)).when(mockZoneRepo).getZone(pendingCreateAAAA.zone.id)

      val result = Stream.emit(change).covary[IO].through(processor).compile.toList.unsafeRunSync()

      result.head shouldBe DeleteMessage(change)
      verifyZeroInteractions(mockRecordChangeProcessor)
      verifyZeroInteractions(mockBackendResolver)
    }
    "use the DB zone for RecordSetChange backend resolution, not the message-supplied zone" in {
      // Zone configuration from database takes precedence for backend resolution
      val modifiedZone = pendingCreateAAAA.zone.copy(backendId = Some("modified-backend"))
      val msg = TestCommandMessage(pendingCreateAAAA.copy(zone = modifiedZone), "foo")
      val dbZone = pendingCreateAAAA.zone

      doReturn(IO.pure(Some(dbZone))).when(mockZoneRepo).getZone(modifiedZone.id)
      doReturn(mock[Backend]).when(mockBackendResolver).resolve(dbZone)
      doReturn(IO.pure(pendingCreateAAAA)).when(mockRecordChangeProcessor).apply(any[Backend], any[RecordSetChange])

      Stream.emit(msg).covary[IO].through(processor).compile.drain.unsafeRunSync()

      verify(mockBackendResolver).resolve(dbZone)
      verify(mockRecordChangeProcessor).apply(any[Backend], any[RecordSetChange])
    }
    "discard a zone sync whose zone id is absent from the repository" in {
      val sync = zoneCreate.copy(changeType = ZoneChangeType.Sync)
      val change = TestCommandMessage(sync, "foo")
      doReturn(IO.pure(None)).when(mockZoneRepo).getZone(sync.zone.id)

      val result = Stream.emit(change).covary[IO].through(processor).compile.toList.unsafeRunSync()

      result.head shouldBe DeleteMessage(change)
      verifyZeroInteractions(mockZoneSyncProcessor)
    }
    "use the DB zone for zone sync, not the message-supplied zone" in {
      // Zone configuration from database takes precedence for sync operations
      val modifiedZone = zoneCreate.zone.copy(backendId = Some("modified-backend"))
      val msg = TestCommandMessage(zoneCreate.copy(changeType = ZoneChangeType.Sync, zone = modifiedZone), "foo")
      val dbZone = zoneCreate.zone
      val syncWithDbZone = zoneCreate.copy(changeType = ZoneChangeType.Sync, zone = dbZone)

      doReturn(IO.pure(Some(dbZone))).when(mockZoneRepo).getZone(zoneCreate.zone.id)
      doReturn(IO.pure(syncWithDbZone)).when(mockZoneSyncProcessor).apply(syncWithDbZone)

      Stream.emit(msg).covary[IO].through(processor).compile.drain.unsafeRunSync()

      verify(mockZoneSyncProcessor).apply(syncWithDbZone)
      verifyZeroInteractions(mockRecordChangeProcessor)
    }
    "pass through zone creates without querying the repository" in {
      val change = TestCommandMessage(zoneCreate, "foo")
      doReturn(IO.pure(zoneCreate)).when(mockZoneSyncProcessor).apply(zoneCreate)

      Stream.emit(change).covary[IO].through(processor).compile.drain.unsafeRunSync()

      verifyZeroInteractions(mockZoneRepo)
      verify(mockZoneSyncProcessor).apply(zoneCreate)
    }
  }

  "main flow" should {
    "process successfully" in {
      val stop = fs2.concurrent.SignallingRef[IO, Boolean](false).unsafeRunSync()
      val cmd = TestCommandMessage(pendingCreateAAAA, "foo")

      // stage pulling from the message queue
      doReturn(IO.pure(List(cmd))).when(mq).receive(count)

      // stage our record change processing
      doReturn(IO.pure(cmd))
        .when(mockRecordChangeProcessor)
        .apply(any[Backend], any[RecordSetChange])

      doReturn(mock[Backend])
        .when(mockBackendResolver)
        .resolve(any[Zone])

      // stage removing from the queue
      doReturn(IO.unit).when(mq).remove(cmd)
      doReturn(IO.pure(Some(pendingCreateAAAA.zone))).when(mockZoneRepo).getZone(pendingCreateAAAA.zone.id)

      val flow =
        CommandHandler
          .mainFlow(
            mockZoneChangeProcessor,
            mockRecordChangeProcessor,
            mockZoneSyncProcessor,
            mockBatchChangeProcessor,
            mq,
            count,
            100.millis,
            stop,
            mockBackendResolver,
            mockZoneRepo,
            1
          )
          .take(1)

      // kick off processing of messages
      flow.compile.drain.unsafeRunSync()

      // verify our interactions
      verify(mq, atLeastOnce()).receive(count)
      verify(mockRecordChangeProcessor, atLeastOnce())
        .apply(any[DnsBackend], mockito.Matchers.eq(pendingCreateAAAA))
      verify(mq, atLeastOnce()).remove(cmd)
    }
    "continue processing on unexpected failure" in {
      val stop = fs2.concurrent.SignallingRef[IO, Boolean](false).unsafeRunSync()
      val cmd = TestCommandMessage(pendingCreateAAAA, "foo")

      // stage pulling from the message queue, return an error then our command
      doReturn(IO.raiseError(new RuntimeException("fail")))
        .doReturn(IO.pure(List(cmd)))
        .when(mq)
        .receive(count)

      // stage our record change processing our command
      doReturn(IO.pure(cmd.command))
        .when(mockRecordChangeProcessor)
        .apply(any[Backend], any[RecordSetChange])

      doReturn(mock[Backend])
        .when(mockBackendResolver)
        .resolve(any[Zone])

      // stage removing from the queue
      doReturn(IO.unit).when(mq).remove(cmd)
      doReturn(IO.pure(Some(pendingCreateAAAA.zone))).when(mockZoneRepo).getZone(pendingCreateAAAA.zone.id)

      val flow =
        CommandHandler
          .mainFlow(
            mockZoneChangeProcessor,
            mockRecordChangeProcessor,
            mockZoneSyncProcessor,
            mockBatchChangeProcessor,
            mq,
            count,
            100.millis,
            stop,
            mockBackendResolver,
            mockZoneRepo
          )
          .take(1)

      // kick off processing of messages
      flow.compile.drain.unsafeRunSync()

      // verify our interactions
      verify(mq, atLeastOnce()).receive(count)

      // verify that our message queue was polled twice
      verify(mq, times(2))
        .receive(count)
      verify(mq).remove(cmd)
    }
  }

  "run" should {
    "process a zone update change through the flow" in {
      // testing the run method, which does nothing more than simplify construction of the main flow
      val stop = fs2.concurrent.SignallingRef[IO, Boolean](false).unsafeRunSync()
      val cmd = TestCommandMessage(zoneUpdate, "foo")

      val zoneRepo = mock[ZoneRepository]
      val zoneChangeRepo = mock[ZoneChangeRepository]
      val recordSetRepo = mock[RecordSetRepository]
      val recordSetCacheRepo = mock[RecordSetCacheRepository]
      val recordChangeRepo = mock[RecordChangeRepository]
      val batchChangeRepo = mock[BatchChangeRepository]

      // stage pulling from the message queue, return the message in the first poll, stage a few additional
      // polls just in case
      doReturn(IO.pure(List(cmd)))
        .doReturn(IO.pure(List()))
        .doReturn(IO.pure(List()))
        .doReturn(IO.pure(List()))
        .when(mq)
        .receive(count)

      // stage processing for a zone update, the simplest of cases
      doReturn(mock[Backend])
        .when(mockBackendResolver)
        .resolve(any[Zone])
      doReturn(IO.pure(Some(zoneUpdate.zone))).when(zoneRepo).getZone(zoneUpdate.zone.id)
      doReturn(IO.pure(Right(zoneUpdate.zone))).when(zoneRepo).save(zoneUpdate.zone)
      doReturn(IO.pure(zoneUpdate)).when(zoneChangeRepo).save(any[ZoneChange])

      // stage removing from the queue
      doReturn(IO.unit).when(mq).remove(cmd)

      val flow =
        CommandHandler
          .run(
            mq,
            count,
            stop,
            100.millis,
            zoneRepo,
            zoneChangeRepo,
            recordSetRepo,
            recordChangeRepo,
            recordSetCacheRepo,
            batchChangeRepo,
            AllNotifiers(List.empty),
            mockBackendResolver,
            10000
          )

      // kick off processing of messages
      flow.unsafeRunAsync { _ =>
        ()
      }

      // sleep to allow the flow to run once
      // TODO: arbitrary sleep statements lead to non-deterministic tests - find a more reliable mechanism
      Thread.sleep(1000)

      // shutoff processing on the flow
      stop.set(true).unsafeRunSync()

      // verify our interactions
      verify(mq, atLeastOnce()).receive(count)
      verify(zoneRepo).save(zoneUpdate.zone)
      verify(zoneChangeRepo).save(any[ZoneChange])
      verify(mq).remove(cmd)
    }
  }
}
