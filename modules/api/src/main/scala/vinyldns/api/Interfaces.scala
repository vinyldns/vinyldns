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

import cats.data._
import cats.effect._
import cats.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object Interfaces {

  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  private implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  /* Our standard business error type */
  type Result[A] = EitherT[IO, Throwable, A]

  /* Transforms a disjunction to a Result */
  def result[A](either: => Either[Throwable, A]): Result[A] = Result(IO(either))

  /* Transforms any value at all into a positive result */
  def result[A](a: A): Result[A] = Result(IO(a.asRight[Throwable]))

  /* Transforms an error into a Result with a left disjunction */
  def result[A](error: Throwable): Result[A] = Result(IO(error.asLeft[A]))

  def ensuring(onError: => Throwable)(check: => Boolean): Either[Throwable, Unit] =
    if (check) Right(()) else Left(onError)

  /**
    * If the IO is an Either already, return the Either; otherwise return the successful value
    * as a disjunction
    *
    * TODO: This is rather unsightly, should remove and update the code elsewhere
    */
  def result[A](io: IO[_]): Result[A] =
    Result(
      io.map {
          case disj: Either[_, _] => disj.asInstanceOf[Either[Throwable, A]]
          case e: Throwable => Left(e)
          case a => Right(a.asInstanceOf[A])
        }
        .handleError(e => Left(e))
    )

  def withTimeout[A](theIo: => IO[A], duration: FiniteDuration, error: Throwable): Result[A] = {
    val timeOut = IO.sleep(duration) *> IO(error)
    EitherT(IO.race(timeOut, theIo).handleError(e => Left(e)))
  }

  /* Enhances IO to easily lift the io to a Result */
  implicit class IOResultImprovements(io: IO[_]) {

    /* Lifts a io into a Result */
    def toResult[A]: Result[A] = result[A](io)
  }

  /* Convenience operations for working with IO of Option */
  implicit class IOOptionImprovements[A](fut: IO[Option[A]]) {

    /* If the result of the IO is None, then fail with the provided parameter `ifNone` */
    // TODO: Can use OptionT here instead
    def orFail(ifNone: => Throwable): IO[Either[Throwable, A]] = fut.map {
      case Some(a) => Right(a)
      case None => Left(ifNone)
    }
  }

  /* Enhances any value to easily lift the class to a Result */
  implicit class AnyResultImprovements[A](a: A) {
    def toResult: Result[A] = result[A](a)
  }

  /* Enhances any existing Either to easily lift the class to a Result */
  implicit class EitherImprovements[A](value: Either[Throwable, A]) {
    def toResult: Result[A] = result[A](value)
  }

  implicit class BooleanImprovements(bool: Boolean) {
    /* If false, then fail with the provided parameter `ifFalse` */
    def failWith(ifFalse: Throwable): Result[Unit] =
      if (bool) result(())
      else result[Unit](ifFalse)
  }

  implicit class OptionImprovements[A](opt: Option[A]) {

    /* If the result of the option is None, then fail with the provided parameter `ifNone` */
    def orFail(ifNone: Throwable): Result[A] = opt match {
      case Some(a) => result(a)
      case None => result[A](ifNone)
    }
  }
}

object Result {

  def apply[A](f: => IO[Either[Throwable, A]]): Interfaces.Result[A] = EitherT(f)
}
