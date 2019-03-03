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
import japgolly.scalajs.react.extra.router.BaseUrl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.css.GlobalStyle
import vinyldns.v2client.ReactApp.loggedInUser

object TopNav {
  case class State(dropdownOpen: Boolean = false)

  class Backend(bs: BackendScope[Unit, State]) {
    def dropdown(state: State): VdomNode =
      if (state.dropdownOpen)
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

    def mouseEnter(e: ReactEventFromInput): Callback =
      Callback(e.currentTarget.className = "active")

    def mouseExit(e: ReactEventFromInput): Callback =
      Callback(e.currentTarget.className = "")

    def render(S: State): VdomElement =
      <.div(
        ^.className := "top-nav",
        <.div(
          ^.className := "nav_menu",
          <.nav(
            <.ul(
              ^.className := "nav navbar-nav navbar-right",
              <.li(
                ^.onMouseEnter ==> mouseEnter,
                ^.onMouseLeave ==> mouseExit,
                <.a(
                  GlobalStyle.styleSheet.cursorPointer,
                  ^.className := "user-profile dropdown-toggle",
                  ^.onClick --> bs.modState(_.copy(dropdownOpen = !S.dropdownOpen)),
                  <.span(^.className := "fa fa-user"),
                  s"  ${loggedInUser.userName}  ",
                  <.span(^.className := "fa fa-angle-down"),
                ),
                dropdown(S)
              )
            )
          )
        )
      )
  }

  private val component = ScalaComponent
    .builder[Unit]("TopNav")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()
}
