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

package vinyldns.client.routes

import japgolly.scalajs.react.Ref
import japgolly.scalajs.react.extra.router.{Resolution, RouterConfigDsl, RouterCtl, _}
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.html.Div
import vinyldns.client.components.{AlertBox, Breadcrumb, LeftNav, TopNav}
import vinyldns.client.pages.extras.NotFoundPage
import vinyldns.client.pages.groupview.GroupViewPage
import vinyldns.client.pages.home.HomePage
import vinyldns.client.ReactApp.version
import vinyldns.client.http.{Http, HttpHelper}
import vinyldns.client.pages.grouplist.GroupListPage
import vinyldns.client.pages.zonelist.ZoneListPage

object AppRouter {
  trait PropsFromAppRouter {
    case class Props(page: Page, router: RouterCtl[Page], http: Http)
  }

  sealed trait Page
  final object ToHomePage extends Page
  final object ToNotFound extends Page
  final object ToGroupListPage extends Page
  final case class ToGroupViewPage(id: String) extends Page
  final object ToZoneListPage extends Page

  private val config = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._
    (
      staticRoute("", ToHomePage) ~>
        renderR(ctl => HomePage(ToHomePage, ctl, HttpHelper))
        |
          staticRoute("home", ToHomePage) ~>
            renderR(ctl => HomePage(ToHomePage, ctl, HttpHelper))
        |
          staticRoute("404", ToNotFound) ~>
            render(NotFoundPage())
        |
          staticRoute("groups", ToGroupListPage) ~>
            renderR(ctl => GroupListPage(ToGroupListPage, ctl, HttpHelper))
        |
          dynamicRouteCT[ToGroupViewPage]("groups" / string("[^ ]+").caseClass[ToGroupViewPage]) ~>
            (p => renderR(ctl => GroupViewPage(p, ctl, HttpHelper)))
        |
          staticRoute("zones", ToZoneListPage) ~>
            renderR(ctl => ZoneListPage(ToZoneListPage, ctl, HttpHelper))
    ).notFound(redirectToPage(ToNotFound)(Redirect.Replace))
      .renderWith(layout)
  }

  private val menu = List(
    LeftNav.NavItem("Home", "fa fa-home", ToHomePage),
    LeftNav.NavItem("Zones", "fa fa-table", ToZoneListPage),
    LeftNav.NavItem("Groups", "fa fa-users", ToGroupListPage)
  )

  val alertBoxRef =
    Ref.toScalaComponent(AlertBox.component)

  private def layout(router: RouterCtl[Page], resolution: Resolution[Page]): VdomTagOf[Div] =
    <.div(
      ^.className := "nav-md",
      <.div(
        ^.className := "container body",
        <.div(
          ^.className := "main_container",
          TopNav(HttpHelper),
          LeftNav(LeftNav.Props(menu, resolution.page, router)),
          alertBoxRef.component(),
          Breadcrumb(Breadcrumb.Props(resolution.page, router)),
          resolution.render()
        ),
        <.footer(
          <.p(
            ^.className := "main-footer-text text-right",
            "VinylDNS",
            version.map(v => s" version $v"),
            <.br,
            <.a(
              ^.href := "https://github.com/vinyldns",
              "Made with ",
              <.i(^.className := "fa fa-heart"),
              " on Github"
            )
          )
        )
      )
    )

  val baseUrl = BaseUrl.fromWindowOrigin / "v2/"

  val router = Router(baseUrl, config)
}
