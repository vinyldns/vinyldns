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

package vinyldns.api

import cats.effect._
import cats.implicits._
import vinyldns.api.domain.batch.BatchChangeInterfaces.ValidatedBatch
import vinyldns.api.domain.batch.BatchTransformations.ChangeForValidation

import scala.concurrent.duration._
import org.scalatest.Assertions._
import org.scalatest.matchers.{MatchResult, Matcher}

import scala.concurrent.ExecutionContext

trait CatsHelpers {
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  def await[E, T](f: => IO[T], duration: FiniteDuration = 1.second): T = {
    val i: IO[Either[E, T]] = f.attempt.map {
      case Right(ok) => Right(ok.asInstanceOf[T])
      case Left(e) => Left(e.asInstanceOf[E])
    }
    awaitResultOf[E, T](i, duration).toOption.get
  }

  // Waits for the future to complete, then returns the value as an Either[Throwable, T]
  def awaitResultOf[E, T](
      f: => IO[Either[E, T]],
      duration: FiniteDuration = 1.second
  ): Either[E, T] = {
    val timeOut = IO.sleep(duration) *> IO(new RuntimeException("Timed out waiting for result"))
    IO.race(timeOut, f).unsafeRunSync().toOption.get
  }

  // Assumes that the result of the future operation will be successful, this will fail on a left disjunction
  def rightResultOf[E, T](f: => IO[Either[E, T]], duration: FiniteDuration = 1.second): T =
    rightValue(awaitResultOf[E, T](f, duration))

  // Assumes that the result of the future operation will fail, this will error on a right disjunction
  def leftResultOf[E, T](f: => IO[Either[E, T]], duration: FiniteDuration = 1.second): E =
    leftValue(awaitResultOf(f, duration))

  def leftValue[E, T](t: Either[E, T]): E = t match {
    case Right(x) => fail(s"expected left value, got right: $x")
    case Left(err) => err
  }

  def rightValue[E, T](t: Either[E, T]): T = t match {
    case Right(x) => x
    case Left(err) => fail(s"expected right value, got left: $err")
  }
}

object ValidatedBatchMatcherImprovements extends ValidatedBatchMatcherImprovements

trait ValidatedBatchMatcherImprovements {
  class ValidatedBatchContainsChangeForValidation(expectedChange: ChangeForValidation)
      extends Matcher[ValidatedBatch[ChangeForValidation]] {
    def apply(left: ValidatedBatch[ChangeForValidation]): MatchResult =
      MatchResult(
        left.contains(expectedChange.validNel),
        s"ValidatedBatch $left does not contain $expectedChange",
        s"ValidatedBatch $left contains $expectedChange"
      )
  }

  def containChangeForValidation(
      expectedChange: ChangeForValidation
  ): ValidatedBatchContainsChangeForValidation =
    new ValidatedBatchContainsChangeForValidation(expectedChange)
}
