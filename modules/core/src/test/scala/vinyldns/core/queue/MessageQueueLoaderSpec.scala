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

import cats.effect.IO
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

object MockMessageQueueProvider extends MockitoSugar {

  val mockMessageQueue: MessageQueue = mock[MessageQueue]

}

class MockMessageQueueProvider extends MessageQueueProvider {

  def load(config: MessageQueueConfig): IO[MessageQueue] =
    IO.pure(MockMessageQueueProvider.mockMessageQueue)

}

class FailMessageQueueProvider extends MessageQueueProvider {

  def load(config: MessageQueueConfig): IO[MessageQueue] =
    IO.raiseError(new RuntimeException("boo"))

}

class MessageQueueLoaderSpec extends WordSpec with Matchers {

  val placeholderConfig: Config = ConfigFactory.parseString("{}")
  private val pollingInterval = 250.millis
  private val messagesPerPoll = 10

  "load" should {
    "return the correct queue if properly configured" in {
      val config =
        MessageQueueConfig(
          "vinyldns.core.queue.MockMessageQueueProvider",
          pollingInterval,
          messagesPerPoll,
          placeholderConfig
        )

      val loadCall = MessageQueueLoader.load(config)
      loadCall.unsafeRunSync() shouldBe MockMessageQueueProvider.mockMessageQueue
    }
    "Error if the configured provider cannot be found" in {
      val config =
        MessageQueueConfig("bad.class", pollingInterval, messagesPerPoll, placeholderConfig)

      val loadCall = MessageQueueLoader.load(config)

      a[ClassNotFoundException] shouldBe thrownBy(loadCall.unsafeRunSync())
    }
    "Error if an error is returned from external load" in {
      val config =
        MessageQueueConfig(
          "vinyldns.core.queue.FailMessageQueueProvider",
          pollingInterval,
          messagesPerPoll,
          placeholderConfig
        )

      val loadCall = MessageQueueLoader.load(config)

      a[RuntimeException] shouldBe thrownBy(loadCall.unsafeRunSync())
    }
  }
}
