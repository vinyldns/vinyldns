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
import cats.data.{NonEmptyList, _}
import cats.implicits._
import vinyldns.api.domain.DomainValidationError

import scala.concurrent.{ExecutionContext, Future}

object BatchChangeInterfaces {

  type SingleValidation[A] = ValidatedNel[DomainValidationError, A]
  type ValidatedBatch[A] = List[ValidatedNel[DomainValidationError, A]]
  type BatchResult[A] = EitherT[Future, BatchChangeErrorResponse, A]

  implicit class FutureBatchResultImprovements[A](fut: Future[A])(implicit ec: ExecutionContext) {
    def toBatchResult: BatchResult[A] = EitherT {
      fut.map(_.asRight[BatchChangeErrorResponse])
    }
  }

  implicit class FutureEitherBatchResultImprovements[A](fut: Future[Either[_, A]])(
      implicit ec: ExecutionContext) {
    def toBatchResult: BatchResult[A] = EitherT {
      fut.map {
        case Right(r) => r.asRight[BatchChangeErrorResponse]
        case Left(err: BatchChangeErrorResponse) => err.asLeft[A]
        case Left(x) =>
          UnknownConversionError(s"Cannot convert item to BatchResponse: $x").asLeft[A]
      }
    }
  }

  implicit class EitherBatchResultImprovements[A](eth: Either[BatchChangeErrorResponse, A])(
      implicit ec: ExecutionContext) {
    def toBatchResult: BatchResult[A] = EitherT.fromEither[Future](eth)
  }

  implicit class BatchResultImprovements[A](a: A)(implicit ec: ExecutionContext) {
    def toRightBatchResult: BatchResult[A] = EitherT.rightT[Future, BatchChangeErrorResponse](a)
  }

  implicit class BatchResultErrorImprovements[A](err: BatchChangeErrorResponse)(
      implicit ec: ExecutionContext) {
    def toLeftBatchResult: BatchResult[A] = EitherT.leftT[Future, A](err)
  }

  implicit class ValidatedBatchImprovements[A](batch: ValidatedBatch[A]) {
    def mapValid[B](fn: A => ValidatedNel[DomainValidationError, B]): ValidatedBatch[B] =
      // gets rid of the map then flatmap thing we have to do when dealing with Seq[ValidatedNel[]]
      batch.map {
        case Valid(item) => fn(item)
        case Invalid(errList) => errList.invalid
      }

    def getValid: List[A] = batch.collect {
      case Valid(input) => input
    }
  }

  implicit class SingleValidationImprovements[A](validation: SingleValidation[A]) {
    def asUnit: SingleValidation[Unit] =
      validation.map(_ => ())
  }

  implicit class FutureCollectionImprovements[A](futures: List[Future[A]]) {
    // Pulls out the successful futures from the list; drops future failures
    def collectSuccesses()(implicit ec: ExecutionContext): Future[List[A]] = {
      val asSuccessfulOpt = futures.map { f =>
        f.map(a => Some(a)).recover {
          case _ => None
        }
      }

      Future.sequence(asSuccessfulOpt).map { lst =>
        lst.collect {
          case Some(rs) => rs
        }
      }
    }
  }
}
