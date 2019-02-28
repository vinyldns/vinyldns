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

package vinyldns.v2client.components

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.extra.router.BaseUrl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.ReactApp.csrf
import upickle.default._
import vinyldns.v2client.css.GlobalStyle
import vinyldns.v2client.models.user.User

import scala.util.Try

object TopNav {
  case class State(user: Option[User], drowdownOpen: Boolean)

  class Backend(bs: BackendScope[Unit, State]) {
    def getUser: Callback =
      Ajax("GET", "/api/users/currentuser")
        .setRequestHeader("Csrf-Token", csrf)
        .send
        .onComplete { xhr =>
          val user = read[User](xhr.responseText)
          bs.modState(_.copy(user = Some(user)))
        }
        .asCallback

    def toggleDropdown(e: ReactEventFromInput): Callback = {
      def withState(state: State) = bs.modState(_.copy(drowdownOpen = !state.drowdownOpen))
      e.preventDefaultCB >> bs.state >>= withState
    }

    def downdown(state: State): VdomNode =
      if (state.drowdownOpen)
        <.ul(
          GlobalStyle.styleSheet.overrideDisplay,
          ^.className := "dropdown-menu dropdown-usermenu pull-right",
          <.li(
            <.a(
              ^.className := "mb-control",
              ^.href := (BaseUrl.fromWindowOrigin / "logout").value,
              "Logout"
            )
          )
        )
      else <.div()

    def render(s: State): VdomElement =
      <.div(
        ^.className := "top-nav",
        <.div(
          ^.className := "nav_menu",
          <.nav(
            <.ul(
              ^.className := "nav navbar-nav navbar-right",
              <.li(
                <.a(
                  GlobalStyle.styleSheet.cursorPointer,
                  ^.className := "user-profile dropdown-toggle",
                  ^.onClick ==> toggleDropdown,
                  <.span(^.className := "fa fa-user"),
                  "  " + Try(s.user.get.userName).getOrElse[String]("Not Logged In") + "  ",
                  <.span(^.className := "fa fa-angle-down"),
                ),
                downdown(s)
              )
            )
          )
        )
      )
  }

  private val component = ScalaComponent
    .builder[Unit]("TopNav")
    .initialState(State(None, drowdownOpen = false))
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.getUser)
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()
}
