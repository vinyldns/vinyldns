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

import java.util.{Base64, UUID}

import cats.data.NonEmptyList
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pureconfig._
import pureconfig.generic.auto._
import vinyldns.core.TestRecordSetData.pendingCreateAAAA
import vinyldns.core.protobuf.ProtobufConversions
import vinyldns.core.queue.{MessageCount, MessageQueueConfig}

import scala.concurrent.duration._

class SqsQueueSecurityIntegrationSpec
    extends AnyWordSpec
    with Matchers
    with EitherValues
    with ProtobufConversions {
  private val count = MessageCount(10).right.value

  private def withQueue(test: SqsMessageQueue => Unit): Unit = {
    val config = ConfigFactory
      .load()
      .getConfig("sqs")
      .withValue(
        "settings.queue-name",
        ConfigValueFactory.fromAnyRef("queue-auth-it-" + UUID.randomUUID().toString)
      )
    val settings = ConfigSource.fromConfig(config).loadOrThrow[MessageQueueConfig]
    val queue = new SqsMessageQueueProvider()
      .load(settings)
      .map(_.asInstanceOf[SqsMessageQueue])
      .unsafeRunSync()
    try test(queue)
    finally {
      try queue.client.deleteQueue(queue.queueUrl)
      finally queue.client.shutdown()
    }
  }

  "Authenticated SQS delivery" should {
    "delete unsigned and altered messages without exposing commands to consumers" in withQueue {
      queue =>
        val signing = vinyldns.core.queue.QueueMessageSigning.fromConfig(
          ConfigFactory.load().getConfig("sqs.signing")
        )
        val command = pendingCreateAAAA.copy(id = UUID.randomUUID().toString)
        val request =
          SqsMessageQueue.toSendMessageRequest(command, signing).withQueueUrl(queue.queueUrl)
        queue.client.sendMessage(
          request
            .clone()
            .withMessageBody(
              Base64.getEncoder.encodeToString(toPB(command).toByteArray)
            )
        )
        val altered = Base64.getDecoder.decode(request.getMessageBody)
        altered(altered.length - 33) = (altered(altered.length - 33) ^ 1).toByte
        queue.client.sendMessage(
          request.clone().withMessageBody(Base64.getEncoder.encodeToString(altered))
        )
        queue.receive(count).unsafeRunSync() shouldBe empty
        queue.receive(count).unsafeRunSync() shouldBe empty
        (queue.client
          .getQueueAttributes(
            queue.queueUrl,
            java.util.Arrays.asList(
              "ApproximateNumberOfMessages",
              "ApproximateNumberOfMessagesNotVisible"
            )
          )
          .getAttributes
          .values()
          .toArray
          .toList should contain).only("0")
    }

    "preserve authenticated identity for single sends, batches and redelivery" in withQueue {
      queue =>
        val first = pendingCreateAAAA.copy(id = UUID.randomUUID().toString)
        val second = pendingCreateAAAA.copy(id = UUID.randomUUID().toString)
        queue.send(first).unsafeRunSync()
        queue.sendBatch(NonEmptyList.of(second)).unsafeRunSync().successes shouldBe List(second)
        val received = queue.receive(count).unsafeRunSync()
        received.map(_.command) should contain theSameElementsAs List(first, second)
        received.foreach(
          message => queue.changeMessageTimeout(message, Duration.Zero).unsafeRunSync()
        )
        val retried = queue.receive(count).unsafeRunSync()
        retried.flatMap(_.authenticatedIdentity).toSet shouldBe received
          .flatMap(_.authenticatedIdentity)
          .toSet
        retried.foreach(message => queue.remove(message).unsafeRunSync())
    }
  }
}
