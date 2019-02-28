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

import japgolly.scalajs.react.extra.Ajax
import org.scalajs.dom.raw.XMLHttpRequest
import vinyldns.v2client.ReactApp.csrf
import vinyldns.v2client.models.Notification

object Request {
  def get(route: Route): Ajax.Step2 =
    Ajax
      .get(route.path)
      .send

  def post(route: Route, body: String): Ajax.Step2 =
    Ajax
      .post(route.path)
      .setRequestHeader("Csrf-Token", csrf)
      .setRequestContentTypeJson
      .send(body)

  def toNotification(
      action: String,
      xhr: XMLHttpRequest,
      onlyOnError: Boolean = false): Option[Notification] =
    if (isError(xhr.status)) {
      val customMessage = Some(s"$action [${xhr.status}] [${xhr.statusText}]")
      val responseMessage = Some(xhr.responseText)
      Some(Notification(customMessage, responseMessage, isError = true))
    } else if (!onlyOnError) {
      Some(Notification(Some(action)))
    } else {
      None
    }

  def isError(status: Int): Boolean = status >= 400
}

sealed trait Route {
  def path: String
}

final case class CurrentUserRoute() extends Route {
  def path: String = "/api/users/currentuser"
}

final case class ListGroupsRoute() extends Route {
  def path: String = "/api/groups"
}

final case class PostGroupRoute() extends Route {
  def path: String = "/api/groups"
}
