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

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.scalatest.ValidatedMatchers
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec

import scala.reflect.ClassTag

final case class TimeoutException(message: String) extends Throwable(message)

trait ResultHelpers {

  def leftValue[T](t: Either[Throwable, T]): Throwable = t.swap.toOption.get

  def rightValue[T](t: Either[Throwable, T]): T = t.toOption.get
}

object ValidationTestImprovements extends AnyPropSpec with Matchers with ValidatedMatchers {

  implicit class ValidatedNelTestImprovements[DomainValidationError, A](
      value: ValidatedNel[DomainValidationError, A]
  ) {

    def failures: List[DomainValidationError] = value match {
      case Invalid(e) => e.toList
      case Valid(_) =>
        fail("should have no failures!") // Will (correctly) cause expected failures to fail upon succeeding
    }

    def failWith[EE <: DomainValidationError](implicit tag: ClassTag[EE]): Unit =
      value.failures.map(_ shouldBe an[EE])
  }
}
