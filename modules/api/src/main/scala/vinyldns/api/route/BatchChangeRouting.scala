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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server
import akka.http.scaladsl.server.{Directives, RejectionHandler, Route, ValidationRejection}
import cats.data.EitherT
import cats.effect._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.api.domain.batch._

trait BatchChangeRoute extends Directives {
  this: VinylDNSJsonProtocol with VinylDNSDirectives with JsonValidationRejection =>

  val batchChangeService: BatchChangeServiceAlgebra

  final private val MAX_ITEMS_LIMIT: Int = 100
  final private val MAX_COMMENT_LENGTH: Int = 1024

  val batchChangeRoute: AuthPrincipal => server.Route = { authPrincipal: AuthPrincipal =>
    (post & path("zones" / "batchrecordchanges")) {
      monitor("Endpoint.postBatchChange") {
        entity(as[BatchChangeInput]) { batchChangeInput =>
          execute(batchChangeService.applyBatchChange(batchChangeInput, authPrincipal)) { chg =>
            complete(StatusCodes.Accepted, chg)
          }
        }
      }
    } ~
      (get & path("zones" / "batchrecordchanges" / Segment)) { id =>
        monitor("Endpoint.getBatchChange") {
          execute(batchChangeService.getBatchChange(id, authPrincipal)) { chg =>
            complete(StatusCodes.OK, chg)
          }
        }
      } ~
      (get & path("zones" / "batchrecordchanges") & monitor("Endpoint.listBatchChangeSummaries")) {
        parameters("startFrom".as[Int].?, "maxItems".as[Int].?(MAX_ITEMS_LIMIT)) {
          (startFrom: Option[Int], maxItems: Int) =>
            {
              handleRejections(rejectionHandler) {
                validate(
                  0 < maxItems && maxItems <= MAX_ITEMS_LIMIT,
                  s"maxItems was $maxItems, maxItems must be between 1 and $MAX_ITEMS_LIMIT, inclusive.") {
                  execute(batchChangeService
                    .listBatchChangeSummaries(authPrincipal, startFrom, maxItems)) { summaries =>
                    complete(StatusCodes.OK, summaries)
                  }
                }
              }
            }
        }
      } ~
      (post & path("zones" / "batchrecordchanges" / Segment / "reject")) { _ =>
        monitor("Endpoint.rejectBatchChange") {
          entity(as[RejectBatchChangeInput]) { rejectBatchChangeInput =>
            handleRejections(rejectionHandler) {
              validate(
                rejectBatchChangeInput.comments.forall(c =>
                  c.length > 0 && c.length <= MAX_COMMENT_LENGTH),
                s"Comment length must be between 1 and $MAX_COMMENT_LENGTH characters, inclusive."
              ) {
                // TODO: Tie into batch change service with auth validation and rejection process
                complete(StatusCodes.OK)
                // TODO: Update response entity to return modified batch change
              }
            }
          } ~
            // TODO: Tie into batch change service with auth validation and rejection process
            complete(StatusCodes.OK) // Required for optional request entity
            // TODO: Update response entity to return modified batch change
        }
      }
  }

  // TODO: This is duplicated across routes.  Leaving duplicated until we upgrade our json serialization
  private val rejectionHandler = RejectionHandler
    .newBuilder()
    .handle {
      case ValidationRejection(msg, _) =>
        complete(StatusCodes.BadRequest, msg)
    }
    .result()

  private def execute[A](f: => EitherT[IO, BatchChangeErrorResponse, A])(rt: A => Route): Route =
    onSuccess(f.value.unsafeToFuture()) {
      case Right(a) => rt(a)
      case Left(ibci: InvalidBatchChangeInput) => complete(StatusCodes.BadRequest, ibci)
      case Left(crl: InvalidBatchChangeResponses) => complete(StatusCodes.BadRequest, crl)
      case Left(cnf: BatchChangeNotFound) => complete(StatusCodes.NotFound, cnf.message)
      case Left(una: UserNotAuthorizedError) => complete(StatusCodes.Forbidden, una.message)
      case Left(uct: BatchConversionError) => complete(StatusCodes.BadRequest, uct)
      case Left(uce: UnknownConversionError) => complete(StatusCodes.InternalServerError, uce)
    }
}
