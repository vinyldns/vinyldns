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

package vinyldns.client.http

import upickle.default.read
import vinyldns.client.models.membership.{Group, GroupList, MemberList}
import vinyldns.client.models.user.User

import scala.util.Try

sealed trait RequestRoute[T] {
  def path: String
  def parse(httpResponse: HttpResponse): Option[T]
}

object CurrentUserRoute extends RequestRoute[User] {
  def path: String = "/api/users/currentuser"
  def parse(httpResponse: HttpResponse): Option[User] =
    Try(Option(read[User](httpResponse.responseText))).getOrElse(None)
}

object ListGroupsRoute extends RequestRoute[GroupList] {
  def path: String = "/api/groups"
  def parse(httpResponse: HttpResponse): Option[GroupList] =
    Try(Option(read[GroupList](httpResponse.responseText))).getOrElse(None)
}

object PostGroupRoute extends RequestRoute[Group] {
  def path: String = "/api/groups"
  def parse(httpResponse: HttpResponse): Option[Group] =
    Try(Option(read[Group](httpResponse.responseText))).getOrElse(None)
}

final case class GetGroupRoute(id: String) extends RequestRoute[Group] {
  def path: String = s"/api/groups/$id"
  def parse(httpResponse: HttpResponse): Option[Group] =
    Try(Option(read[Group](httpResponse.responseText))).getOrElse(None)
}

final case class DeleteGroupRoute(id: String) extends RequestRoute[Group] {
  def path: String = s"/api/groups/$id"
  def parse(httpResponse: HttpResponse): Option[Group] =
    Try(Option(read[Group](httpResponse.responseText))).getOrElse(None)
}

final case class UpdateGroupRoute(id: String) extends RequestRoute[Group] {
  def path: String = s"/api/groups/$id"
  def parse(httpResponse: HttpResponse): Option[Group] =
    Try(Option(read[Group](httpResponse.responseText))).getOrElse(None)
}

final case class GetGroupMembersRoute(id: String) extends RequestRoute[MemberList] {
  def path: String = s"/api/groups/$id/members"
  def parse(httpResponse: HttpResponse): Option[MemberList] =
    Try(Option(read[MemberList](httpResponse.responseText))).getOrElse(None)
}

final case class LookupUserRoute(username: String) extends RequestRoute[User] {
  def path: String = s"/api/users/lookupuser/$username"
  def parse(httpResponse: HttpResponse): Option[User] =
    Try(Option(read[User](httpResponse.responseText))).getOrElse(None)
}
