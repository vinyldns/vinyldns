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

package vinyldns.sqs.queue

import java.util.concurrent.TimeUnit.SECONDS

import cats.data.NonEmptyList
import cats.scalatest.EitherMatchers
import com.amazonaws.services.sqs.model._
import org.scalatest.mockito.MockitoSugar
import org.scalatest._
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue.{MessageCount, MessageId}
import vinyldns.sqs.queue.SqsMessageQueue.InvalidMessageTimeout

import scala.concurrent.duration.FiniteDuration

class SqsMessageQueueIntegrationSpec extends WordSpec
  with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach with Matchers with EitherMatchers with EitherValues
  with ProtobufConversions {

  private val queue: SqsMessageQueue = SqsMessageQueue()

  // Re-create queue before tests
  override protected def beforeAll(): Unit = {
    queue.client.createQueue("sqs")
  }

  override protected def afterEach(): Unit = {
    // Remove items from queue after each test
    val result = queue.receive(MessageCount(200).right.value).unsafeRunSync()
    result.foreach(queue.remove)
  }

  // Delete queue after tests since stuff can potentially linger
  override protected def afterAll(): Unit = {
    queue.client.deleteQueue(queue.queueUrl)
  }

  val rsAddChange: RecordSetChange = makeTestAddChange(rsOk)

  "SqsMessageQueue" should {
    "receive a single message from the queue" in {
      queue.send(rsAddChange).unsafeRunSync()

      val result = queue.receive(MessageCount(1).right.value).unsafeRunSync()
      result.map(_.command) should contain theSameElementsAs List(rsAddChange)
    }

    "receive up to MessageCount messages from the queue" in {
      for (_ <- 1 to 6) {
        queue.send(rsAddChange).unsafeRunSync()
        queue.send(zoneChangePending).unsafeRunSync()
      }

      val result = queue.receive(MessageCount(4).right.value).unsafeRunSync()
      result.map(_.command) should contain theSameElementsAs List(rsAddChange, rsAddChange, zoneChangePending,
        zoneChangePending)
    }

    "drop malformed message from the queue" in {
      queue.sqsAsync[SendMessageRequest, SendMessageResult](
        new SendMessageRequest()
          .withMessageBody("malformed data")
          .withQueueUrl(queue.queueUrl),
          queue.client.sendMessageAsync)

      val result = queue.receive(MessageCount(1).right.value).unsafeRunSync()
      result shouldBe empty
    }

    "succeed when attempting to remove item from empty queue" in {
      queue.remove(SqsMessage(MessageId("does-not-exist"), makeTestAddChange(rsOk)))
        .attempt.unsafeRunSync() should beRight(())
    }

    "succeed when attempting to remove item from queue" in {
      queue.send(rsAddChange).unsafeRunSync()
      val result = queue.receive(MessageCount(1).right.value).unsafeRunSync()
      result.length shouldBe 1

      queue.remove(SqsMessage(MessageId(result(0).id.value), makeTestAddChange(rsOk)))
        .attempt.unsafeRunSync() should beRight(())
    }

    "succeed when attempting to requeue" in {
      val recordSetChange = makeTestAddChange(rsOk.copy(name = "requeue-attempt"))
      queue.sendBatch(NonEmptyList.fromListUnsafe(List(recordSetChange))).unsafeRunSync()

      val result = queue.receive(MessageCount(2).right.value).unsafeRunSync()
      result.map(_.command) should contain theSameElementsAs List(recordSetChange)

      queue.requeue(SqsMessage(MessageId(result(0).id.value), recordSetChange))
        .attempt.unsafeRunSync() should beRight(())

      val immediateReceive = queue.receive(MessageCount(2).right.value).unsafeRunSync()
      immediateReceive shouldBe Nil

      Thread.sleep(10000)

      val requeueResult = queue.receive(MessageCount(2).right.value).unsafeRunSync()
      requeueResult.map(_.command) shouldBe List(recordSetChange)
    }

    "send a single message request" in {
      queue.send(makeTestAddChange(rsOk)).attempt.unsafeRunSync() should beRight(())

      val result = queue.receive(MessageCount(1).right.value).unsafeRunSync()
      result should have length 1
      result(0).command shouldBe a[RecordSetChange]
    }

    "send a message batch request with successes" in {
      val recordSetChanges = for (_ <- 0 to 15) yield makeTestAddChange(aaaa, zoneActive)
      val zoneChanges = for (_ <- 0 to 15) yield makeTestPendingZoneChange(zoneActive)
      val commands = recordSetChanges.toList ++ zoneChanges.toList

      val messages = NonEmptyList.fromListUnsafe(commands)

      val result = queue.sendBatch(messages).unsafeRunSync()

      result.successes should contain theSameElementsAs commands
      result.failures shouldBe empty
    }

    "send a message batch request with more than 256KB successfully" in {
      val recordSet = aaaa.copy(name = "a"*100000, zoneId = "b"*10000)
      val recordSetChanges = for (_ <- 0 to 99) yield makeTestAddChange(recordSet, zoneActive)
      val commands = recordSetChanges.toList

      val messages = NonEmptyList.fromListUnsafe(commands)

      val result = queue.sendBatch(messages).unsafeRunSync()

      result.successes should contain theSameElementsAs commands
      result.failures shouldBe empty
    }

    "change message visibility timeout correctly" in {
      val recordSetChange = makeTestAddChange(rsOk)
      queue.send(recordSetChange).unsafeRunSync()

      val result = queue.receive(MessageCount(2).right.value).unsafeRunSync()
      result.map(_.command) shouldBe List(recordSetChange)

      // Set next visibility of timeout for message
      queue.changeMessageTimeout(SqsMessage(MessageId(result(0).id.value), recordSetChange),
        FiniteDuration(0, SECONDS)).attempt.unsafeRunSync() should beRight(())

      // Test that we can immediately receive message after timeout adjustment
      val adjustedTimeoutResult = queue.receive(MessageCount(2).right.value).unsafeRunSync()
      adjustedTimeoutResult.map(_.command) shouldBe List(recordSetChange)

      // Once received, visibility timeout gets reset to the default value (of 30s)
      val defaultTimeoutResult = queue.receive(MessageCount(2).right.value).unsafeRunSync()
      defaultTimeoutResult.map(_.command) shouldBe Nil
    }

    "throw an InvalidMessageTimeout when attempting to set invalid visibility timeout" in {
      assertThrows[InvalidMessageTimeout] {
        queue.changeMessageTimeout(
          SqsMessage(MessageId("does-not-matter"), pendingCreateAAAA), new FiniteDuration(43201, SECONDS))
          .unsafeRunSync()
      }
    }

    "throw an error if there are issues parsing the message" in {
      val message = new Message()

      val queue = SqsMessageQueue()
      assertThrows[AmazonSQSException] {
        queue.parse(message).unsafeRunSync()
      }
    }
  }
}
