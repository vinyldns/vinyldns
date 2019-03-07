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

package vinyldns.client.ajax

import japgolly.scalajs.react.{Callback, CallbackTo}
import japgolly.scalajs.react.extra.Ajax
import org.scalajs.dom.raw.XMLHttpRequest
import vinyldns.client.ReactApp
import vinyldns.client.models.Notification
import vinyldns.client.models.user.User
import upickle.default.read
import vinyldns.client.models.membership.{Group, GroupList, MemberList}
import org.scalajs.dom

import scala.util.Try

// we do this so tests can use a mocked version of Request
object RequestHelper extends Request {
  val csrf: String = ReactApp.csrf.getOrElse("")
  val loggedInUser: User = ReactApp.loggedInUser
  val POST = "POST"
  val PUT = "PUT"
  val DELETE = "DELETE"

  def get[T](route: Route[T], onSuccess: OnSuccess[T], onFailure: OnFailure): Callback =
    Ajax
      .get(route.path)
      .setRequestHeader("Csrf-Token", csrf)
      .send
      .onComplete { xhr =>
        if (isError(xhr)) onFailure(xhr)
        else onSuccess(xhr, route.parse(xhr))
      }
      .asCallback

  private def putOrPost[T](
      route: Route[T],
      body: String,
      onSuccess: OnSuccess[T],
      onFailure: OnFailure,
      method: String): Callback =
    Ajax(method, route.path)
      .setRequestHeader("Csrf-Token", csrf)
      .setRequestContentTypeJson
      .send(body)
      .onComplete { xhr =>
        if (isError(xhr)) onFailure(xhr)
        else onSuccess(xhr, route.parse(xhr))
      }
      .asCallback

  def post[T](
      route: Route[T],
      body: String,
      onSuccess: OnSuccess[T],
      onFailure: OnFailure): Callback = putOrPost(route, body, onSuccess, onFailure, POST)

  def put[T](
      route: Route[T],
      body: String,
      onSuccess: OnSuccess[T],
      onFailure: OnFailure): Callback = putOrPost(route, body, onSuccess, onFailure, PUT)

  def delete[T](route: Route[T], onSuccess: OnSuccess[T], onFailure: OnFailure): Callback =
    Ajax(DELETE, route.path)
      .setRequestHeader("Csrf-Token", csrf)
      .send
      .onComplete { xhr =>
        if (isError(xhr)) onFailure(xhr)
        else onSuccess(xhr, route.parse(xhr))
      }
      .asCallback

  def withConfirmation(message: String, cb: Callback): Callback =
    CallbackTo[Boolean](dom.window.confirm(message)) >>= { confirmed =>
      if (confirmed) cb
      else Callback.empty
    }
}

trait Request {
  val csrf: String
  val loggedInUser: User
  type OnSuccess[T] = (XMLHttpRequest, Option[T]) => Callback
  type OnFailure = XMLHttpRequest => Callback

  def get[T](route: Route[T], onSuccess: OnSuccess[T], onFailure: OnFailure): Callback

  def post[T](
      route: Route[T],
      body: String,
      onSuccess: OnSuccess[T],
      onFailure: OnFailure): Callback

  def put[T](route: Route[T], body: String, onSuccess: OnSuccess[T], onFailure: OnFailure): Callback

  def delete[T](route: Route[T], onSuccess: OnSuccess[T], onFailure: OnFailure): Callback

  def withConfirmation(message: String, cb: Callback): Callback

  def toNotification(
      action: String,
      xhr: XMLHttpRequest,
      onlyOnError: Boolean = false,
      verbose: Boolean = false): Option[Notification] =
    xhr match {
      case error if isError(xhr) =>
        val customMessage = Some(s"$action [${error.status}] [${error.statusText}]")
        val responseMessage = Some(error.responseText)
        Some(Notification(customMessage, responseMessage, isError = true))
      case success if !onlyOnError =>
        val customMessage = Some(s"$action [${success.status}] [${success.statusText}]")
        val responseMessage = if (verbose) Some(success.responseText) else None
        Some(Notification(customMessage, responseMessage))
      case _ => None
    }

  def isError(xhr: XMLHttpRequest): Boolean = xhr.status >= 400
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
