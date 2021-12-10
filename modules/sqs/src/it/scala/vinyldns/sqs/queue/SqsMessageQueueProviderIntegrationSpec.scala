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

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.core.queue.{MessageQueueConfig, MessageQueueLoader}
import vinyldns.sqs.queue.SqsMessageQueueProvider.InvalidQueueName
import pureconfig._
import pureconfig.generic.auto._

class SqsMessageQueueProviderIntegrationSpec extends AnyWordSpec with Matchers {
  val underTest = new SqsMessageQueueProvider()
  private val sqsEndpoint = sys.env.getOrElse("SQS_SERVICE_ENDPOINT", "http://localhost:19003")

  "load" should {
    "fail if a required setting is not provided" in {
      val badConfig =
        ConfigFactory.parseString(
          s"""
             |    class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
             |    polling-interval = 250.millis
             |    messages-per-poll = 10
             |    max-retries = 100
             |
             |    settings {
             |      service-endpoint = "$sqsEndpoint"
             |      queue-name = "queue-name"
             |    }
             |    """.stripMargin)

      val badSettings = ConfigSource.fromConfig(badConfig).loadOrThrow[MessageQueueConfig]

      a[pureconfig.error.ConfigReaderException[MessageQueueConfig]] should be thrownBy underTest
        .load(badSettings)
        .unsafeRunSync()
    }

    "create the queue if the queue is non-existent" in {
      val nonExistentQueueConfig =
        ConfigFactory.parseString(
          s"""
             |    class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
             |    polling-interval = 250.millis
             |    messages-per-poll = 10
             |    max-retries = 100
             |
             |    settings {
             |      access-key = "x"
             |      secret-key = "x"
             |      signing-region = "us-east-1"
             |      service-endpoint = "$sqsEndpoint"
             |      queue-name = "new-queue"
             |    }
             |    """.stripMargin)

      val messageConfig =
        ConfigSource.fromConfig(nonExistentQueueConfig).loadOrThrow[MessageQueueConfig]
      val messageQueue = underTest.load(messageConfig).unsafeRunSync()

      noException should be thrownBy messageQueue
        .asInstanceOf[SqsMessageQueue]
        .client
        .getQueueUrl("new-queue")
    }

    "fail with InvalidQueueName if an invalid queue name is given" in {
      val invalidQueueNameConfig =
        ConfigFactory.parseString(
          s"""
             |    class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
             |    polling-interval = 250.millis
             |    messages-per-poll = 10
             |    max-retries = 100
             |
             |    settings {
             |      access-key = "x"
             |      secret-key = "x"
             |      signing-region = "us-east-1"
             |      service-endpoint = "$sqsEndpoint"
             |      queue-name = "bad*queue*name"
             |    }
             |    """.stripMargin)

      val messageConfig =
        ConfigSource.fromConfig(invalidQueueNameConfig).loadOrThrow[MessageQueueConfig]
      assertThrows[InvalidQueueName](underTest.load(messageConfig).unsafeRunSync())
    }

    "fail with InvalidQueueName if a FIFO queue is specified" in {
      val fifoQueueName =
        ConfigFactory.parseString(
          s"""
             |    class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
             |    polling-interval = 250.millis
             |    messages-per-poll = 10
             |    max-retries = 100
             |
             |    settings {
             |      access-key = "x"
             |      secret-key = "x"
             |      signing-region = "us-east-1"
             |      service-endpoint = "$sqsEndpoint"
             |      queue-name = "queue.fifo"
             |    }
             |    """.stripMargin)

      val messageConfig = ConfigSource.fromConfig(fifoQueueName).loadOrThrow[MessageQueueConfig]
      assertThrows[InvalidQueueName](underTest.load(messageConfig).unsafeRunSync())
    }
  }

  "MessageQueueLoader" should {
    "invoke SQS provider properly" in {
      val nonExistentQueueConfig =
        ConfigFactory.parseString(
          s"""
             |    class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
             |    polling-interval = 250.millis
             |    messages-per-poll = 10
             |    max-retries = 100
             |
             |    settings {
             |      access-key = "x"
             |      secret-key = "x"
             |      signing-region = "us-east-1"
             |      service-endpoint = "$sqsEndpoint"
             |      queue-name = "new-queue"
             |    }
             |    """.stripMargin)

      val messageConfig =
        ConfigSource.fromConfig(nonExistentQueueConfig).loadOrThrow[MessageQueueConfig]
      val queue = MessageQueueLoader.load(messageConfig).unsafeRunSync()

      queue shouldBe a[SqsMessageQueue]
    }
  }
}
