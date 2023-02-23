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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import vinyldns.api.domain.batch.BatchChangeInterfaces._
import cats.effect._
import cats.implicits._
import vinyldns.core.domain.{BatchChangeIsEmpty, ChangeLimitExceeded}

class BatchChangeInterfacesSpec extends AnyWordSpec with Matchers {

  "toBatchResult" should {
    "work with either success input" in {
      val input = "good"
      val out = input.asRight[BatchChangeErrorResponse].toBatchResult

      out.value.unsafeRunSync().toOption.get shouldBe input
    }
    "work with either failure input" in {
      val error = InvalidBatchChangeInput(List(BatchChangeIsEmpty(10)))
      val out = error.asLeft.toBatchResult

      out.value.unsafeRunSync().swap.toOption.get shouldBe error
    }
    "work with Future success inputs" in {
      val input = "good"
      val futureA = IO.pure(input)
      val futureEitherA = IO.pure(input.asRight[BatchChangeErrorResponse])
      val futureEitherANoType = IO.pure(input.asRight)

      val out1 = futureA.toBatchResult
      val out2 = futureEitherA.toBatchResult
      val out3 = futureEitherANoType.toBatchResult

      out1.value.unsafeRunSync().toOption.get shouldBe input
      out2.value.unsafeRunSync().toOption.get shouldBe input
      out3.value.unsafeRunSync().toOption.get shouldBe input
    }
    "return a BatchChangeIsEmpty error if no changes are found" in {
      val futureError =
        IO.pure(InvalidBatchChangeInput(List(BatchChangeIsEmpty(10))).asLeft)
      val output = futureError.toBatchResult

      output.value.unsafeRunSync().swap.toOption.get shouldBe InvalidBatchChangeInput(List(BatchChangeIsEmpty(10)))
    }
    "return a ChangeLimitExceeded error if change limit is exceeded" in {
      val futureError =
        IO.pure(InvalidBatchChangeInput(List(ChangeLimitExceeded(10))).asLeft)
      val output = futureError.toBatchResult

      output.value.unsafeRunSync().swap.toOption.get shouldBe InvalidBatchChangeInput(List(ChangeLimitExceeded(10)))
    }
    "return a UnknownConversionError if run-time error is encountered during processing" in {
      val futureError = IO.pure(new RuntimeException("bad!").asLeft)
      val output = futureError.toBatchResult

      output.value.unsafeRunSync().swap.toOption.get shouldBe an[UnknownConversionError]
    }
    "return a RuntimeException error if Future fails" in {
      val futureError = IO.raiseError(new RuntimeException("bad!"))
      val output = futureError.toBatchResult

      a[RuntimeException] shouldBe thrownBy(output.value.unsafeRunSync())
    }
  }
  "collectSuccesses" should {
    "return a IO[List] of all if all are successful" in {
      val futures = List(1, 2, 3, 4).map(IO.pure)

      val result = futures.collectSuccesses.unsafeRunSync()
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

      val result = futures.collectSuccesses.unsafeRunSync()
      result shouldBe List(1, 2, 3)
    }
    "return an empty list of all fail" in {
      val futures = List(
        IO.raiseError(new RuntimeException("bad")),
        IO.raiseError(new RuntimeException("bad again"))
      )

      val result = futures.collectSuccesses.unsafeRunSync()
      result shouldBe List()
    }
  }
}
