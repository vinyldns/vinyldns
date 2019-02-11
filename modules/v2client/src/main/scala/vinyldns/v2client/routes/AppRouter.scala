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
import vinyldns.v2client.components.{Footer, LeftNav, TopNav}
import vinyldns.v2client.models.Menu
import vinyldns.v2client.pages.HomePage
import vinyldns.v2client.pages.OtherPage

object AppRouter {

  sealed trait AppPage

  case object Home extends AppPage
  case object Other extends AppPage

  val menu = Vector(
    Menu("Home", Home),
    Menu("Other", Other)
  )

  val config = RouterConfigDsl[AppPage].buildConfig { dsl =>
    import dsl._
    (staticRoute("Home", Home) ~> render(HomePage())
      | staticRoute("Other", Other) ~> render(OtherPage()))
      .notFound(redirectToPage(Home)(Redirect.Replace))
      .renderWith(layout)
  }

  def layout(c: RouterCtl[AppPage], r: Resolution[AppPage]): VdomTagOf[Div] =
    <.div(
      TopNav(TopNav.Props(menu, r.page, c)),
      LeftNav(LeftNav.Props(menu, r.page, c)),
      r.render(),
      Footer()
    )

  val baseUrl = BaseUrl.fromWindowOrigin / "v2/"

  val router = Router(baseUrl, config)
}
