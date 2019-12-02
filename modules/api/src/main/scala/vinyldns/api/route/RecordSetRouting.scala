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
import akka.http.scaladsl.server.{RejectionHandler, Route, ValidationRejection}
import akka.util.Timeout
import vinyldns.api.Interfaces._
import vinyldns.api.domain.record.RecordSetServiceAlgebra
import vinyldns.api.domain.zone._
import vinyldns.core.domain.record.NameSort.NameSort
import vinyldns.core.domain.record.RecordType.RecordType
import vinyldns.core.domain.record.{NameSort, RecordSet, RecordType}
import vinyldns.core.domain.zone.ZoneCommandResult

import scala.concurrent.duration._

case class GetRecordSetResponse(recordSet: RecordSetInfo)
case class ListRecordSetsResponse(
    recordSets: List[RecordSetListInfo],
    startFrom: Option[String] = None,
    nextId: Option[String] = None,
    maxItems: Option[Int] = None,
    recordNameFilter: Option[String] = None,
    recordTypeFilter: Option[Set[RecordType]] = None,
    sort: NameSort
)

class RecordSetRoute(
    recordSetService: RecordSetServiceAlgebra,
    val vinylDNSAuthenticator: VinylDNSAuthenticator
) extends VinylDNSJsonProtocol
    with VinylDNSDirectives[Throwable] {

  def getRoutes: Route = recordSetRoute

  final private val DEFAULT_MAX_ITEMS: Int = 100

  // Timeout must be long enough to allow the cluster to form
  implicit val rsCmdTimeout: Timeout = Timeout(10.seconds)

  def handleErrors(e: Throwable): PartialFunction[Throwable, Route] = {
    case ZoneNotFoundError(msg) => complete(StatusCodes.NotFound, msg)
    case RecordSetAlreadyExists(msg) => complete(StatusCodes.Conflict, msg)
    case ZoneInactiveError(msg) => complete(StatusCodes.BadRequest, msg)
    case NotAuthorizedError(msg) => complete(StatusCodes.Forbidden, msg)
    case ZoneUnavailableError(msg) => complete(StatusCodes.Conflict, msg)
    case RecordSetNotFoundError(msg) => complete(StatusCodes.NotFound, msg)
    case InvalidRequest(msg) => complete(StatusCodes.UnprocessableEntity, msg)
    case PendingUpdateError(msg) => complete(StatusCodes.Conflict, msg)
    case RecordSetChangeNotFoundError(msg) => complete(StatusCodes.NotFound, msg)
    case InvalidGroupError(msg) => complete(StatusCodes.UnprocessableEntity, msg)
  }

  val recordSetRoute: Route = path("zones" / Segment / "recordsets") { zoneId =>
    (post & monitor("Endpoint.addRecordSet")) {
      authenticateAndExecuteWithEntity[ZoneCommandResult, RecordSet](
        (authPrincipal, recordSet) => recordSetService.addRecordSet(recordSet, authPrincipal)
      ) { rc =>
        complete(StatusCodes.Accepted, rc)
      }
    } ~
      (get & monitor("Endpoint.getRecordSets")) {
        parameters(
          "startFrom".?,
          "maxItems".as[Int].?(DEFAULT_MAX_ITEMS),
          "recordNameFilter".?,
          "recordTypeFilter".?,
          "sort".as[String].?("ASC")
        ) {
          (
              startFrom: Option[String],
              maxItems: Int,
              recordNameFilter: Option[String],
              recordTypeFilter: Option[String],
              sort: String
          ) =>
            val convertedRecordTypeFilter = recordTypeFilter match {
              case Some(typeFilter) => {
                val recordTypes = typeFilter.split(",").flatMap(RecordType.find).toSet
                if (recordTypes.nonEmpty) {
                  Some(recordTypes)
                } else {
                  None
                }
              }
              case _ => None
            }
            handleRejections(invalidQueryHandler) {
              validate(
                0 < maxItems && maxItems <= DEFAULT_MAX_ITEMS,
                s"maxItems was $maxItems, maxItems must be between 0 and $DEFAULT_MAX_ITEMS"
              )
              validate(
                "ASC" == sort.toUpperCase || "DESC" == sort.toUpperCase,
                s"""sort was $sort, sort must be "ASC" or "DESC"."""
              ) {
                authenticateAndExecute(
                  recordSetService
                    .listRecordSets(
                      zoneId,
                      startFrom,
                      Some(maxItems),
                      recordNameFilter,
                      convertedRecordTypeFilter,
                      NameSort.find(sort),
                      _
                    )
                ) { rsResponse =>
                  complete(StatusCodes.OK, rsResponse)
                }
              }
            }
        }
      }
  } ~
    path("zones" / Segment / "recordsets" / Segment) { (zoneId, rsId) =>
      (get & monitor("Endpoint.getRecordSet")) {
        authenticateAndExecute(recordSetService.getRecordSet(rsId, zoneId, _)) { rs =>
          complete(StatusCodes.OK, GetRecordSetResponse(rs))
        }
      } ~
        (delete & monitor("Endpoint.deleteRecordSet")) {
          authenticateAndExecute(recordSetService.deleteRecordSet(rsId, zoneId, _)) { rc =>
            complete(StatusCodes.Accepted, rc)
          }
        } ~
        (put & monitor("Endpoint.updateRecordSet")) {
          authenticateAndExecuteWithEntity[ZoneCommandResult, RecordSet] {
            (authPrincipal, recordSet) =>
              recordSet match {
                case badRs if badRs.zoneId != zoneId =>
                  Left(InvalidRequest("Cannot update RecordSet's zoneId attribute")).toResult
                case goodRs =>
                  recordSetService.updateRecordSet(goodRs, authPrincipal)
              }
          } { rc =>
            complete(StatusCodes.Accepted, rc)
          }
        }
    } ~
    path("zones" / Segment / "recordsets" / Segment / "changes" / Segment) {
      (zoneId, _, changeId) =>
        (get & monitor("Endpoint.getRecordSetChange")) {
          authenticateAndExecute(recordSetService.getRecordSetChange(zoneId, changeId, _)) {
            change =>
              complete(StatusCodes.OK, change)
          }
        }
    } ~
    path("zones" / Segment / "recordsetchanges") { zoneId =>
      (get & monitor("Endpoint.listRecordSetChanges")) {
        parameters("startFrom".?, "maxItems".as[Int].?(DEFAULT_MAX_ITEMS)) {
          (startFrom: Option[String], maxItems: Int) =>
            handleRejections(invalidQueryHandler) {
              validate(
                check = 0 < maxItems && maxItems <= DEFAULT_MAX_ITEMS,
                errorMsg = s"maxItems was $maxItems, maxItems must be between 0 exclusive " +
                  s"and $DEFAULT_MAX_ITEMS inclusive"
              ) {
                authenticateAndExecute(
                  recordSetService
                    .listRecordSetChanges(zoneId, startFrom, maxItems, _)
                ) { changes =>
                  complete(StatusCodes.OK, changes)
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
}
