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
import vinyldns.api.domain.membership._
import vinyldns.api.domain.zone.NotAuthorizedError
import vinyldns.api.route.MembershipJsonProtocol.{CreateGroupInput, UpdateGroupInput}
import vinyldns.core.domain.membership.{Group, LockStatus}

class MembershipRoute(
    membershipService: MembershipServiceAlgebra,
    val vinylDNSAuthenticator: VinylDNSAuthenticator)
    extends VinylDNSJsonProtocol
    with VinylDNSDirectives[Throwable] {
  final private val DEFAULT_MAX_ITEMS: Int = 100
  final private val MAX_ITEMS_LIMIT: Int = 1000

  def getRoutes(): Route = membershipRoute

  def sendResponse[A](either: Either[Throwable, A], f: A => Route): Route =
    either match {
      case Right(a) => f(a)
      case Left(GroupNotFoundError(msg)) => complete(StatusCodes.NotFound, msg)
      case Left(NotAuthorizedError(msg)) => complete(StatusCodes.Forbidden, msg)
      case Left(GroupAlreadyExistsError(msg)) => complete(StatusCodes.Conflict, msg)
      case Left(InvalidGroupError(msg)) => complete(StatusCodes.BadRequest, msg)
      case Left(UserNotFoundError(msg)) => complete(StatusCodes.NotFound, msg)
      case Left(InvalidGroupRequestError(msg)) => complete(StatusCodes.BadRequest, msg)
      case Left(e) => failWith(e)
    }

  val membershipRoute: Route = path("groups" / Segment) { groupId =>
    get {
      monitor("Endpoint.getGroup") {
        authenticateAndExecute(membershipService.getGroup(groupId, _)) { group =>
          complete(StatusCodes.OK, GroupInfo(group))
        }
      }
    } ~
      delete {
        monitor("Endpoint.deleteGroup") {
          authenticateAndExecute(membershipService.deleteGroup(groupId, _)) { group =>
            complete(StatusCodes.OK, GroupInfo(group))
          }
        }
      }
  } ~
    path("groups") {
      post {
        monitor("Endpoint.createGroup") {
          authenticateAndExecuteWithEntity[Group, CreateGroupInput] { (authPrincipal, input) =>
            val group = Group(
              input.name,
              input.email,
              input.description,
              memberIds = input.members.map(_.id),
              adminUserIds = input.admins.map(_.id))
            membershipService.createGroup(group, authPrincipal)
          } { group =>
            complete(StatusCodes.OK, GroupInfo(group))
          }
        }
      } ~
        get {
          parameters("startFrom".?, "maxItems".as[Int].?(DEFAULT_MAX_ITEMS), "groupNameFilter".?) {
            (startFrom: Option[String], maxItems: Int, groupNameFilter: Option[String]) =>
              {
                monitor("Endpoint.listMyGroups") {
                  handleRejections(invalidQueryHandler) {
                    validate(
                      check = 0 < maxItems && maxItems <= MAX_ITEMS_LIMIT,
                      errorMsg = s"""
                             | maxItems was $maxItems, maxItems must be between 0 exclusive
                             | and $MAX_ITEMS_LIMIT inclusive"
                           """.stripMargin
                    ) {
                      authenticateAndExecute(membershipService
                        .listMyGroups(groupNameFilter, startFrom, maxItems, _)) { groups =>
                        complete(StatusCodes.OK, groups)
                      }
                    }
                  }
                }
              }
          }
        }
    } ~
    path("groups" / Segment) { _ =>
      put {
        monitor("Endpoint.updateGroup") {
          authenticateAndExecuteWithEntity[Group, UpdateGroupInput](
            (authPrincipal, input) =>
              membershipService.updateGroup(
                input.id,
                input.name,
                input.email,
                input.description,
                (input.members ++ input.admins).map(_.id),
                input.admins.map(_.id),
                authPrincipal)) { group =>
            complete(StatusCodes.OK, GroupInfo(group))
          }
        }
      }
    } ~
    path("groups" / Segment / "members") { groupId =>
      get {
        monitor("Endpoint.listGroupMembers") {
          parameters("startFrom".?, "maxItems".as[Int].?(DEFAULT_MAX_ITEMS)) {
            (startFrom: Option[String], maxItems: Int) =>
              handleRejections(invalidQueryHandler) {
                validate(
                  0 < maxItems && maxItems <= MAX_ITEMS_LIMIT,
                  s"maxItems was $maxItems, maxItems must be between 0 exclusive and $MAX_ITEMS_LIMIT inclusive") {
                  authenticateAndExecute(membershipService
                    .listMembers(groupId, startFrom, maxItems, _)) { members =>
                    complete(StatusCodes.OK, members)
                  }
                }
              }
          }
        }
      }
    } ~
    path("groups" / Segment / "admins") { groupId =>
      get {
        monitor("Endpoint.listGroupAdmins") {
          authenticateAndExecute(membershipService.listAdmins(groupId, _)) { admins =>
            complete(StatusCodes.OK, admins)
          }
        }
      }
    } ~
    path("groups" / Segment / "activity") { groupId =>
      get {
        monitor("Endpoint.groupActivity") {
          parameters("startFrom".?, "maxItems".as[Int].?(DEFAULT_MAX_ITEMS)) {
            (startFrom: Option[String], maxItems: Int) =>
              handleRejections(invalidQueryHandler) {
                validate(
                  0 < maxItems && maxItems <= MAX_ITEMS_LIMIT,
                  s"maxItems was $maxItems, maxItems must be between 0 and $MAX_ITEMS_LIMIT") {
                  authenticateAndExecute(membershipService
                    .getGroupActivity(groupId, startFrom, maxItems, _)) { activity =>
                    complete(StatusCodes.OK, activity)
                  }
                }
              }
          }
        }
      }
    } ~
    (put & path("users" / Segment / "lock") & monitor("Endpoint.lockUser")) { id =>
      authenticateAndExecute(membershipService.updateUserLockStatus(id, LockStatus.Locked, _)) {
        user =>
          complete(StatusCodes.OK, UserInfo(user))
      }
    } ~
    (put & path("users" / Segment / "unlock") & monitor("Endpoint.unlockUser")) { id =>
      authenticateAndExecute(membershipService.updateUserLockStatus(id, LockStatus.Unlocked, _)) {
        user =>
          complete(StatusCodes.OK, UserInfo(user))
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
