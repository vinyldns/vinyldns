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

package vinyldns.client.components

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.{BaseUrl, RouterCtl}
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.http.Http
import vinyldns.client.css.GlobalStyle
import vinyldns.client.router.{Page, ToApiCredentialsPage}

object TopNav {
  case class State(dropdownOpen: Boolean = false)
  case class Props(http: Http, routerCtl: RouterCtl[Page])

  private val component = ScalaComponent
    .builder[Props]("TopNav")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(http: Http, routerCtl: RouterCtl[Page]): Unmounted[Props, State, Backend] =
    component(Props(http, routerCtl))

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      <.div(
        ^.className := "top-nav",
        <.div(
          ^.className := "nav_menu",
          <.nav(
            <.ul(
              ^.className := "nav navbar-nav navbar-right",
              <.li(
                ^.onMouseEnter ==> mouseEnterDropdown,
                ^.onMouseLeave ==> mouseLeaveDropDown,
                <.a(
                  GlobalStyle.Styles.cursorPointer,
                  ^.className := "user-profile dropdown-toggle",
                  <.span(^.className := "fa fa-user"),
                  s"  ${P.http.getLoggedInUser().userName}  ",
                  <.span(^.className := "fa fa-angle-down"),
                ),
                dropdown(P, S)
              )
            )
          )
        )
      )

    def dropdown(P: Props, S: State): VdomNode =
      if (S.dropdownOpen)
        <.ul(
          GlobalStyle.Styles.displayBlock,
          ^.className := "dropdown-menu dropdown-usermenu pull-right",
          <.li(
            ^.onMouseEnter ==> mouseEnterItem,
            ^.onMouseLeave ==> mouseLeaveItem,
            <.a(
              ^.className := "mb-control",
              ^.href := (BaseUrl.fromWindowOrigin / "logout").value,
              "Logout"
            )
          ),
          <.li(
            ^.onMouseEnter ==> mouseEnterItem,
            ^.onMouseLeave ==> mouseLeaveItem,
            <.a(
              ^.className := "mb-control",
              P.routerCtl.setOnClick(ToApiCredentialsPage),
              "API Credentials"
            )
          )
        )
      else <.div()

    def mouseEnterDropdown(e: ReactEventFromInput): Callback =
      Callback(e.currentTarget.className = "active") >> bs.modState(_.copy(dropdownOpen = true))

    def mouseLeaveDropDown(e: ReactEventFromInput): Callback =
      Callback(e.currentTarget.className = "") >> bs.modState(_.copy(dropdownOpen = false))

    def mouseEnterItem(e: ReactEventFromInput): Callback =
      Callback(e.currentTarget.className = "active")

    def mouseLeaveItem(e: ReactEventFromInput): Callback =
      Callback(e.currentTarget.className = "")
  }
}
