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

package vinyldns.api.route

import cats.scalatest.{EitherMatchers, EitherValues}
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.ResultHelpers
import cats.effect._
import vinyldns.core.route.HealthCheck._

class HealthServiceSpec
    extends WordSpec
    with Matchers
    with ResultHelpers
    with EitherMatchers
    with EitherValues {

  "Checking Status" should {
    val successCheck: HealthCheckResponse = IO.unit.attempt.asHealthCheckResponse
    val failCheck: HealthCheckResponse =
      IO.raiseError(new RuntimeException("bad!")).attempt.asHealthCheckResponse

    "return all health check failures" in {
      val dsHealthCheck = List(successCheck, failCheck)
      val underTest = new HealthService(dsHealthCheck)
      val result = underTest.checkHealth().unsafeRunSync()
      result.length shouldBe 1
      result.head.message shouldBe "bad!"
    }

    "return an empty list when no errors" in {
      val dsHealthCheck = List(successCheck, successCheck)
      val underTest = new HealthService(dsHealthCheck)
      val result = underTest.checkHealth().unsafeRunSync()
      result shouldBe Nil
    }
  }
}
