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
import org.scalatest.mockito.MockitoSugar
import org.scalatest._
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.record.RecordSetChange
import vinyldns.core.domain.zone.ZoneCommand
import vinyldns.core.queue.{CommandMessage, MessageCount}

import scala.concurrent.duration.FiniteDuration

class SqsMessageQueueSpec extends WordSpec
  with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach with Matchers with EitherValues {
  private val queue: SqsMessageQueue = SqsMessageQueue()

  // Re-create queue before tests
  override protected def beforeAll(): Unit = {
    queue.client.createQueue("sqs")
  }

  override protected def afterEach(): Unit = {
    // Remove items from queue after each test
    val result = queue.receive(MessageCount(10).right.value).unsafeRunSync()
    result.foreach(queue.remove)
  }

  // Delete queue after tests since stuff can potentially linger
  override protected def afterAll(): Unit = {
    queue.client.deleteQueue(queue.queueUrl)
  }

  val rsAddChange: RecordSetChange = makeTestAddChange(rsOk)

  "SqsMessageQueueSpec" should {
    "receive a single message from the queue" in {
      queue.send(rsAddChange).unsafeRunSync()

      val result = queue.receive(MessageCount(1).right.value).unsafeRunSync()
      result.length shouldBe 1
      result.head.command shouldBe a[RecordSetChange]
    }

    "receive up to MessageCount messages from the queue" in {
      for (_ <- 1 to 6) {
        queue.send(rsAddChange).unsafeRunSync()
        queue.send(zoneChangePending).unsafeRunSync()
      }

      val result = queue.receive(MessageCount(4).right.value).unsafeRunSync()
      result.length shouldBe 4
      result.foreach(_.command shouldBe a[ZoneCommand])
    }

    "return Unit when attempting to remove item from empty queue" in {
      val result = queue.receive(MessageCount(1).right.value).unsafeRunSync()
      result.length shouldBe 0

      noException should be thrownBy
        queue.remove(CommandMessage(ReceiptHandle("does-not-exist"), makeTestAddChange(rsOk))).unsafeRunSync()
    }

    "return Unit when attempting to remove item from queue" in {
      queue.send(rsAddChange).unsafeRunSync()
      val result = queue.receive(MessageCount(1).right.value).unsafeRunSync()
      result.length shouldBe 1

      noException should be thrownBy
        queue.remove(CommandMessage(result(0).handle, makeTestAddChange(rsOk))).unsafeRunSync()
    }

    "return Unit when attempting to requeue" in {
      queue.send(makeTestAddChange(rsOk)).unsafeRunSync()

      val result = queue.receive(MessageCount(2).right.value).unsafeRunSync()
      result.length shouldBe 1

      noException should be thrownBy
        queue.requeue(CommandMessage(result(0).handle, makeTestAddChange(rsOk))).unsafeRunSync()
    }

    "send a single message request" in {
      noException should be thrownBy queue.send(makeTestAddChange(rsOk)).unsafeRunSync()

      val result = queue.receive(MessageCount(1).right.value).unsafeRunSync()
      result.length shouldBe 1
      result(0).command shouldBe a[RecordSetChange]
    }

    "send a message batch request" in {
      val messages = NonEmptyList.fromListUnsafe(List(rsAddChange, zoneChangePending))

      val result = queue.send(messages).unsafeRunSync()
      result.successes.length shouldBe 2
      result.failures shouldBe empty
    }

    "change message visibility timeouts" in {
      queue.send(makeTestAddChange(rsOk)).unsafeRunSync()

      val result = queue.receive(MessageCount(2).right.value).unsafeRunSync()
      result.length shouldBe 1

      noException should be thrownBy
        queue.changeMessageTimeout(CommandMessage(result(0).handle, makeTestAddChange(rsOk)),
          FiniteDuration(5, SECONDS)).unsafeRunSync()
    }
  }
}
