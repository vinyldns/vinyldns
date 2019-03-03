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

package vinyldns.v2client.ajax

import japgolly.scalajs.react.{Callback, CallbackTo}
import japgolly.scalajs.react.extra.Ajax
import org.scalajs.dom.raw.XMLHttpRequest
import vinyldns.v2client.ReactApp.csrf
import vinyldns.v2client.models.Notification
import vinyldns.v2client.models.user.User
import upickle.default.read
import vinyldns.v2client.models.membership.{Group, GroupList, MemberList}
import org.scalajs.dom

import scala.util.Try

object RequestHelper {
  def get[T](route: Route[T]): Ajax.Step2 =
    Ajax
      .get(route.path)
      .send

  def post[T](route: Route[T], body: String): Ajax.Step2 =
    Ajax
      .post(route.path)
      .setRequestHeader("Csrf-Token", csrf)
      .setRequestContentTypeJson
      .send(body)

  def put[T](route: Route[T], body: String): Ajax.Step2 =
    Ajax("PUT", route.path)
      .setRequestHeader("Csrf-Token", csrf)
      .setRequestContentTypeJson
      .send(body)

  def delete[T](route: Route[T]): Ajax.Step2 =
    Ajax("DELETE", route.path)
      .setRequestHeader("Csrf-Token", csrf)
      .send

  def toNotification(
      action: String,
      xhr: XMLHttpRequest,
      onlyOnError: Boolean = false): Option[Notification] =
    if (isError(xhr)) {
      val customMessage = Some(s"$action [${xhr.status}] [${xhr.statusText}]")
      val responseMessage = Some(xhr.responseText)
      Some(Notification(customMessage, responseMessage, isError = true))
    } else if (!onlyOnError) {
      val customMessage = Some(s"$action [${xhr.status}] [${xhr.statusText}]")
      Some(Notification(customMessage))
    } else {
      None
    }

  def isError(xhr: XMLHttpRequest): Boolean = xhr.status >= 400

  def withConfirmation(message: String, cb: Callback): Callback =
    CallbackTo[Boolean](dom.window.confirm(message)) >>= { confirmed =>
      if (confirmed) cb
      else Callback.empty
    }
}

sealed trait Route[T] {
  def path: String
  def parse(xhr: XMLHttpRequest): Option[T]
}

object CurrentUserRoute extends Route[User] {
  def path: String = "/api/users/currentuser"
  def parse(xhr: XMLHttpRequest): Option[User] =
    Try(Option(read[User](xhr.responseText))).getOrElse(None)
}

object ListGroupsRoute extends Route[GroupList] {
  def path: String = "/api/groups"
  def parse(xhr: XMLHttpRequest): Option[GroupList] =
    Try(Option(read[GroupList](xhr.responseText))).getOrElse(None)
}

object PostGroupRoute extends Route[Group] {
  def path: String = "/api/groups"
  def parse(xhr: XMLHttpRequest): Option[Group] =
    Try(Option(read[Group](xhr.responseText))).getOrElse(None)
}

final case class GetGroupRoute(id: String) extends Route[Group] {
  def path: String = s"/api/groups/$id"
  def parse(xhr: XMLHttpRequest): Option[Group] =
    Try(Option(read[Group](xhr.responseText))).getOrElse(None)
}

final case class DeleteGroupRoute(id: String) extends Route[Group] {
  def path: String = s"/api/groups/$id"
  def parse(xhr: XMLHttpRequest): Option[Group] =
    Try(Option(read[Group](xhr.responseText))).getOrElse(None)
}

final case class UpdateGroupRoute(id: String) extends Route[Group] {
  def path: String = s"/api/groups/$id"
  def parse(xhr: XMLHttpRequest): Option[Group] =
    Try(Option(read[Group](xhr.responseText))).getOrElse(None)
}

final case class GetGroupMembersRoute(id: String) extends Route[MemberList] {
  def path: String = s"/api/groups/$id/members"
  def parse(xhr: XMLHttpRequest): Option[MemberList] =
    Try(Option(read[MemberList](xhr.responseText))).getOrElse(None)
}

final case class LookupUserRoute(username: String) extends Route[User] {
  def path: String = s"/api/users/lookupuser/$username"
  def parse(xhr: XMLHttpRequest): Option[User] =
    Try(Option(read[User](xhr.responseText))).getOrElse(None)
}
