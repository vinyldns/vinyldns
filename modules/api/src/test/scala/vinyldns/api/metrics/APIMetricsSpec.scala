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

package vinyldns.api.metrics
import java.util.concurrent.TimeUnit

import cats.scalatest.EitherMatchers
import com.codahale.metrics.ScheduledReporter
import com.typesafe.config.ConfigFactory
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.VinylDNSConfig

class APIMetricsSpec extends WordSpec with Matchers with MockitoSugar with EitherMatchers {

  "APIMetricsSettings" should {
    "succeed with valid config" in {
      val config = ConfigFactory.parseString(
        """
          |{
          |  memory {
          |    log-enabled = true
          |    log-seconds = 5
          |  }
          |}
        """.stripMargin
      )
      APIMetrics.loadSettings(config).attempt.unsafeRunSync() shouldBe Right(
        APIMetricsSettings(MemoryMetricsSettings(logEnabled = true, logSeconds = 5)))
    }
    "fail with invalid config" in {
      val config = ConfigFactory.parseString(
        """
          |{
          |  memory {
          |    log-blah-enabled = true
          |    log-seconds = 5
          |  }
          |}
        """.stripMargin
      )
      APIMetrics.loadSettings(config).attempt.unsafeRunSync() shouldBe left
    }
    "default to log memory off" in {
      APIMetrics
        .loadSettings(VinylDNSConfig.vinyldnsConfig.getConfig("metrics"))
        .unsafeRunSync()
        .memory
        .logEnabled shouldBe false
    }
  }

  "APIMetrics" should {
    "start the log reporter if enabled" in {
      val reporter = mock[ScheduledReporter]
      APIMetrics
        .initialize(
          APIMetricsSettings(MemoryMetricsSettings(logEnabled = true, logSeconds = 5)),
          reporter)
        .unsafeRunSync()
      verify(reporter).start(5, TimeUnit.SECONDS)
    }
    "not start the log reporter if not enabled" in {
      val reporter = mock[ScheduledReporter]
      APIMetrics
        .initialize(
          APIMetricsSettings(MemoryMetricsSettings(logEnabled = false, logSeconds = 5)),
          reporter)
        .unsafeRunSync()
      verifyZeroInteractions(reporter)
    }
  }
}
