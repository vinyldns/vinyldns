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

class HealthServiceSpec
    extends WordSpec
    with Matchers
    with ResultHelpers
    with EitherMatchers
    with EitherValues {

  "Checking Status" should {
    "return an error if the zone repository could not be reached" in {
      val dsHealthCheck = List(IO.unit, IO.raiseError(new RuntimeException("bad!")))
      val underTest = new HealthService(dsHealthCheck)
      val result = underTest.checkHealth().unsafeRunSync()
      result.leftValue shouldBe a[RuntimeException]
    }

    "return success if the zone repository returns appropriately" in {
      val dsHealthCheck = List(IO.unit)
      val underTest = new HealthService(dsHealthCheck)
      val result = underTest.checkHealth().unsafeRunSync()
      result should be(right)
    }
  }
}
