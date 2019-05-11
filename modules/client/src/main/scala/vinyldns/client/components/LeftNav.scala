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
import vinyldns.client.css.GlobalStyle
import vinyldns.client.models.CustomLink
import vinyldns.client.router._
import upickle.default.read
import vinyldns.client.ReactApp.customLinksJson
import vinyldns.client.components.JsNative.logError

import scala.util.{Failure, Success, Try}

object LeftNav {
  case class NavItem(name: String, faClassName: String, page: Page)
  case class Props(menus: List[NavItem], selectedPage: Page, router: RouterCtl[Page])
  case class State(customLinks: List[CustomLink] = List())

  def apply(props: Props): Unmounted[Props, State, Unit] = component(props)

  private val component = ScalaComponent
    .builder[Props]("LeftNav")
    .initialState(State())
    .render_PS { (P, S) =>
      <.div(
        ^.className := "col-md-3 col-sm-3 col-xs-3 left_col",
        <.div(
          ^.className := "left_col scroll-view",
          GlobalStyle.Styles.width100,
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
                P.menus
                  .map(
                    item => {
                      val active = isActive(P, item.page)
                      <.li(
                        ^.className := activeClass(active),
                        ^.onMouseEnter ==> mouseEnter,
                        ^.onMouseLeave ==> (e => mouseExit(e, active)),
                        ^.key := item.name,
                        <.a(
                          <.i(^.className := item.faClassName),
                          item.name,
                          <.span(^.className := "fa fa-chevron-right"),
                          P.router.setOnClick(item.page)
                        ),
                        // determine if the current menu item has active children
                        toSubMenu(P, item.page)
                      )
                    }
                  )
                  .toTagMod,
                S.customLinks.map { link =>
                  if (link.displayOnSidebar)
                    <.li(
                      ^.key := link.title,
                      ^.onMouseEnter ==> mouseEnter,
                      ^.onMouseLeave ==> (e => mouseExit(e, isActive = false)),
                      <.a(
                        ^.href := link.href,
                        <.i(^.className := link.icon),
                        link.title
                      )
                    )
                  else TagMod.empty
                }.toTagMod,
                <.li(
                  ^.key := "logout",
                  ^.onMouseEnter ==> mouseEnter,
                  ^.onMouseLeave ==> (e => mouseExit(e, isActive = false)),
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
    .componentWillMount { e =>
      customLinksJson match {
        case Some(j) =>
          Try(read[List[CustomLink]](j)) match {
            case Success(list) => e.modState(_.copy(customLinks = list))
            case Failure(error) =>
              Callback(logError(s"Failure parsing custom links: ${error.getMessage}"))
          }
        case None => Callback.empty
      }
    }
    .build

  def activeClass(isActive: Boolean): String =
    if (isActive) "active"
    else ""

  /*
    Make sub menus for children, e.g. zone view is a child of the Zone menu
   */
  def toSubMenu(P: Props, parent: Page): TagMod = {
    // some title => sub menu div
    def titleToMenu(title: String): TagMod =
      <.ul(
        GlobalStyle.Styles.displayBlock,
        ^.className := "nav child_menu",
        <.li(
          ^.className := "active",
          <.a(
            <.i(^.className := "fa fa-eye"),
            title,
            P.router.setOnClick(P.selectedPage)
          )
        )
      )

    // (child, parent)
    (P.selectedPage, parent) match {
      case (_: ToGroupViewPage, _: ToGroupListPage.type) =>
        titleToMenu("View")
      case (_: ToZoneViewPage, _: ToZoneListPage.type) =>
        titleToMenu("View")
      case (_: ToBatchChangeCreatePage.type, _: ToBatchChangeListPage.type) =>
        titleToMenu("Create")
      case (_: ToBatchChangeViewPage, _: ToBatchChangeListPage.type) =>
        titleToMenu("View")
      case _ => TagMod.empty
    }
  }

  /*
    If we have a sub menu we want the parent menu to be highlighted as it was active
   */
  def isActive(P: Props, target: Page): Boolean =
    // (child, parent)
    (P.selectedPage, target) match {
      case _ if target.getClass == P.selectedPage.getClass => true
      case (_: ToGroupViewPage, _: ToGroupListPage.type) => true
      case (_: ToZoneViewPage, _: ToZoneListPage.type) => true
      case (_: ToBatchChangeCreatePage.type, _: ToBatchChangeListPage.type) => true
      case (_: ToBatchChangeViewPage, _: ToBatchChangeListPage.type) => true
      case _ => false
    }

  def mouseEnter(e: ReactEventFromInput): Callback =
    Callback(e.currentTarget.className = "active")

  def mouseExit(e: ReactEventFromInput, isActive: Boolean): Callback =
    if (!isActive) Callback(e.currentTarget.className = "")
    else Callback.empty
}
