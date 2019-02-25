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
import japgolly.scalajs.react.extra.Reusability
import japgolly.scalajs.react.extra.router.{BaseUrl, RouterCtl}
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.css.GlobalStyle
import vinyldns.v2client.models.Menu
import vinyldns.v2client.routes.AppRouter.AppPage

object LeftNav {
  case class Props(menus: Vector[Menu], selectedPage: AppPage, ctrl: RouterCtl[AppPage])

  implicit val currentPageReuse = Reusability.by_==[AppPage]
  implicit val propsReuse = Reusability.by((_: Props).selectedPage)

  def activeClass(isActive: Boolean): String =
    if (isActive) "active"
    else ""

  val component = ScalaComponent
    .builder[Props]("LeftNav")
    .render_P { P =>
      <.div(
        ^.className := "col-md-3 left_col",
        <.div(
          ^.className := "left_col scroll-view",
          GlobalStyle.styleSheet.maxWidth,
          <.div(^.className := "clearfix"),
          <.div(
            ^.className := "main_menu_side hidden-print main_menu",
            ^.id := "sidebar-menu",
            <.div(
              ^.className := "menu_section active",
              <.ul(
                ^.className := "nav side-menu",
                P.menus.toTagMod(
                  item =>
                    <.li(
                      ^.className := activeClass(item.route.getClass == P.selectedPage.getClass),
                      ^.key := item.name,
                      <.a(
                        <.i(^.className := item.faClassName),
                        item.name,
                        <.span(^.className := "fa fa-chevron-right"),
                        P.ctrl.setOnClick(item.route)
                      )
                  )
                ),
                <.li(
                  ^.key := "logout",
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
    .configure(Reusability.shouldComponentUpdate)
    .build

  def apply(props: Props): Unmounted[Props, Unit, Unit] = component(props)

}
