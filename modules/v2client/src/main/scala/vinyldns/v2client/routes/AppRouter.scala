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

package vinyldns.v2client.routes

import japgolly.scalajs.react.extra.router.{Resolution, RouterConfigDsl, RouterCtl, _}
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.html.Div
import vinyldns.v2client.components.{LeftNav, TopNav}
import vinyldns.v2client.pages.group.GroupViewPage
import vinyldns.v2client.pages.grouplist.GroupListPage
import vinyldns.v2client.pages.home.HomePage
import vinyldns.v2client.pages.MainPage

object AppRouter {
  sealed trait Page
  final case object ToHomePage extends Page
  final case object ToGroupListPage extends Page
  final case class ToGroupViewPage(id: String) extends Page

  private val config = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._
    (
      staticRoute("home", ToHomePage) ~>
        renderR(ctl => MainPage(HomePage, ctl))
        |
          staticRoute("groups", ToGroupListPage) ~>
            renderR(ctl => MainPage(GroupListPage, ctl))
        |
          dynamicRouteCT[ToGroupViewPage]("groups" / string("[^ ]+").caseClass[ToGroupViewPage]) ~>
            (g => renderR(ctl => MainPage(GroupViewPage, ctl, List(g.id))))
    ).notFound(redirectToPage(ToHomePage)(Redirect.Replace))
      .renderWith(layout)
  }

  private val menu = List(
    LeftNav.NavItem("Home", "fa fa-home", ToHomePage),
    LeftNav.NavItem("Groups", "fa fa-users", ToGroupListPage)
  )

  private def layout(c: RouterCtl[Page], r: Resolution[Page]): VdomTagOf[Div] =
    <.div(
      ^.className := "nav-md",
      <.div(
        ^.className := "container body",
        <.div(
          ^.className := "main_container",
          TopNav(),
          LeftNav(LeftNav.Props(menu, r.page, c)),
          r.render()
        )
      )
    )

  private val baseUrl = BaseUrl.fromWindowOrigin / "v2/"

  val router = Router(baseUrl, config)
}
