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

package vinyldns.api.domain.batch

import cats.implicits._
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.CatsHelpers
import vinyldns.api.domain.batch.BatchChangeInterfaces._

import cats.effect._
import cats.implicits._

class BatchChangeInterfacesSpec extends WordSpec with Matchers with CatsHelpers {

  "toBatchResult" should {
    "work with either success input" in {
      val input = "good"
      val out = input.asRight[BatchChangeErrorResponse].toBatchResult

      rightResultOf(out.value) shouldBe input
    }
    "work with either failure input" in {
      val error = ChangeLimitExceeded(10)
      val out = error.asLeft.toBatchResult

      leftResultOf(out.value) shouldBe error
    }
    "work with Future success inputs" in {
      val input = "good"
      val futureA = IO.pure(input)
      val futureEitherA = IO.pure(input.asRight[BatchChangeErrorResponse])
      val futureEitherANoType = IO.pure(input.asRight)

      val out1 = futureA.toBatchResult
      val out2 = futureEitherA.toBatchResult
      val out3 = futureEitherANoType.toBatchResult

      rightResultOf(out1.value) shouldBe input
      rightResultOf(out2.value) shouldBe input
      rightResultOf(out3.value) shouldBe input
    }
    "return a BatchChangeIsEmpty error if no changes are found" in {
      val futureError = IO.pure(BatchChangeIsEmpty(10).asLeft)
      val output = futureError.toBatchResult

      leftResultOf(output.value) shouldBe BatchChangeIsEmpty(10)
    }
    "return a ChangeLimitExceeded error if change limit is exceeded" in {
      val futureError = IO.pure(ChangeLimitExceeded(10).asLeft)
      val output = futureError.toBatchResult

      leftResultOf(output.value) shouldBe ChangeLimitExceeded(10)
    }
    "return a UnknownConversionError if run-time error is encountered during processing" in {
      val futureError = IO.pure(new RuntimeException("bad!").asLeft)
      val output = futureError.toBatchResult

      leftResultOf(output.value) shouldBe an[UnknownConversionError]
    }
    "return a RuntimeException error if Future fails" in {
      val futureError = IO.raiseError(new RuntimeException("bad!"))
      val output = futureError.toBatchResult

      a[RuntimeException] shouldBe thrownBy(await(output.value))
    }
  }
  "collectSuccesses" should {
    "return a IO[List] of all if all are successful" in {
      val futures = List(1, 2, 3, 4).map(IO.pure)

      val result = await(futures.collectSuccesses)
      result shouldBe List(1, 2, 3, 4)
    }
    "filter out unsuccessful futures" in {
      val futures = List(
        IO.pure(1),
        IO.raiseError(new RuntimeException("bad")),
        IO.pure(2),
        IO.raiseError(new RuntimeException("bad again")),
        IO.pure(3)
      )

      val result = await(futures.collectSuccesses)
      result shouldBe List(1, 2, 3)
    }
    "return an empty list of all fail" in {
      val futures = List(
        IO.raiseError(new RuntimeException("bad")),
        IO.raiseError(new RuntimeException("bad again"))
      )

      val result = await(futures.collectSuccesses)
      result shouldBe List()
    }
  }
}
