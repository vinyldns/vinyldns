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
import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.queue.{MessageQueueConfig, MessageQueueLoader}
import vinyldns.sqs.queue.SqsMessageQueueProvider.InvalidQueueName

class SqsMessageQueueProviderIntegrationSpec extends WordSpec with Matchers {
  val undertest = new SqsMessageQueueProvider()

  "load" should {
    "fail if a required setting is not provided" in {
      val badConfig =
        ConfigFactory.parseString("""
          |    class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
          |    polling-interval = 250.millis
          |    messages-per-poll = 10
          |
          |    settings {
          |      access-key = "x"
          |      signing-region = "x"
          |      service-endpoint = "http://localhost:19007/"
          |      queue-name = "queue-name"
          |    }
          |    """.stripMargin)

      val badSettings = pureconfig.loadConfigOrThrow[MessageQueueConfig](badConfig)

      a[pureconfig.error.ConfigReaderException[MessageQueueConfig]] should be thrownBy undertest
        .load(badSettings)
        .unsafeRunSync()
    }

    "create the queue if the queue is non-existent" in {
      val nonExistentQueueConfig =
        ConfigFactory.parseString("""
          |    class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
          |    polling-interval = 250.millis
          |    messages-per-poll = 10
          |
          |    settings {
          |      access-key = "x"
          |      secret-key = "x"
          |      signing-region = "x"
          |      service-endpoint = "http://localhost:19007/"
          |      queue-name = "new-queue"
          |    }
          |    """.stripMargin)

      val messageConfig = pureconfig.loadConfigOrThrow[MessageQueueConfig](nonExistentQueueConfig)
      val messageQueue = undertest.load(messageConfig).unsafeRunSync()

      noException should be thrownBy messageQueue
        .asInstanceOf[SqsMessageQueue]
        .client
        .getQueueUrl("new-queue")
    }

    "fail with InvalidQueueName if an invalid queue name is given" in {
      val invalidQueueNameConfig =
        ConfigFactory.parseString("""
          |    class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
          |    polling-interval = 250.millis
          |    messages-per-poll = 10
          |
          |    settings {
          |      access-key = "x"
          |      secret-key = "x"
          |      signing-region = "x"
          |      service-endpoint = "http://localhost:19007/"
          |      queue-name = "bad*queue*name"
          |    }
          |    """.stripMargin)

      val messageConfig = pureconfig.loadConfigOrThrow[MessageQueueConfig](invalidQueueNameConfig)
      assertThrows[InvalidQueueName](undertest.load(messageConfig).unsafeRunSync())
    }

    "fail with InvalidQueueName if a FIFO queue is specified" in {
      val fifoQueueName =
        ConfigFactory.parseString("""
          |    class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
          |    polling-interval = 250.millis
          |    messages-per-poll = 10
          |
          |    settings {
          |      access-key = "x"
          |      secret-key = "x"
          |      signing-region = "x"
          |      service-endpoint = "http://localhost:19007/"
          |      queue-name = "queue.fifo"
          |    }
          |    """.stripMargin)

      val messageConfig = pureconfig.loadConfigOrThrow[MessageQueueConfig](fifoQueueName)
      assertThrows[InvalidQueueName](undertest.load(messageConfig).unsafeRunSync())
    }
  }

  "MessageQueueLoader" should {
    "invoke SQS provider properly" in {
      val nonExistentQueueConfig =
        ConfigFactory.parseString("""
          |    class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
          |    polling-interval = 250.millis
          |    messages-per-poll = 10
          |
          |    settings {
          |      access-key = "x"
          |      secret-key = "x"
          |      signing-region = "x"
          |      service-endpoint = "http://localhost:19007/"
          |      queue-name = "new-queue"
          |    }
          |    """.stripMargin)

      val messageConfig = pureconfig.loadConfigOrThrow[MessageQueueConfig](nonExistentQueueConfig)
      val queue = MessageQueueLoader.load(messageConfig).unsafeRunSync()

      queue shouldBe a[SqsMessageQueue]
    }
  }
}
