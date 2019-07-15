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

  def getRoutes(): Route = zoneRoute

  final private val DEFAULT_MAX_ITEMS: Int = 100
  final private val MAX_ITEMS_LIMIT: Int = 100

  // Timeout must be long enough to allow the cluster to form
  implicit val zoneCmdTimeout: Timeout = Timeout(10.seconds)

  def sendResponse[A](either: Either[Throwable, A], f: A => Route): Route =
    either match {
      case Right(a) => f(a)
      case Left(ZoneAlreadyExistsError(msg)) => complete(StatusCodes.Conflict, msg)
      case Left(ConnectionFailed(_, msg)) => complete(StatusCodes.BadRequest, msg)
      case Left(ZoneValidationFailed(zone, errors, _)) =>
        complete(StatusCodes.BadRequest, ZoneRejected(zone, errors))
      case Left(NotAuthorizedError(msg)) => complete(StatusCodes.Forbidden, msg)
      case Left(InvalidGroupError(msg)) => complete(StatusCodes.BadRequest, msg)
      case Left(ZoneNotFoundError(msg)) => complete(StatusCodes.NotFound, msg)
      case Left(ZoneUnavailableError(msg)) => complete(StatusCodes.Conflict, msg)
      case Left(InvalidSyncStateError(msg)) => complete(StatusCodes.BadRequest, msg)
      case Left(PendingUpdateError(msg)) => complete(StatusCodes.Conflict, msg)
      case Left(RecentSyncError(msg)) => complete(StatusCodes.Forbidden, msg)
      case Left(ZoneInactiveError(msg)) => complete(StatusCodes.BadRequest, msg)
      case Left(InvalidRequest(msg)) => complete(StatusCodes.BadRequest, msg)
      case Left(e) => failWith(e)
    }

  val zoneRoute: Route = (post & path("zones") & monitor("Endpoint.createZone")) {
    authenticateAndExecuteWithEntity[ZoneCommandResult, CreateZoneInput](
      (authPrincipal, createZoneInput) =>
        zoneService.connectToZone(encrypt(createZoneInput), authPrincipal)) { chg =>
      complete(StatusCodes.Accepted, chg)
    }
  } ~
    (get & path("zones") & monitor("Endpoint.listZones")) {
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
    } ~
    (get & path("zones" / "backendids") & monitor("Endpoint.getBackendIds")) {
      authenticateAndExecute(_ => zoneService.getBackendIds()) { ids =>
        complete(StatusCodes.OK, ids)
      }
    } ~
    (get & path("zones" / Segment) & monitor("Endpoint.getZone")) { id =>
      authenticateAndExecute(zoneService.getZone(id, _)) { zone =>
        complete(StatusCodes.OK, GetZoneResponse(zone))
      }
    } ~
    (get & path("zones" / "name" / Segment) & monitor("Endpoint.getZoneByName")) { zoneName =>
      authenticateAndExecute(zoneService.getZoneByName(zoneName, _)) { zone =>
        complete(StatusCodes.OK, GetZoneResponse(zone))
      }
    } ~
    (delete & path("zones" / Segment) & monitor("Endpoint.deleteZone")) { id =>
      authenticateAndExecute(zoneService.deleteZone(id, _)) { chg =>
        complete(StatusCodes.Accepted, chg)
      }
    } ~
    (put & path("zones" / Segment) & monitor("Endpoint.updateZone")) { _ =>
      authenticateAndExecuteWithEntity[ZoneCommandResult, UpdateZoneInput](
        (authPrincipal, updateZoneInput) =>
          zoneService.updateZone(encrypt(updateZoneInput), authPrincipal)) { chg =>
        complete(StatusCodes.Accepted, chg)
      }
    } ~
    (post & path("zones" / Segment / "sync") & monitor("Endpoint.syncZone")) { id =>
      authenticateAndExecute(zoneService.syncZone(id, _)) { chg =>
        complete(StatusCodes.Accepted, chg)
      }
    } ~
    (get & path("zones" / Segment / "changes") & monitor("Endpoint.listZoneChanges")) { id =>
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
    } ~
    (put & path("zones" / Segment / "acl" / "rules") & monitor("Endpoint.addZoneACLRule")) { id =>
      authenticateAndExecuteWithEntity[ZoneCommandResult, ACLRuleInfo]((authPrincipal, rule) =>
        zoneService.addACLRule(id, rule, authPrincipal)) { chg =>
        complete(StatusCodes.Accepted, chg)
      }
    } ~
    (delete & path("zones" / Segment / "acl" / "rules") & monitor("Endpoint.deleteZoneACLRule")) {
      id =>
        authenticateAndExecuteWithEntity[ZoneCommandResult, ACLRuleInfo]((authPrincipal, rule) =>
          zoneService.deleteACLRule(id, rule, authPrincipal)) { chg =>
          complete(StatusCodes.Accepted, chg)
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
