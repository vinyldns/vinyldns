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
import akka.http.scaladsl.server._
import akka.util.Timeout
import vinyldns.api.crypto.Crypto
import vinyldns.api.domain.zone._
import vinyldns.core.domain.zone._

import scala.concurrent.duration._

case class GetZoneResponse(zone: ZoneInfo)
case class ZoneRejected(zone: Zone, errors: List[String])

class ZoneRoute(zoneService: ZoneServiceAlgebra, val vinylDNSAuthenticator: VinylDNSAuthenticator)
    extends VinylDNSJsonProtocol
    with VinylDNSDirectives[Throwable] {

  def getRoutes: Route = zoneRoute

  final private val DEFAULT_MAX_ITEMS: Int = 100
  final private val MAX_ITEMS_LIMIT: Int = 100

  // Timeout must be long enough to allow the cluster to form
  implicit val zoneCmdTimeout: Timeout = Timeout(10.seconds)

  def handleErrors(errors: Throwable): PartialFunction[Throwable, Route] = {
    case ZoneAlreadyExistsError(msg) => complete(StatusCodes.Conflict, msg)
    case ConnectionFailed(_, msg) => complete(StatusCodes.BadRequest, msg)
    case ZoneValidationFailed(zone, errorList, _) =>
      complete(StatusCodes.BadRequest, ZoneRejected(zone, errorList))
    case NotAuthorizedError(msg) => complete(StatusCodes.Forbidden, msg)
    case InvalidGroupError(msg) => complete(StatusCodes.BadRequest, msg)
    case ZoneNotFoundError(msg) => complete(StatusCodes.NotFound, msg)
    case ZoneUnavailableError(msg) => complete(StatusCodes.Conflict, msg)
    case InvalidSyncStateError(msg) => complete(StatusCodes.BadRequest, msg)
    case PendingUpdateError(msg) => complete(StatusCodes.Conflict, msg)
    case RecentSyncError(msg) => complete(StatusCodes.Forbidden, msg)
    case ZoneInactiveError(msg) => complete(StatusCodes.BadRequest, msg)
    case InvalidRequest(msg) => complete(StatusCodes.BadRequest, msg)
  }

  val zoneRoute: Route = path("zones") {
    (post & monitor("Endpoint.createZone")) {
      authenticateAndExecuteWithEntity[ZoneCommandResult, CreateZoneInput](
        (authPrincipal, createZoneInput) =>
          zoneService.connectToZone(encrypt(createZoneInput), authPrincipal)) { chg =>
        complete(StatusCodes.Accepted, chg)
      }
    } ~
      (get & monitor("Endpoint.listZones")) {
        parameters(
          "nameFilter".?,
          "startFrom".as[String].?,
          "maxItems".as[Int].?(DEFAULT_MAX_ITEMS),
          "ignoreAccess".as[Boolean].?(false)) {
          (
              nameFilter: Option[String],
              startFrom: Option[String],
              maxItems: Int,
              ignoreAccess: Boolean) =>
            {
              handleRejections(invalidQueryHandler) {
                validate(
                  0 < maxItems && maxItems <= MAX_ITEMS_LIMIT,
                  s"maxItems was $maxItems, maxItems must be between 0 and $MAX_ITEMS_LIMIT") {
                  authenticateAndExecute(zoneService
                    .listZones(_, nameFilter, startFrom, maxItems, ignoreAccess)) { result =>
                    complete(StatusCodes.OK, result)
                  }
                }
              }
            }
        }
      }
  } ~
    path("zones" / "backendids") {
      (get & monitor("Endpoint.getBackendIds")) {
        authenticateAndExecute(_ => zoneService.getBackendIds()) { ids =>
          complete(StatusCodes.OK, ids)
        }
      }
    } ~
    path("zones" / "name" / Segment) { zoneName =>
      authenticateAndExecute(zoneService.getZoneByName(zoneName, _)) { zone =>
        complete(StatusCodes.OK, GetZoneResponse(zone))
      }
    } ~
    path("zones" / Segment) { id =>
      (get & monitor("Endpoint.getZone")) {
        authenticateAndExecute(zoneService.getZone(id, _)) { zone =>
          complete(StatusCodes.OK, GetZoneResponse(zone))
        }
      } ~
        (put & monitor("Endpoint.updateZone")) {
          authenticateAndExecuteWithEntity[ZoneCommandResult, UpdateZoneInput](
            (authPrincipal, updateZoneInput) =>
              zoneService.updateZone(encrypt(updateZoneInput), authPrincipal)) { chg =>
            complete(StatusCodes.Accepted, chg)
          }
        } ~
        (delete & monitor("Endpoint.deleteZone")) {
          authenticateAndExecute(zoneService.deleteZone(id, _)) { chg =>
            complete(StatusCodes.Accepted, chg)
          }
        }
    } ~
    path("zones" / Segment / "sync") { id =>
      (post & monitor("Endpoint.syncZone")) {
        authenticateAndExecute(zoneService.syncZone(id, _)) { chg =>
          complete(StatusCodes.Accepted, chg)
        }
      }
    } ~
    path("zones" / Segment / "changes") { id =>
      (get & monitor("Endpoint.listZoneChanges")) {
        parameters("startFrom".?, "maxItems".as[Int].?(DEFAULT_MAX_ITEMS)) {
          (startFrom: Option[String], maxItems: Int) =>
            handleRejections(invalidQueryHandler) {
              validate(
                0 < maxItems && maxItems <= DEFAULT_MAX_ITEMS,
                s"maxItems was $maxItems, maxItems must be between 0 exclusive and $DEFAULT_MAX_ITEMS inclusive") {
                authenticateAndExecute(zoneService.listZoneChanges(id, _, startFrom, maxItems)) {
                  changes =>
                    complete(StatusCodes.OK, changes)
                }
              }
            }
        }
      }
    } ~
    path("zones" / Segment / "acl" / "rules") { id =>
      (put & monitor("Endpoint.addZoneACLRule")) {
        authenticateAndExecuteWithEntity[ZoneCommandResult, ACLRuleInfo]((authPrincipal, rule) =>
          zoneService.addACLRule(id, rule, authPrincipal)) { chg =>
          complete(StatusCodes.Accepted, chg)
        }
      } ~
        (delete & monitor("Endpoint.deleteZoneACLRule")) {
          authenticateAndExecuteWithEntity[ZoneCommandResult, ACLRuleInfo]((authPrincipal, rule) =>
            zoneService.deleteACLRule(id, rule, authPrincipal)) { chg =>
            complete(StatusCodes.Accepted, chg)
          }
        }
    }

  /**
    * Important!  Will encrypt the key on the zone if a connection is present
    * @param createZoneInput/updateZoneInput The zone input to be encrypted
    * @return A new zone with the connection encrypted, or the same zone if not connection
    */
  private def encrypt(createZoneInput: CreateZoneInput): CreateZoneInput =
    createZoneInput.copy(
      connection = createZoneInput.connection.map(_.encrypted(Crypto.instance)),
      transferConnection = createZoneInput.transferConnection.map(_.encrypted(Crypto.instance))
    )

  private def encrypt(updateZoneInput: UpdateZoneInput): UpdateZoneInput =
    updateZoneInput.copy(
      connection = updateZoneInput.connection.map(_.encrypted(Crypto.instance)),
      transferConnection = updateZoneInput.transferConnection.map(_.encrypted(Crypto.instance))
    )

  // TODO: This is duplicated across routes.  Leaving duplicated until we upgrade our json serialization
  private val invalidQueryHandler = RejectionHandler
    .newBuilder()
    .handle {
      case ValidationRejection(msg, _) =>
        complete(StatusCodes.BadRequest, msg)
    }
    .result()
}
