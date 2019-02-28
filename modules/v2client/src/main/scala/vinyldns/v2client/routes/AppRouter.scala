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
import vinyldns.v2client.pages.grouplist.GroupListPage
import vinyldns.v2client.pages.home.HomePage
import vinyldns.v2client.pages.{AppPage, MainPage}

object AppRouter {

  private val menu = List(
    LeftNav.NavItem("Home", "fa fa-home", HomePage),
    LeftNav.NavItem("Groups", "fa fa-users", GroupListPage)
  )

  private val config = RouterConfigDsl[AppPage].buildConfig { dsl =>
    import dsl._
    (staticRoute("home", HomePage) ~> render(MainPage(HomePage))
      | staticRoute("groups", GroupListPage) ~> render(MainPage(GroupListPage)))
      .notFound(redirectToPage(HomePage)(Redirect.Replace))
      .renderWith(layout)
  }

  private def layout(c: RouterCtl[AppPage], r: Resolution[AppPage]): VdomTagOf[Div] =
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
