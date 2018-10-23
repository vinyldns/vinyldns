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

import java.util.Base64

import cats.effect.{ContextShift, IO, Timer}
import com.amazonaws.services.sqs.model._
import org.mockito.Matchers.{eq => sameas, _}
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, OneInstancePerTest, WordSpec}
import vinyldns.api.VinylDNSTestData
import vinyldns.api.domain.dns.DnsConnection
import vinyldns.api.engine.sqs.SqsConnection
import vinyldns.api.engine.sqs.SqsConverters.{SqsRecordSetChangeMessage, SqsZoneChangeMessage}
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.{ZoneChange, ZoneChangeType}
import vinyldns.core.protobuf.ProtobufConversions

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ZoneCommandHandlerSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with ProtobufConversions
    with VinylDNSTestData
    with OneInstancePerTest {

  import ZoneCommandHandler._

  private val mockRmr = mock[ReceiveMessageResult]
  private val mockSqs = mock[SqsConnection]
  private val mockMsg = mock[Message]
  private val zcp = mock[ZoneChange => IO[ZoneChange]]
  private val rcp = mock[(DnsConnection, RecordSetChange) => IO[RecordSetChange]]
  private val zsp = mock[ZoneChange => IO[ZoneChange]]

  private def rmrIO = IO.pure(mockRmr)

  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  override def beforeEach(): Unit = {
    reset(mockRmr, mockSqs, mockMsg, zcp, rcp, zsp)

    doReturn(rmrIO)
      .doReturn(rmrIO)
      .doReturn(rmrIO)
      .doReturn(rmrIO)
      .doReturn(rmrIO)
      .doReturn(rmrIO)
      .doReturn(rmrIO)
      .when(mockSqs)
      .receiveMessageBatch(any[ReceiveMessageRequest])
  }

  "pollingStream" should {
    "poll for ReceiveMessageResults" in {
      val stop = fs2.concurrent.SignallingRef[IO, Boolean](false).unsafeRunSync()
      val test = startPolling(mockSqs, 80.millis).interruptWhen(stop)

      test.compile.drain.unsafeToFuture()

      Thread.sleep(250)
      stop.set(true).unsafeRunSync()

      verify(mockSqs, atLeastOnce()).receiveMessageBatch(any[ReceiveMessageRequest])

    }

    "continue polling even on error" in {
      val fetchFail = mock[SqsConnection]
      doReturn(IO.raiseError(new RuntimeException("fail")))
        .when(fetchFail)
        .receiveMessageBatch(any[ReceiveMessageRequest])

      val stop = fs2.concurrent.SignallingRef[IO, Boolean](false).unsafeRunSync()
      val test = startPolling(fetchFail, 80.millis).interruptWhen(stop)

      test.compile.drain.unsafeToFuture()

      Thread.sleep(400)
      stop.set(true).unsafeRunSync()

      verify(fetchFail, Mockito.atLeast(2)).receiveMessageBatch(any[ReceiveMessageRequest])
    }
  }

  "changeVisibilityTimeoutForZoneSyncs" should {
    "change message visibility for zone sync" in {
      val sqs = mock[SqsConnection]
      val res = mock[ChangeMessageVisibilityResult]

      doReturn("1").when(mockMsg).getReceiptHandle
      doReturn(IO.pure(res)).when(sqs).changeMessageVisibility(any[ChangeMessageVisibilityRequest])

      val sync = zoneChangePending.copy(changeType = ZoneChangeType.Sync)

      val underTest = changeVisibilityTimeoutForZoneSyncs(sqs)

      fs2.Stream
        .eval(IO.pure(ZoneSyncRequest(sync, mockMsg)))
        .to(underTest)
        .compile
        .drain
        .unsafeRunSync()

      val captor = ArgumentCaptor.forClass(classOf[ChangeMessageVisibilityRequest])
      verify(sqs).changeMessageVisibility(captor.capture())

      val req = captor.getValue
      req.getReceiptHandle shouldBe "1"
      req.getVisibilityTimeout shouldBe 1600
    }

    "ignore zone create" in {
      val sqs = mock[SqsConnection]

      val sync = zoneChangePending.copy(changeType = ZoneChangeType.Create)

      val underTest = changeVisibilityTimeoutForZoneSyncs(sqs)

      fs2.Stream
        .eval(IO.pure(ZoneChangeRequest(sync, mockMsg)))
        .to(underTest)
        .compile
        .drain
        .unsafeRunSync()

      verifyZeroInteractions(sqs)
    }

    "ignore zone update" in {
      val sqs = mock[SqsConnection]

      val sync = zoneChangePending.copy(changeType = ZoneChangeType.Update)

      val underTest = changeVisibilityTimeoutForZoneSyncs(sqs)

      fs2.Stream
        .eval(IO.pure(ZoneChangeRequest(sync, mockMsg)))
        .to(underTest)
        .compile
        .drain
        .unsafeRunSync()

      verifyZeroInteractions(sqs)
    }

    "ignore zone delete" in {
      val sqs = mock[SqsConnection]

      val sync = zoneChangePending.copy(changeType = ZoneChangeType.Delete)

      val underTest = changeVisibilityTimeoutForZoneSyncs(sqs)

      fs2.Stream
        .eval(IO.pure(ZoneChangeRequest(sync, mockMsg)))
        .to(underTest)
        .compile
        .drain
        .unsafeRunSync()

      verifyZeroInteractions(sqs)
    }

    "ignore record changes" in {
      val sqs = mock[SqsConnection]

      val underTest = changeVisibilityTimeoutForZoneSyncs(sqs)

      fs2.Stream
        .eval(IO.pure(RecordChangeRequest(pendingCreateAAAA, mockMsg)))
        .to(underTest)
        .compile
        .drain
        .unsafeRunSync()

      verifyZeroInteractions(sqs)
    }
  }

  "genMessageStreams" should {
    "emit the messages in a ReceiveMessageResult" in {
      val msg1 = mock[Message]
      val msg2 = mock[Message]

      doReturn(List(msg1, msg2).asJava).when(mockRmr).getMessages

      val test = fs2.Stream.eval(IO.pure(mockRmr)).through(genMessageStreams).parJoin(1)
      val result = test.compile.toVector.unsafeRunSync()

      result should contain theSameElementsAs List(msg1, msg2)
    }
  }

  "genChangeRequests" should {
    "convert messages to RecordChangeRequests" in {
      val bytes = toPB(pendingCreateAAAA).toByteArray
      val messageBody = Base64.getEncoder.encodeToString(bytes)
      val msg = new Message()
        .withBody(messageBody)
        .withMessageAttributes(
          Map(
            "message-type" -> new MessageAttributeValue()
              .withStringValue(SqsRecordSetChangeMessage.name)
              .withDataType("String")
          ).asJava)

      val res = fs2.Stream
        .eval[IO, Message](IO.pure(msg))
        .through(genChangeRequests)
        .compile
        .toVector
        .unsafeRunSync()
        .head

      res shouldBe a[RecordChangeRequest]
      val result = res.asInstanceOf[RecordChangeRequest]

      result.recordSetChange shouldBe pendingCreateAAAA
      result.message shouldBe msg
    }

    "convert messages to ZoneSyncRequests" in {
      val bytes = toPB(zoneCreate).toByteArray
      val messageBody = Base64.getEncoder.encodeToString(bytes)
      val msg = new Message()
        .withBody(messageBody)
        .withMessageAttributes(
          Map(
            "message-type" -> new MessageAttributeValue()
              .withStringValue(SqsZoneChangeMessage.name)
              .withDataType("String")
          ).asJava)

      val res = fs2.Stream
        .eval[IO, Message](IO.pure(msg))
        .through(genChangeRequests)
        .compile
        .toVector
        .unsafeRunSync()
        .head

      res shouldBe a[ZoneSyncRequest]
      val result = res.asInstanceOf[ZoneSyncRequest]

      result.zoneChange shouldBe zoneCreate
      result.message shouldBe msg
    }

    "convert messages to ZoneChangeRequests" in {
      val bytes = toPB(zoneUpdate).toByteArray
      val messageBody = Base64.getEncoder.encodeToString(bytes)
      val msg = new Message()
        .withBody(messageBody)
        .withMessageAttributes(
          Map(
            "message-type" -> new MessageAttributeValue()
              .withStringValue(SqsZoneChangeMessage.name)
              .withDataType("String")
          ).asJava)

      val res = fs2.Stream
        .eval[IO, Message](IO.pure(msg))
        .through(genChangeRequests)
        .compile
        .toVector
        .unsafeRunSync()
        .head

      res shouldBe a[ZoneChangeRequest]
      val result = res.asInstanceOf[ZoneChangeRequest]

      result.zoneChange shouldBe zoneUpdate
      result.message shouldBe msg
    }
  }

  "processChangeRequests" should {
    "handle zone syncs" in {
      val zoneSyncChange = zoneChangePending.copy(changeType = ZoneChangeType.Sync)
      doReturn(IO.pure(zoneSyncChange)).when(zsp).apply(any[ZoneChange])
      doReturn(IO.pure(zoneSyncChange)).when(zcp).apply(any[ZoneChange])

      val underTest = processChangeRequests(zcp, rcp, zsp)

      val result = fs2.Stream
        .eval[IO, ChangeRequest](IO.pure(ZoneSyncRequest(zoneSyncChange, mockMsg)))
        .through(underTest)
        .compile
        .toVector
        .unsafeRunSync()
        .head

      result shouldBe a[DeleteMessage]
      result.message shouldBe mockMsg

      verify(zsp).apply(zoneSyncChange)
      verify(zcp, times(2)).apply(any[ZoneChange])
    }

    "handle record changes" in {
      doReturn(IO.pure(pendingCreateAAAA))
        .when(rcp)
        .apply(any[DnsConnection], sameas(pendingCreateAAAA))

      val underTest = processChangeRequests(zcp, rcp, zsp)
      val result = fs2.Stream
        .eval[IO, ChangeRequest](IO.pure(RecordChangeRequest(pendingCreateAAAA, mockMsg)))
        .through(underTest)
        .compile
        .toVector
        .unsafeRunSync()
        .head

      result shouldBe a[DeleteMessage]
      result.message shouldBe mockMsg

      verify(rcp).apply(any[DnsConnection], sameas(pendingCreateAAAA))
    }

    "handle zone changes" in {
      doReturn(IO.pure(zoneChangePending)).when(zcp).apply(zoneChangePending)

      val underTest = processChangeRequests(zcp, rcp, zsp)

      val result = fs2.Stream
        .eval[IO, ChangeRequest](IO.pure(ZoneChangeRequest(zoneChangePending, mockMsg)))
        .through(underTest)
        .compile
        .toVector
        .unsafeRunSync()
        .head

      result shouldBe a[DeleteMessage]
      result.message shouldBe mockMsg

      verify(zcp).apply(zoneChangePending)
    }

    "result in a retry message on failure" in {
      doReturn(IO.raiseError(new RuntimeException("fail"))).when(zcp).apply(zoneChangePending)

      val underTest = processChangeRequests(zcp, rcp, zsp)

      val result = fs2.Stream
        .eval[IO, ChangeRequest](IO.pure(ZoneChangeRequest(zoneChangePending, mockMsg)))
        .through(underTest)
        .compile
        .toVector
        .unsafeRunSync()
        .head

      result shouldBe a[RetryMessage]
      result.message shouldBe mockMsg

      verify(zcp).apply(zoneChangePending)
    }
  }

  "messageSink" should {
    "call deleteMessage" in {
      val sqs = mock[SqsConnection]
      val dmr = mock[DeleteMessageResult]

      doReturn("1").when(mockMsg).getReceiptHandle
      doReturn(IO.pure(dmr)).when(sqs).deleteMessage(any[DeleteMessageRequest])

      val underTest = messageSink(sqs)

      fs2.Stream.eval(IO.pure(DeleteMessage(mockMsg))).to(underTest).compile.drain.unsafeRunSync()

      val captor = ArgumentCaptor.forClass(classOf[DeleteMessageRequest])
      verify(sqs).deleteMessage(captor.capture())

      val dm = captor.getValue
      dm.getReceiptHandle shouldBe "1"
    }

    "skip on retry" in {
      val sqs = mock[SqsConnection]

      doReturn("1").when(mockMsg).getReceiptHandle

      val underTest = messageSink(sqs)

      fs2.Stream.eval(IO.pure(RetryMessage(mockMsg))).to(underTest).compile.drain.unsafeRunSync()

      verifyZeroInteractions(sqs)
    }
  }

}
