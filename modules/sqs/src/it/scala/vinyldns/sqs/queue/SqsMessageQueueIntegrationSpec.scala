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
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.retry.PredefinedBackoffStrategies.ExponentialBackoffStrategy
import com.amazonaws.retry.RetryPolicy
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.model.{SendMessageBatchRequest, SendMessageBatchRequestEntry}
import org.scalatest.mockito.MockitoSugar
import org.scalatest._
import vinyldns.core.TestRecordSetData._
import vinyldns.core.TestZoneData._
import vinyldns.core.domain.{RecordSetChange, ZoneCommand}
import vinyldns.core.queue.{CommandMessage, MessageCount}

import scala.concurrent.duration.FiniteDuration
import scala.collection.JavaConverters._

class SqsMessageQueueIntegrationSpec extends WordSpec
  with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach with Matchers with EitherMatchers with EitherValues {

  private val client =
    AmazonSQSAsyncClientBuilder
      .standard()
      .withClientConfiguration(
        new ClientConfiguration()
          .withRetryPolicy(new RetryPolicy(
            RetryPolicy.RetryCondition.NO_RETRY_CONDITION,
            new ExponentialBackoffStrategy(2, 64), // Base delay and max back-off delay of 64
            100, // Max error retry count (set to dead-letter count); default is 3
            true
          )))
      .withEndpointConfiguration(new EndpointConfiguration("http://localhost:19005/", "us-east-1"))
      .withCredentials(new AWSStaticCredentialsProvider(
        new BasicAWSCredentials("x", "x")))
      .build()

  case class TestSqsMessageQueue() extends SqsMessageQueue("http://localhost:19005/queue/sqs", client) {
    override def toSendMessageBatchRequest[A <: ZoneCommand](
      commands: NonEmptyList[A]): SendMessageBatchRequest = {
      print(s"Entering toSendMessageBatchRequest...\n")
      val entries = commands
        .map(cmd => (cmd, messageData(cmd)))
        .map {
          case (zc: ZoneCommand, msgBody) if zc.zoneId == okZone.id => {
            print(s"Entering here...\n")
            new SendMessageBatchRequestEntry()
              .withId("same-id")
              .withMessageBody(msgBody)
              .withMessageType(messageType(zc))
          }
          case (cmd, msgBody) =>
            new SendMessageBatchRequestEntry()
              .withMessageBody(msgBody)
              .withId(cmd.id)
              .withMessageType(messageType(cmd))
        }
        .toList
        .asJava

      new SendMessageBatchRequest()
        .withEntries(entries)
    }
  }

  private val queue: SqsMessageQueue = TestSqsMessageQueue()

  // Re-create queue before tests
  override protected def beforeAll(): Unit = {
    queue.client.createQueue("sqs")
  }

  override protected def afterEach(): Unit = {
    // Remove items from queue after each test
    val result = queue.receive(MessageCount(100).right.value).unsafeRunSync()
    result.foreach(queue.remove)
  }

  // Delete queue after tests since stuff can potentially linger
  override protected def afterAll(): Unit = {
    queue.client.deleteQueue(queue.queueUrl)
  }

  val rsAddChange: RecordSetChange = makeTestAddChange(rsOk)

  "SqsMessageQueueIntegrationSpec" should {
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
      result should have length 4
      result.foreach(_.command shouldBe a[ZoneCommand])
    }

    "succeed when attempting to remove item from empty queue" in {
      queue.remove(CommandMessage(SqsMessageHandle("does-not-exist"), makeTestAddChange(rsOk)))
        .attempt.unsafeRunSync() should beRight(())
    }

    "succeed when attempting to remove item from queue" in {
      queue.send(rsAddChange).unsafeRunSync()
      val result = queue.receive(MessageCount(1).right.value).unsafeRunSync()
      result.length shouldBe 1

      queue.remove(CommandMessage(result(0).handle, makeTestAddChange(rsOk)))
        .attempt.unsafeRunSync() should beRight(())
    }

    "succeed when attempting to requeue" in {
      queue.send(makeTestAddChange(rsOk)).unsafeRunSync()

      val result = queue.receive(MessageCount(2).right.value).unsafeRunSync()
      result should have length 1

      queue.requeue(CommandMessage(result(0).handle, makeTestAddChange(rsOk)))
        .attempt.unsafeRunSync() should beRight(())
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

      val result = queue.send(messages).unsafeRunSync()

      result.successes should contain theSameElementsAs commands
      result.failures shouldBe empty
    }

    "send a message batch request with successes and failures" in {
      val recordSetChanges = for (_ <- 0 to 4) yield makeTestAddChange(aaaa, zoneActive)
      val zoneChanges = for (_ <- 0 to 2) yield makeTestPendingZoneChange(okZone)
      val commands = recordSetChanges.toList ++ zoneChanges.toList

      val messages = NonEmptyList.fromListUnsafe(commands)

      val result = queue.send(messages).unsafeRunSync()
      print(s"failures: ${result.failures}\n")

      result.successes should contain theSameElementsAs commands
      result.failures shouldBe empty
    }

    "change message visibility timeouts" in {
      queue.send(makeTestAddChange(rsOk)).unsafeRunSync()

      val result = queue.receive(MessageCount(2).right.value).unsafeRunSync()
      result should have length 1

      queue.changeMessageTimeout(CommandMessage(result(0).handle, makeTestAddChange(rsOk)),
        FiniteDuration(5, SECONDS)).attempt.unsafeRunSync() should beRight(())
    }
  }
}
