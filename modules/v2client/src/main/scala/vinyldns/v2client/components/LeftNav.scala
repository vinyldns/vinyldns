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
import japgolly.scalajs.react.extra.router.{BaseUrl, RouterCtl}
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.css.GlobalStyle
import vinyldns.v2client.routes.AppRouter.{Page, ToGroupListPage, ToGroupViewPage}

object LeftNav {
  case class NavItem(name: String, faClassName: String, page: Page)
  case class Props(menus: List[NavItem], selectedPage: Page, router: RouterCtl[Page])

  def activeClass(isActive: Boolean): String =
    if (isActive) "active"
    else ""

  def toSubMenu(P: Props, parent: Page): TagMod =
    (P.selectedPage, parent) match {
      case (groupView: ToGroupViewPage, _: ToGroupListPage.type) =>
        <.ul(
          GlobalStyle.styleSheet.overrideDisplay,
          ^.className := "nav child_menu",
          <.li(
            ^.className := "active",
            <.a(
              <.i(^.className := "fa fa-eye"),
              groupView.id,
              P.router.setOnClick(P.selectedPage)
            )
          )
        )
      case _ => TagMod.empty
    }

  def mouseEnter(e: ReactEventFromInput): Callback =
    Callback(e.currentTarget.className = "active")

  def mouseExit(e: ReactEventFromInput, isActive: Boolean): Callback =
    if (!isActive) Callback(e.currentTarget.className = "")
    else Callback(())

  private val component = ScalaComponent
    .builder[Props]("LeftNav")
    .render_P { P =>
      <.div(
        ^.className := "col-md-3 col-sm-3 col-xs-3 left_col",
        <.div(
          ^.className := "left_col scroll-view",
          GlobalStyle.styleSheet.width100,
          <.div(
            ^.className := "navbar nav_title vinyldns-nav-title",
            <.a(
              <.img(
                ^.className := "vinyldns-logo",
                ^.src := (BaseUrl.fromWindowOrigin / "public/images/vinyldns-portal.png").value
              )
            )
          ),
          <.div(^.className := "clearfix"),
          <.div(
            ^.className := "main_menu_side hidden-print main_menu",
            ^.id := "sidebar-menu",
            <.div(
              ^.className := "menu_section active",
              <.ul(
                ^.className := "nav side-menu",
                P.menus.toTagMod(
                  item => {
                    val isActive = item.page.getClass == P.selectedPage.getClass
                    <.li(
                      ^.className := activeClass(isActive),
                      ^.onMouseEnter ==> mouseEnter,
                      ^.onMouseLeave ==> (e => mouseExit(e, isActive)),
                      ^.key := item.name,
                      <.a(
                        <.i(^.className := item.faClassName),
                        item.name,
                        <.span(^.className := "fa fa-chevron-right"),
                        P.router.setOnClick(item.page)
                      ),
                      toSubMenu(P, item.page)
                    )
                  }
                ),
                <.li(
                  ^.key := "logout",
                  ^.onMouseEnter ==> mouseEnter,
                  ^.onMouseLeave ==> (e => mouseExit(e, false)),
                  <.a(
                    ^.href := (BaseUrl.fromWindowOrigin / "logout").value,
                    <.i(^.className := "fa fa-sign-out"),
                    "Logout"
                  )
                )
              )
            )
          )
        )
      )
    }
    .build

  def apply(props: Props): Unmounted[Props, Unit, Unit] = component(props)

}
