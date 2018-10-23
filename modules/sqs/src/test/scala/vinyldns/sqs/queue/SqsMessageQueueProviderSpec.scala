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
import org.scalatest.{Matchers, WordSpec}

class SqsMessageQueueProviderSpec extends WordSpec with Matchers {
  import SqsMessageQueueProvider._

  "getConfigName" should {
    "use DEFAULT_QUEUE_NAME if queue-name is not provided in config settings" in {
      val settings =
        SqsMessageQueueSettings("accessKey", "secretKey", "serviceEndpoint", "signingRegion", None)
      getQueueName(settings) shouldBe Right(DEFAULT_QUEUE_NAME)
    }

    "use queue-name if specified in config settings" in {
      val queueName = "override-queue-name"
      val settings = SqsMessageQueueSettings(
        "accessKey",
        "secretKey",
        "serviceEndpoint",
        "signingRegion",
        Some(queueName))
      getQueueName(settings) shouldBe Right(queueName)
    }
  }

  "validateQueueName" should {
    "succeed if queue name fully matches regex" in {
      val queueName = "valid-queue-name"
      validateQueueName(queueName) shouldBe Right(queueName)
    }

    "succeed if queue name ends in '.fifo'" in {
      val queueName = "queue.fifo"
      validateQueueName(queueName) shouldBe Right(queueName)
    }

    "fail if queue name does not match regex" in {
      val invalidQueueName = "a" * 81
      validateQueueName(invalidQueueName) shouldBe Left(InvalidQueueName(invalidQueueName))
    }
  }
}
