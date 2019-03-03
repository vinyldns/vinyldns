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

package vinyldns.v2client

import japgolly.scalajs.react.extra.router.BaseUrl
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.ext.Ajax
import vinyldns.v2client.ajax.CurrentUserRoute
import vinyldns.v2client.css.AppCSS
import vinyldns.v2client.routes.AppRouter
import upickle.default.read
import vinyldns.v2client.models.user.User

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.util.Try

@JSExportTopLevel("ReactApp")
object ReactApp {
  final val SUCCESS_ALERT_TIMEOUT_MILLIS = 5000.0
  final val csrf: String = document.getElementById("csrf").getAttribute("content")
  final val version: String = document.getElementById("version").getAttribute("content")
  var loggedInUser: User = _

  @JSExport
  def main(containerId: String): Unit = {
    AppCSS.load
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    Ajax
      .get(CurrentUserRoute().path)
      .onComplete { response =>
        response.map { xhr =>
          Try(Option(read[User](xhr.responseText))).getOrElse(None) match {
            case Some(u) =>
              loggedInUser = u
              AppRouter.router().renderIntoDOM(dom.document.getElementById(containerId))
            case None => dom.window.location.assign((BaseUrl.fromWindowOrigin / "login").value)
          }
        }
      }
  }
}
