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
import com.amazonaws.AmazonClientException
import com.amazonaws.retry.RetryPolicy
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import org.scalatest.{Matchers, WordSpec}

class SqsMessageQueueProviderSpec extends WordSpec with Matchers {
  val undertest = new SqsMessageQueueProvider()
  import SqsMessageQueueProvider._

  "clientConfiguration" should {
    "create a configuration with expected settings" in {
      val clientConfig = undertest.clientConfiguration

      val retryPolicy = clientConfig.getRetryPolicy

      retryPolicy.getRetryCondition shouldBe RetryPolicy.RetryCondition.NO_RETRY_CONDITION
      retryPolicy.getMaxErrorRetry shouldBe 100
      retryPolicy.isMaxErrorRetryInClientConfigHonored shouldBe true
    }

    "exponential backoff should retry as expected" in {
      val backoffStrategy = undertest.clientConfiguration.getRetryPolicy.getBackoffStrategy
      val originalRequest = new ReceiveMessageRequest()

      backoffStrategy.delayBeforeNextRetry(
        originalRequest,
        new AmazonClientException("first retry"),
        0) shouldBe 2

      backoffStrategy.delayBeforeNextRetry(
        originalRequest,
        new AmazonClientException("second retry"),
        1) shouldBe 4

      backoffStrategy.delayBeforeNextRetry(
        originalRequest,
        new AmazonClientException("third retry"),
        2) shouldBe 8

      backoffStrategy.delayBeforeNextRetry(
        originalRequest,
        new AmazonClientException("fourth retry"),
        3) shouldBe 16

      backoffStrategy.delayBeforeNextRetry(
        originalRequest,
        new AmazonClientException("fifth retry"),
        4) shouldBe 32

      backoffStrategy.delayBeforeNextRetry(
        originalRequest,
        new AmazonClientException("sixth retry"),
        5) shouldBe 64

      backoffStrategy.delayBeforeNextRetry(
        originalRequest,
        new AmazonClientException("max retry"),
        99) shouldBe 64
    }
  }

  "validateQueueName" should {
    "succeed if queue name fully matches regex" in {
      val queueName = "valid-queue-name"
      undertest.validateQueueName(queueName) shouldBe Right(queueName)
    }

    "fail if queue name does not match regex" in {
      val invalidQueueName = "a" * 81
      undertest.validateQueueName(invalidQueueName) shouldBe Left(
        InvalidQueueName(invalidQueueName))
    }
  }
}
