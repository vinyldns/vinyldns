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
import org.scalajs.dom

// we do this so tests can use a mocked version of Request
object RequestHelper extends Request {
  val csrf: String = ReactApp.csrf.getOrElse("")
  val loggedInUser: User = ReactApp.loggedInUser
  val POST = "POST"
  val PUT = "PUT"
  val DELETE = "DELETE"

  def get[T](route: RequestRoute[T], onSuccess: OnSuccess[T], onFailure: OnFailure): Callback =
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
      route: RequestRoute[T],
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
      route: RequestRoute[T],
      body: String,
      onSuccess: OnSuccess[T],
      onFailure: OnFailure): Callback = putOrPost(route, body, onSuccess, onFailure, POST)

  def put[T](
      route: RequestRoute[T],
      body: String,
      onSuccess: OnSuccess[T],
      onFailure: OnFailure): Callback = putOrPost(route, body, onSuccess, onFailure, PUT)

  def delete[T](route: RequestRoute[T], onSuccess: OnSuccess[T], onFailure: OnFailure): Callback =
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
