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
import vinyldns.api.Interfaces.Result
import vinyldns.api.domain.auth.AuthPrincipal
import vinyldns.api.domain.membership._
import vinyldns.api.domain.zone.NotAuthorizedError
import vinyldns.api.route.MembershipJsonProtocol.{CreateGroupInput, UpdateGroupInput}

trait MembershipRoute extends Directives {
  this: VinylDNSJsonProtocol with VinylDNSDirectives with JsonValidationRejection =>
  final private val DEFAULT_MAX_ITEMS: Int = 100
  final private val MAX_ITEMS_LIMIT: Int = 1000

  val membershipService: MembershipServiceAlgebra

  val membershipRoute = { authPrincipal: AuthPrincipal =>
    path("groups" / Segment) { groupId =>
      get {
        monitor("Endpoint.getGroup") {
          execute(membershipService.getGroup(groupId, authPrincipal)) { group =>
            complete(StatusCodes.OK, GroupInfo(group))
          }
        }
      } ~
        delete {
          monitor("Endpoint.deleteGroup") {
            execute(membershipService.deleteGroup(groupId, authPrincipal)) { group =>
              complete(StatusCodes.OK, GroupInfo(group))
            }
          }
        }
    } ~
      path("groups") {
        post {
          monitor("Endpoint.createGroup") {
            entity(as[CreateGroupInput]) { input =>
              ifValid(Group
                .build(input.name, input.email, input.description, input.members, input.admins)) {
                inputGroup: Group =>
                  execute(membershipService.createGroup(inputGroup, authPrincipal)) { group =>
                    complete(StatusCodes.OK, GroupInfo(group))
                  }
              }
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
                        execute(membershipService
                          .listMyGroups(groupNameFilter, startFrom, maxItems, authPrincipal)) {
                          groups =>
                            complete(StatusCodes.OK, groups)
                        }
                      }
                    }
                  }
                }
            }
          }
      } ~
      path("groups" / Segment) { id =>
        put {
          monitor("Endpoint.updateGroup") {
            entity(as[UpdateGroupInput]) { input =>
              ifValid(
                Group.build(
                  input.id,
                  input.name,
                  input.email,
                  input.description,
                  input.members,
                  input.admins)) { inputGroup: Group =>
                execute(
                  membershipService.updateGroup(
                    inputGroup.id,
                    inputGroup.name,
                    inputGroup.email,
                    inputGroup.description,
                    inputGroup.memberIds,
                    inputGroup.adminUserIds,
                    authPrincipal)) { group =>
                  complete(StatusCodes.OK, GroupInfo(group))
                }
              }
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
                    execute(
                      membershipService.listMembers(groupId, startFrom, maxItems, authPrincipal)) {
                      members =>
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
            execute(membershipService.listAdmins(groupId, authPrincipal)) { admins =>
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
                    execute(membershipService
                      .getGroupActivity(groupId, startFrom, maxItems, authPrincipal)) { activity =>
                      complete(StatusCodes.OK, activity)
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
      case Left(GroupNotFoundError(msg)) => complete(StatusCodes.NotFound, msg)
      case Left(NotAuthorizedError(msg)) => complete(StatusCodes.Forbidden, msg)
      case Left(GroupAlreadyExistsError(msg)) => complete(StatusCodes.Conflict, msg)
      case Left(InvalidGroupError(msg)) => complete(StatusCodes.BadRequest, msg)
      case Left(UserNotFoundError(msg)) => complete(StatusCodes.NotFound, msg)
      case Left(InvalidGroupRequestError(msg)) => complete(StatusCodes.BadRequest, msg)
      case Left(e) => failWith(e)
    }
}
