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

package vinyldns.v2client.pages

import scalacss.ScalaCssReact._
import vinyldns.v2client.models.Notification
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import scalajs.js.timers.setTimeout
import upickle.default.read
import vinyldns.v2client.ajax.{CurrentUserRoute, Request}
import vinyldns.v2client.components.AlertBox
import vinyldns.v2client.css.GlobalStyle
import vinyldns.v2client.models.user.User
import vinyldns.v2client.pages.MainContainer.PropsFromMain
import vinyldns.v2client.routes.AppRouter.Page

import scala.util.Try

// AppPages are pages that can be nested in MainContainer, e.g. HomePage, GroupListPage, etc.
// All AppPages receive PropsFromMain so they can get access to the Alerter, `Router, LoggedInUser, etc
trait AppPage {
  def apply(propsFromMain: PropsFromMain): Unmounted[PropsFromMain, _, _]
}

object MainContainer {
  final private val SUCCESS_ALERT_TIMEOUT_MILLIS = 5000.0
  // for now making error alerts never timeout, i.e. user must click close

  case class State(notification: Option[Notification] = None, loggedInUser: Option[User] = None)
  case class Props(childPage: AppPage, router: RouterCtl[Page], argsFromPath: List[String])

  case class Alerter(set: Option[Notification] => Callback)

  // these are passed to all child `AppPage`s
  case class PropsFromMain(
      alerter: Alerter,
      loggedInUser: User,
      router: RouterCtl[Page],
      argsFromPath: List[String])

  class Backend(bs: BackendScope[Props, State]) {
    def clearNotification: Callback =
      bs.modState(_.copy(notification = None))
    def setNotification(notification: Option[Notification]): Callback =
      notification match {
        case Some(n) if !n.isError =>
          bs.modState(_.copy(notification = notification)) >>
            Callback(setTimeout(SUCCESS_ALERT_TIMEOUT_MILLIS)(clearNotification.runNow()))
        case Some(n) if n.isError => bs.modState(_.copy(notification = notification))
        case None => Callback(())
      }

    def getLoggedInUser: Callback =
      Request
        .get(CurrentUserRoute())
        .onComplete { xhr =>
          setNotification(Request.toNotification("getting logged in user", xhr, onlyOnError = true))
          if (!Request.isError(xhr.status)) {
            val user = Try(read[User](xhr.responseText)).toOption
            bs.modState(_.copy(loggedInUser = user))
          } else Callback(())
        }
        .asCallback

    def renderAppPage(P: Props, S: State): VdomNode =
      S.loggedInUser match {
        case Some(user) =>
          P.childPage(PropsFromMain(Alerter(setNotification), user, P.router, P.argsFromPath))
        case None =>
          <.p(
            "Trouble retrieving user info. Please re-login. " +
              "If necessary, contact your VinylDNS Administrators.")
      }

    def render(P: Props, S: State): VdomElement =
      <.div(
        GlobalStyle.styleSheet.height100,
        ^.className := "right_col",
        ^.role := "main",
        S.notification match {
          case Some(n) => AlertBox(AlertBox.Props(n, () => clearNotification))
          case None => TagMod.empty
        },
        renderAppPage(P, S)
      )
  }

  private val component = ScalaComponent
    .builder[Props]("MainPage")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.getLoggedInUser)
    .build

  def apply(
      childPage: AppPage,
      router: RouterCtl[Page],
      argsFromPath: List[String] = List()): Unmounted[Props, State, Backend] =
    component(Props(childPage, router, argsFromPath))
}
