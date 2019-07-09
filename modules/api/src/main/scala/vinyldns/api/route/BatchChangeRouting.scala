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
import akka.http.scaladsl.server.{Directives, RejectionHandler, Route, ValidationRejection}
import vinyldns.api.VinylDNSConfig
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.batch._
import vinyldns.api.domain.batch._

trait BatchChangeRoute extends Directives {
  this: VinylDNSJsonProtocol with VinylDNSDirectives =>

  val batchChangeService: BatchChangeServiceAlgebra

  object BatchChangeAuthHelper extends AuthenticationBatchResultImprovements {
    def sendResponse[A](either: Either[BatchChangeErrorResponse, A], f: A => Route): Route =
      either match {
        case Right(a) => f(a)
        case Left(ibci: InvalidBatchChangeInput) => complete(StatusCodes.BadRequest, ibci)
        case Left(crl: InvalidBatchChangeResponses) => complete(StatusCodes.BadRequest, crl)
        case Left(cnf: BatchChangeNotFound) => complete(StatusCodes.NotFound, cnf.message)
        case Left(una: UserNotAuthorizedError) => complete(StatusCodes.Forbidden, una.message)
        case Left(uct: BatchConversionError) => complete(StatusCodes.BadRequest, uct)
        case Left(bcnpa: BatchChangeNotPendingApproval) =>
          complete(StatusCodes.BadRequest, bcnpa.message)
        case Left(uce: UnknownConversionError) => complete(StatusCodes.InternalServerError, uce)
      }
  }

  import BatchChangeAuthHelper._

  final private val MAX_ITEMS_LIMIT: Int = 100

  val batchChangeRoute: Route = {
    val standardBatchChangeRoutes = (post & path("zones" / "batchrecordchanges")) {
      parameters("allowManualReview".as[Boolean].?(true)) { allowManualReview: Boolean =>
        {
          monitor("Endpoint.postBatchChange") {
            authenticateAndExecuteWithEntity[BatchChange, BatchChangeInput](
              (authPrincipal, batchChangeInput) =>
                batchChangeService.applyBatchChange(batchChangeInput, authPrincipal, allowManualReview)) { chg =>
                  complete(StatusCodes.Accepted, chg)
                }
          }
        }
      }
    } ~
      (get & path("zones" / "batchrecordchanges" / Segment)) { id =>
        monitor("Endpoint.getBatchChange") {
          authenticateAndExecute(batchChangeService.getBatchChange(id, _)) { chg =>
            complete(StatusCodes.OK, chg)
          }
        }
      } ~
      (get & path("zones" / "batchrecordchanges") & monitor("Endpoint.listBatchChangeSummaries")) {
        parameters(
          "startFrom".as[Int].?,
          "maxItems".as[Int].?(MAX_ITEMS_LIMIT),
          "ignoreAccess".as[Boolean].?(false),
          "approvalStatus".as[String].?) {
          (
              startFrom: Option[Int],
              maxItems: Int,
              ignoreAccess: Boolean,
              approvalStatus: Option[String]) =>
            {
              val convertApprovalStatus = approvalStatus.flatMap(BatchChangeApprovalStatus.find)

              handleRejections(invalidQueryHandler) {
                validate(
                  0 < maxItems && maxItems <= MAX_ITEMS_LIMIT,
                  s"maxItems was $maxItems, maxItems must be between 1 and $MAX_ITEMS_LIMIT, inclusive.") {
                  authenticateAndExecute(
                    batchChangeService.listBatchChangeSummaries(
                      _,
                      startFrom,
                      maxItems,
                      ignoreAccess,
                      convertApprovalStatus)) { summaries =>
                    complete(StatusCodes.OK, summaries)
                  }
                }
              }
            }
        }
      }

    val manualBatchReviewRoutes =
      (post & path("zones" / "batchrecordchanges" / Segment / "reject")) { id =>
        monitor("Endpoint.rejectBatchChange") {
          authenticateAndExecuteWithEntity[BatchChange, Option[RejectBatchChangeInput]]((
              authPrincipal,
              input) => batchChangeService.rejectBatchChange(id, authPrincipal, input)) { chg =>
            complete(StatusCodes.OK, chg)
          }
          // TODO: Update response entity to return modified batch change
        }
      } ~
        (post & path("zones" / "batchrecordchanges" / Segment / "approve")) { id =>
          monitor("Endpoint.approveBatchChange") {
            authenticateAndExecuteWithEntity[BatchChange, Option[ApproveBatchChangeInput]](
              (authPrincipal, input) =>
                batchChangeService.approveBatchChange(id, authPrincipal, input)) { chg =>
              complete(StatusCodes.OK, chg)
            // TODO: Update response entity to return modified batch change
            }
          }
        }

    if (VinylDNSConfig.manualBatchReviewEnabled) standardBatchChangeRoutes ~ manualBatchReviewRoutes
    else standardBatchChangeRoutes
  }

  // TODO: This is duplicated across routes.  Leaving duplicated until we upgrade our json serialization
  private val invalidQueryHandler = RejectionHandler
    .newBuilder()
    .handle {
      case ValidationRejection(msg, _) =>
        complete(StatusCodes.BadRequest, msg)
    }
    .result()
}
