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

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{RejectionHandler, Route, ValidationRejection}
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.config.{LimitsConfig, ManualReviewConfig}
import vinyldns.api.domain.batch._
import vinyldns.core.domain.batch._

class BatchChangeRoute(
    batchChangeService: BatchChangeServiceAlgebra,
    limitsConfig: LimitsConfig,
    val vinylDNSAuthenticator: VinylDNSAuthenticator,
    manualReviewConfig: ManualReviewConfig
) extends VinylDNSJsonProtocol
    with VinylDNSDirectives[BatchChangeErrorResponse] {

  def getRoutes: Route = batchChangeRoute

  def logger: Logger = LoggerFactory.getLogger(classOf[BatchChangeRoute])

  def handleErrors(e: BatchChangeErrorResponse): PartialFunction[BatchChangeErrorResponse, Route] = {
    case ibci: InvalidBatchChangeInput => complete(StatusCodes.BadRequest, ibci)
    case crl: InvalidBatchChangeResponses => complete(StatusCodes.BadRequest, crl)
    case bcfa: BatchChangeFailedApproval => complete(StatusCodes.BadRequest, bcfa)
    case cnf: BatchChangeNotFound => complete(StatusCodes.NotFound, cnf.message)
    case una: UserNotAuthorizedError => complete(StatusCodes.Forbidden, una.message)
    case uct: BatchConversionError => complete(StatusCodes.BadRequest, uct)
    case bcnpa: BatchChangeNotPendingReview =>
      complete(StatusCodes.BadRequest, bcnpa.message)
    case uce: UnknownConversionError => complete(StatusCodes.InternalServerError, uce)
    case brnf: BatchRequesterNotFound => complete(StatusCodes.NotFound, brnf.message)
    case ManualReviewRequiresOwnerGroup =>
      complete(StatusCodes.BadRequest, ManualReviewRequiresOwnerGroup.message)
    case ScheduledChangesDisabled =>
      complete(StatusCodes.BadRequest, ScheduledChangesDisabled.message)
    case ScheduledTimeMustBeInFuture =>
      complete(StatusCodes.BadRequest, ScheduledTimeMustBeInFuture.message)
    case scnpd: ScheduledChangeNotDue => complete(StatusCodes.Forbidden, scnpd.message)
  }

  final private val MAX_ITEMS_LIMIT: Int = limitsConfig.BATCHCHANGE_ROUTING_MAX_ITEMS_LIMIT

  val batchChangeRoute: Route = {
    val standardBatchChangeRoutes = path("zones" / "batchrecordchanges") {
      (post & monitor("Endpoint.postBatchChange")) {
        parameters("allowManualReview".as[Boolean].?(true)) { allowManualReview: Boolean =>
          authenticateAndExecuteWithEntity[BatchChange, BatchChangeInput](
            (authPrincipal, batchChangeInput) =>
              batchChangeService
                .applyBatchChange(batchChangeInput, authPrincipal, allowManualReview)
          ) { chg =>
            complete(StatusCodes.Accepted, chg)
          }
        }
      } ~
      (get & monitor("Endpoint.listBatchChangeSummaries")) {
        parameters(
          "userName".as[String].?,
          "dateTimeRangeStart".as[String].?,
          "dateTimeRangeEnd".as[String].?,
          "startFrom".as[Int].?,
          "maxItems".as[Int].?(MAX_ITEMS_LIMIT),
          "ignoreAccess".as[Boolean].?(false),
          "approvalStatus".as[String].?
        ) {
          (
              userName: Option[String],
              dateTimeRangeStart: Option[String],
              dateTimeRangeEnd: Option[String],
              startFrom: Option[Int],
              maxItems: Int,
              ignoreAccess: Boolean,
              approvalStatus: Option[String]
          ) =>
            {
              val convertApprovalStatus = approvalStatus.flatMap(BatchChangeApprovalStatus.find)

              handleRejections(invalidQueryHandler) {
                validate(
                  0 < maxItems && maxItems <= MAX_ITEMS_LIMIT,
                  s"maxItems was $maxItems, maxItems must be between 1 and $MAX_ITEMS_LIMIT, inclusive."
                ) {
                  authenticateAndExecute(
                    batchChangeService.listBatchChangeSummaries(
                      _,
                      userName,
                      dateTimeRangeStart,
                      dateTimeRangeEnd,
                      startFrom,
                      maxItems,
                      ignoreAccess,
                      convertApprovalStatus
                    )
                  ) { summaries =>
                    complete(StatusCodes.OK, summaries)
                  }
                }
              }
            }
        }
      }
    } ~
      path("zones" / "batchrecordchanges" / Segment) { id =>
        (get & monitor("Endpoint.getBatchChange")) {
          authenticateAndExecute(batchChangeService.getBatchChange(id, _)) { chg =>
            complete(StatusCodes.OK, chg)
          }
        }
      }

    val manualBatchReviewRoutes =
      path("zones" / "batchrecordchanges" / Segment / "reject") { id =>
        (post & monitor("Endpoint.rejectBatchChange")) {
          authenticateAndExecuteWithEntity[BatchChange, Option[RejectBatchChangeInput]](
            (authPrincipal, input) =>
              batchChangeService
                .rejectBatchChange(id, authPrincipal, input.getOrElse(RejectBatchChangeInput()))
          ) { chg =>
            complete(StatusCodes.OK, chg)
          }
          // TODO: Update response entity to return modified batch change
        }
      } ~
        path("zones" / "batchrecordchanges" / Segment / "approve") { id =>
          (post & monitor("Endpoint.approveBatchChange")) {
            authenticateAndExecuteWithEntity[BatchChange, Option[ApproveBatchChangeInput]](
              (authPrincipal, input) =>
                batchChangeService
                  .approveBatchChange(id, authPrincipal, input.getOrElse(ApproveBatchChangeInput()))
            ) { chg =>
              complete(StatusCodes.Accepted, chg)
            // TODO: Update response entity to return modified batch change
            }
          }
        } ~
        path("zones" / "batchrecordchanges" / Segment / "cancel") { id =>
          (post & monitor("Endpoint.approveBatchChange")) {
            authenticateAndExecute(batchChangeService.cancelBatchChange(id, _)) { chg =>
              complete(StatusCodes.OK, chg)
            }
          }
        }

    if (manualReviewConfig.enabled)
      standardBatchChangeRoutes ~ manualBatchReviewRoutes
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
