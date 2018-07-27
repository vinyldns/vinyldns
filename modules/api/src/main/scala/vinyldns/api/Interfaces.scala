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

import akka.actor.Scheduler
import akka.pattern.after

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scalaz._
import scalaz.syntax.ToEitherOps

object Interfaces extends ToEitherOps {

  /**
    * the type returned from the ZoneActor is a ScalaZ disjunction \/, EitherT extends that to support
    * Future[ZoneError \/ ZoneEvt] this makes it easy to use results in for comprehensions among other things
    */
  type Result[A] = EitherT[Future, Throwable, A]

  /* Transforms a disjunction to a Result */
  def result[A](either: => Throwable \/ A): Result[A] = Result(Future.successful(either))

  /* Transforms any value at all into a positive result */
  def result[A](a: A): Result[A] = Result(Future.successful(a.right))

  /* Transforms an error into a Result with a left disjunction */
  def result[A](error: Throwable): Result[A] = Result(Future.successful(\/.left(error)))

  def ensuring(onError: => Throwable)(check: => Boolean): Disjunction[Throwable, Unit] =
    if (check) ().right else onError.left

  /**
    * If the future is a disjunction already, return the disjunction; otherwise return the successful value
    * as a disjunction
    */
  def result[A](fut: Future[_])(implicit ec: ExecutionContext): Result[A] =
    Result(
      fut
        .map {
          case disj: Disjunction[_, _] => disj
          case e: Throwable => e.left
          case a => a.right
        }
        .recover {
          case e: Throwable => e.left
        }
        .mapTo[Throwable \/ A]
    )

  def withTimeout[A](
      theFuture: => Future[A],
      duration: FiniteDuration,
      error: Throwable,
      scheduler: Scheduler)(implicit ec: ExecutionContext): Result[A] = result[A] {
    val timeOut = after(duration = duration, using = scheduler)(Future.failed(error))

    Future.firstCompletedOf(Seq(theFuture, timeOut))
  }

  /* Pimps futures to easily lift the future to a Result */
  implicit class FutureResultImprovements(fut: Future[_])(implicit ec: ExecutionContext) {

    /* Lifts a future into a Result */
    def toResult[A]: Result[A] = result[A](fut)
  }

  /*Convenience operations for working with Future of Option*/
  implicit class FutureOptionImprovements[A](fut: Future[Option[A]])(
      implicit ec: ExecutionContext) {

    /* If the result of the future is None, then fail with the provided parameter `ifNone` */
    def orFail(ifNone: => Throwable): Future[Throwable \/ A] = fut.map {
      case Some(a) => a.right
      case None => ifNone.left
    }
  }

  /* Pimps any value to easily lift the class to a Result */
  implicit class AnyResultImprovements[A](a: A)(implicit ec: ExecutionContext) {
    def toResult: Result[A] = result[A](a)
  }

  /* Pimps any existing Disjunction to easily lift the class to a Result */
  implicit class DisjunctionImprovements[A](disj: Throwable \/ A)(implicit ec: ExecutionContext) {
    def toResult: Result[A] = result[A](disj)
  }

  implicit class BooleanImprovements(bool: Boolean)(implicit ec: ExecutionContext) {
    /* If false, then fail with the provided parameter `ifFalse` */
    def failWith(ifFalse: Throwable): Result[Unit] =
      if (bool) result(())
      else result[Unit](ifFalse)
  }

  implicit class OptionImprovements[A](opt: Option[A])(implicit ec: ExecutionContext) {

    /* If the result of the future is None, then fail with the provided parameter `ifNone` */
    def orFail(ifNone: Throwable): Result[A] = opt match {
      case Some(a) => result(a)
      case None => result[A](ifNone)
    }
  }

}

object Result {

  def apply[A](f: => Future[Throwable \/ A]): Interfaces.Result[A] = EitherT(f)
}
