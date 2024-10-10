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
import org.slf4j.{Logger, LoggerFactory}
import vinyldns.api.config.LimitsConfig
import vinyldns.api.domain.membership._
import vinyldns.api.domain.zone.NotAuthorizedError
import vinyldns.api.route.MembershipJsonProtocol.{CreateGroupInput, UpdateGroupInput}
import vinyldns.core.domain.membership.{Group, LockStatus, PermissionStatus}

class MembershipRoute(
    membershipService: MembershipServiceAlgebra,
    limitsConfig: LimitsConfig,
    val vinylDNSAuthenticator: VinylDNSAuthenticator
) extends VinylDNSJsonProtocol
    with VinylDNSDirectives[Throwable] {

  final private val DEFAULT_MAX_ITEMS: Int = limitsConfig.MEMBERSHIP_ROUTING_DEFAULT_MAX_ITEMS
  final private val MAX_ITEMS_LIMIT: Int = limitsConfig.MEMBERSHIP_ROUTING_MAX_ITEMS_LIMIT
  final private val MAX_GROUPS_LIST_LIMIT: Int =
    limitsConfig.MEMBERSHIP_ROUTING_MAX_GROUPS_LIST_LIMIT

  def getRoutes: Route = membershipRoute

  def logger: Logger = LoggerFactory.getLogger(classOf[MembershipRoute])

  def handleErrors(e: Throwable): PartialFunction[Throwable, Route] = {
    case GroupNotFoundError(msg) => complete(StatusCodes.NotFound, msg)
    case NotAuthorizedError(msg) => complete(StatusCodes.Forbidden, msg)
    case GroupAlreadyExistsError(msg) => complete(StatusCodes.Conflict, msg)
    case GroupValidationError(msg) => complete(StatusCodes.BadRequest, msg)
    case InvalidGroupError(msg) => complete(StatusCodes.BadRequest, msg)
    case UserNotFoundError(msg) => complete(StatusCodes.NotFound, msg)
    case InvalidGroupRequestError(msg) => complete(StatusCodes.BadRequest, msg)
    case EmailValidationError(msg) => complete(StatusCodes.BadRequest, msg)
  }

  val membershipRoute: Route = path("groups" / Segment) { groupId =>
    (get & monitor("Endpoint.getGroup")) {
      authenticateAndExecute(membershipService.getGroup(groupId, _)) { group =>
        complete(StatusCodes.OK, GroupInfo(group))
      }
    } ~
      (delete & monitor("Endpoint.deleteGroup")) {
        authenticateAndExecute(membershipService.deleteGroup(groupId, _)) { group =>
          complete(StatusCodes.OK, GroupInfo(group))
        }
      }
  } ~
    path("groups") {
      (post & monitor("Endpoint.createGroup")) {
        authenticateAndExecuteWithEntity[Group, CreateGroupInput] { (authPrincipal, input) =>
          val group = Group(
            input.name,
            input.email,
            input.description,
            memberIds = (input.members ++ input.admins).map(_.id),
            adminUserIds = input.admins.map(_.id)
          )
          membershipService.createGroup(group, authPrincipal)
        } { group =>
          complete(StatusCodes.OK, GroupInfo(group))
        }
      } ~
        (get & monitor("Endpoint.listMyGroups")) {
          parameters(
            "startFrom".as[String].?,
            "maxItems".as[Int].?(DEFAULT_MAX_ITEMS),
            "groupNameFilter".?,
            "ignoreAccess".as[Boolean].?(false),
            "abridged".as[Boolean].?(false),
          ) {
            (
                startFrom: Option[String],
                maxItems: Int,
                groupNameFilter: Option[String],
                ignoreAccess: Boolean,
                abridged: Boolean
            ) =>
              {
                handleRejections(invalidQueryHandler) {
                  validate(
                    check = 0 < maxItems && maxItems <= MAX_GROUPS_LIST_LIMIT,
                    errorMsg = s"""
                           | maxItems was $maxItems, maxItems must be between 0 exclusive
                           | and $MAX_GROUPS_LIST_LIMIT inclusive"
                         """.stripMargin
                  ) {
                    authenticateAndExecute(
                      membershipService
                        .listMyGroups(groupNameFilter, startFrom, maxItems, _, ignoreAccess, abridged)
                    ) { groups =>
                      complete(StatusCodes.OK, groups)
                    }
                  }
                }
              }
          }
        }
    } ~
    path("groups" / Segment) { _ =>
      (put & monitor("Endpoint.updateGroup")) {
        authenticateAndExecuteWithEntity[Group, UpdateGroupInput](
          (authPrincipal, input) =>
            membershipService.updateGroup(
              input.id,
              input.name,
              input.email,
              input.description,
              (input.members ++ input.admins).map(_.id),
              input.admins.map(_.id),
              authPrincipal
            )
        ) { group =>
          complete(StatusCodes.OK, GroupInfo(group))
        }
      }
    } ~
    path("groups" / Segment / "members") { groupId =>
      (get & monitor("Endpoint.listGroupMembers")) {
        parameters("startFrom".?, "maxItems".as[Int].?(DEFAULT_MAX_ITEMS)) {
          (startFrom: Option[String], maxItems: Int) =>
            handleRejections(invalidQueryHandler) {
              validate(
                0 < maxItems && maxItems <= MAX_ITEMS_LIMIT,
                s"maxItems was $maxItems, maxItems must be between 0 exclusive and $MAX_ITEMS_LIMIT inclusive"
              ) {
                authenticateAndExecute(
                  membershipService
                    .listMembers(groupId, startFrom, maxItems, _)
                ) { members =>
                  complete(StatusCodes.OK, members)
                }
              }
            }
        }
      }
    } ~
    path("groups" / Segment / "admins") { groupId =>
      (get & monitor("Endpoint.listGroupAdmins")) {
        authenticateAndExecute(membershipService.listAdmins(groupId, _)) { admins =>
          complete(StatusCodes.OK, admins)
        }
      }
    } ~
    path("groups" / Segment / "activity") { groupId =>
      (get & monitor("Endpoint.groupActivity")) {
        parameters("startFrom".as[Int].?, "maxItems".as[Int].?(DEFAULT_MAX_ITEMS)) {
          (startFrom: Option[Int], maxItems: Int) =>
            handleRejections(invalidQueryHandler) {
              validate(
                0 < maxItems && maxItems <= MAX_ITEMS_LIMIT,
                s"maxItems was $maxItems, maxItems must be between 0 and $MAX_ITEMS_LIMIT"
              ) {
                authenticateAndExecute(
                  membershipService
                    .getGroupActivity(groupId, startFrom, maxItems, _)
                ) { activity =>
                  complete(StatusCodes.OK, activity)
                }
              }
            }
        }
      }
    } ~
    path("groups" / "change" / Segment) { groupChangeId =>
      (get & monitor("Endpoint.groupSingleChange")) {
        authenticateAndExecute(membershipService.getGroupChange(groupChangeId, _)) { groupChange =>
          complete(StatusCodes.OK, groupChange)
        }
      }
    } ~
    path("groups" / "valid" / "domains") {
      (get & monitor("Endpoint.validdomains")) {
        authenticateAndExecute(membershipService.listEmailDomains) { emailDomains =>
          complete(StatusCodes.OK, emailDomains)
        }
      }
    } ~
    path("users" / Segment / "lock") { id =>
      (put & monitor("Endpoint.lockUser")) {
        authenticateAndExecute(membershipService.updateUserLockStatus(id, LockStatus.Locked, _)) {
          user =>
            complete(StatusCodes.OK, UserInfo(user))
        }
      }
    } ~
    path("users" / Segment / "unlock") { id =>
      (put & monitor("Endpoint.unlockUser")) {
        authenticateAndExecute(membershipService.updateUserLockStatus(id, LockStatus.Unlocked, _)) {
          user =>
            complete(StatusCodes.OK, UserInfo(user))
        }
      }
    } ~
    path("users" / Segment / "update" / Segment) { (id, permissionStatus) =>
      (put & monitor("Endpoint.updateUserPermissionStatus")) {
        authenticateAndExecute(membershipService.updateUserPermissionStatus(id, PermissionStatus.find(permissionStatus), _)) {
          user =>
            complete(StatusCodes.OK, UserInfo(user))
        }
      }
    } ~
    path("users" / Segment) { id =>
      (get & monitor("Endpoint.getUser")) {
        authenticateAndExecute(membershipService.getUserDetails(id, _)) {
          user =>
            complete(StatusCodes.OK, user)
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
