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

package vinyldns.core.queue

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pureconfig._

import scala.concurrent.duration._

class MessageQueueConfigSpec extends AnyWordSpec with Matchers {

  private def load(hocon: String): MessageQueueConfig =
    ConfigSource.fromConfig(ConfigFactory.parseString(hocon)).loadOrThrow[MessageQueueConfig]

  "MessageQueueConfig configReader" should {

    "use default polling-interval (250ms) and messages-per-poll (10) when absent" in {
      val cfg = load(
        """{
          |  class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
          |  settings {}
          |}""".stripMargin
      )
      cfg.className       shouldBe "vinyldns.sqs.queue.SqsMessageQueueProvider"
      cfg.pollingInterval shouldBe 250.millis
      cfg.messagesPerPoll shouldBe 10
      cfg.maxRetries      shouldBe 100
    }

    "load explicit polling-interval and messages-per-poll when provided" in {
      val cfg = load(
        """{
          |  class-name       = "vinyldns.sqs.queue.SqsMessageQueueProvider"
          |  polling-interval = 500 milliseconds
          |  messages-per-poll = 20
          |  settings {}
          |}""".stripMargin
      )
      cfg.pollingInterval shouldBe 500.millis
      cfg.messagesPerPoll shouldBe 20
    }

    "load explicit max-retries when provided" in {
      val cfg = load(
        """{
          |  class-name  = "vinyldns.sqs.queue.SqsMessageQueueProvider"
          |  max-retries = 5
          |  settings {}
          |}""".stripMargin
      )
      cfg.maxRetries shouldBe 5
    }

    "default max-retries to 100 when absent" in {
      val cfg = load(
        """{
          |  class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
          |  settings {}
          |}""".stripMargin
      )
      cfg.maxRetries shouldBe 100
    }

    "parse settings block into a Config" in {
      val cfg = load(
        """{
          |  class-name = "vinyldns.sqs.queue.SqsMessageQueueProvider"
          |  settings { queue-name = "test-queue" }
          |}""".stripMargin
      )
      cfg.settings.getString("queue-name") shouldBe "test-queue"
    }
  }
}
