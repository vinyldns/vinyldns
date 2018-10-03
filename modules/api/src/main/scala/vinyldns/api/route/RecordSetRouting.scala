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
import akka.util.Timeout
import vinyldns.api.Interfaces._
import vinyldns.api.domain.record.RecordSetServiceAlgebra
import vinyldns.api.domain.zone._
import vinyldns.core.domain.auth.AuthPrincipal
import vinyldns.core.domain.record.RecordSet

import scala.concurrent.duration._

case class GetRecordSetResponse(recordSet: RecordSet)
case class ListRecordSetsResponse(
    recordSets: List[RecordSetInfo],
    startFrom: Option[String] = None,
    nextId: Option[String] = None,
    maxItems: Option[Int] = None,
    recordNameFilter: Option[String] = None)

trait RecordSetRoute extends Directives {
  this: VinylDNSJsonProtocol with VinylDNSDirectives with JsonValidationRejection =>
  final private val DEFAULT_MAX_ITEMS: Int = 100

  val recordSetService: RecordSetServiceAlgebra

  // Timeout must be long enough to allow the cluster to form
  implicit val rsCmdTimeout: Timeout = Timeout(10.seconds)

  val recordSetRoute = { authPrincipal: AuthPrincipal =>
    path("zones" / Segment / "recordsets") { zoneId =>
      post {
        monitor("Endpoint.addRecordSet") {
          entity(as[RecordSet]) { rs =>
            execute(recordSetService.addRecordSet(rs, authPrincipal)) { rc =>
              complete(StatusCodes.Accepted, rc)
            }
          }
        }
      } ~
        get {
          monitor("Endpoint.getRecordSets") {
            parameters("startFrom".?, "maxItems".as[Int].?(DEFAULT_MAX_ITEMS), "recordNameFilter".?) {
              (startFrom: Option[String], maxItems: Int, recordNameFilter: Option[String]) =>
                handleRejections(invalidQueryHandler) {
                  validate(
                    0 < maxItems && maxItems <= DEFAULT_MAX_ITEMS,
                    s"maxItems was ${maxItems}, maxItems must be between 0 and $DEFAULT_MAX_ITEMS") {
                    execute(
                      recordSetService.listRecordSets(
                        zoneId,
                        startFrom,
                        Some(maxItems),
                        recordNameFilter,
                        authPrincipal)) { rsResponse =>
                      complete(StatusCodes.OK, rsResponse)
                    }
                  }
                }
            }
          }
        }
    } ~
      path("zones" / Segment / "recordsets" / Segment) { (zoneId, rsId) =>
        get {
          monitor("Endpoint.getRecordSet") {
            execute(recordSetService.getRecordSet(rsId, zoneId, authPrincipal)) { rs =>
              complete(StatusCodes.OK, GetRecordSetResponse(rs))
            }
          }
        } ~
          delete {
            monitor("Endpoint.deleteRecordSet") {
              execute(recordSetService.deleteRecordSet(rsId, zoneId, authPrincipal)) { rc =>
                complete(StatusCodes.Accepted, rc)
              }
            }
          } ~
          put {
            monitor("Endpoint.updateRecordSet") {
              entity(as[RecordSet]) { rs =>
                execute(recordSetService.updateRecordSet(rs, authPrincipal)) { rc =>
                  complete(StatusCodes.Accepted, rc)
                }
              }
            }
          }
      } ~
      path("zones" / Segment / "recordsets" / Segment / "changes" / Segment) {
        (zoneId, _, changeId) =>
          get {
            monitor("Endpoint.getRecordSetChange") {
              execute(recordSetService.getRecordSetChange(zoneId, changeId, authPrincipal)) {
                change =>
                  complete(StatusCodes.OK, change)
              }
            }
          }
      } ~
      path("zones" / Segment / "recordsetchanges") { zoneId =>
        get {
          monitor("Endpoint.listRecordSetChanges") {
            parameters("startFrom".?, "maxItems".as[Int].?(DEFAULT_MAX_ITEMS)) {
              (startFrom: Option[String], maxItems: Int) =>
                handleRejections(invalidQueryHandler) {
                  validate(
                    check = 0 < maxItems && maxItems <= DEFAULT_MAX_ITEMS,
                    errorMsg = s"maxItems was $maxItems, maxItems must be between 0 exclusive " +
                      s"and $DEFAULT_MAX_ITEMS inclusive"
                  ) {
                    execute(recordSetService
                      .listRecordSetChanges(zoneId, startFrom, maxItems, authPrincipal)) {
                      changes =>
                        complete(StatusCodes.OK, changes)
                    }
                  }
                }
            }
          }
        }
      }
  }

  private val invalidQueryHandler = RejectionHandler
    .newBuilder()
    .handle {
      case ValidationRejection(msg, _) =>
        complete(StatusCodes.BadRequest, msg)
    }
    .result()

  private def execute[A](f: => Result[A])(rt: A => Route): Route =
    onSuccess(f.value.unsafeToFuture()) {
      case Right(a) => rt(a)
      case Left(ZoneNotFoundError(msg)) => complete(StatusCodes.NotFound, msg)
      case Left(RecordSetAlreadyExists(msg)) => complete(StatusCodes.Conflict, msg)
      case Left(ZoneInactiveError(msg)) => complete(StatusCodes.BadRequest, msg)
      case Left(NotAuthorizedError(msg)) => complete(StatusCodes.Forbidden, msg)
      case Left(ZoneUnavailableError(msg)) => complete(StatusCodes.Conflict, msg)
      case Left(RecordSetNotFoundError(msg)) => complete(StatusCodes.NotFound, msg)
      case Left(InvalidRequest(msg)) => complete(StatusCodes.UnprocessableEntity, msg)
      case Left(PendingUpdateError(msg)) => complete(StatusCodes.Conflict, msg)
      case Left(RecordSetChangeNotFoundError(msg)) => complete(StatusCodes.NotFound, msg)
      case Left(e) => failWith(e)
    }
}
