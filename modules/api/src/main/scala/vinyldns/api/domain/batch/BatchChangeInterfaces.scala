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

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.effect._
import cats.implicits._
import vinyldns.core.domain.DomainValidationError

object BatchChangeInterfaces {

  type SingleValidation[A] = ValidatedNel[DomainValidationError, A]
  type ValidatedBatch[A] = List[ValidatedNel[DomainValidationError, A]]
  type ValidatedBatchMonad[F[_], A] = List[F[ValidatedNel[DomainValidationError, A]]]
  type BatchResult[A] = EitherT[IO, BatchChangeErrorResponse, A]

  implicit class IOBatchResultImprovements[A](theIo: IO[A]) {
    def toBatchResult: BatchResult[A] = EitherT.liftF(theIo)
  }

  implicit class IOEitherBatchResultImprovements[A](theIo: IO[Either[_, A]]) {
    def toBatchResult: BatchResult[A] = EitherT {
      theIo.map {
        case Right(r) => Right(r)
        case Left(err: BatchChangeErrorResponse) => Left(err)
        case Left(x) =>
          Left(UnknownConversionError(s"Cannot convert item to BatchResponse: $x"))
      }
    }
  }

  implicit class EitherBatchResultImprovements[A](eth: Either[BatchChangeErrorResponse, A]) {
    def toBatchResult: BatchResult[A] = EitherT.fromEither[IO](eth)
  }

  implicit class BatchResultImprovements[A](a: A) {
    def toRightBatchResult: BatchResult[A] = EitherT.rightT[IO, BatchChangeErrorResponse](a)
  }

  implicit class BatchResultErrorImprovements[A](err: BatchChangeErrorResponse) {
    def toLeftBatchResult: BatchResult[A] = EitherT.leftT[IO, A](err)
  }

  implicit class ValidatedBatchImprovements[A](batch: ValidatedBatch[A]) {
    def mapValid[B](fn: A => ValidatedNel[DomainValidationError, B]): ValidatedBatch[B] =
      // gets rid of the map then flatmap thing we have to do when dealing with Seq[ValidatedNel[]]
      batch.map {
        case Valid(item) => fn(item)
        case Invalid(errList) => errList.invalid
      }

    def mapValidMonad[F[_], B](
        validFn: A => F[SingleValidation[B]]
    )(
        invalidFn: NonEmptyList[DomainValidationError] => F[SingleValidation[B]]
    ): ValidatedBatchMonad[F, B] =
      batch.map {
        case Valid(item) => validFn(item)
        case Invalid(errList) => invalidFn(errList)
      }

    def getValid: List[A] = batch.collect {
      case Valid(input) => input
    }

    def getInvalid: List[DomainValidationError] =
      batch
        .collect {
          case Invalid(input) => input
        }
        .flatMap(_.toList)
  }

  implicit class SingleValidationImprovements[A](validation: SingleValidation[A]) {
    def asUnit: SingleValidation[Unit] =
      validation.map(_ => ())
  }

  implicit class IOCollectionImprovements[A](value: List[IO[A]]) {
    // Pulls out the successful IO from the list; drops IO failures
    def collectSuccesses(): IO[List[A]] = {
      val asSuccessfulOpt: List[IO[Option[A]]] = value.map { f =>
        f.attempt.map {
          case Right(a) => Some(a)
          case _ => None
        }
      }

      asSuccessfulOpt.sequence[IO, Option[A]].map { lst =>
        lst.collect {
          case Some(rs) => rs
        }
      }
    }
  }
}
