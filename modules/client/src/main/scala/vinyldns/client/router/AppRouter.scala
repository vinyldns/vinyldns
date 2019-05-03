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

package vinyldns.client.router

import japgolly.scalajs.react.Ref
import japgolly.scalajs.react.extra.router.{Resolution, RouterConfigDsl, RouterCtl, _}
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.html.Div
import vinyldns.client.components.{AlertBox, Breadcrumb, LeftNav, TopNav}
import vinyldns.client.pages.extras.NotFoundPage
import vinyldns.client.pages.group.view.GroupViewPage
import vinyldns.client.pages.home.HomePage
import vinyldns.client.ReactApp.version
import vinyldns.client.http.{Http, HttpHelper}
import vinyldns.client.pages.batch.create.BatchChangeCreatePage
import vinyldns.client.pages.batch.list.BatchChangeListPage
import vinyldns.client.pages.credentials.ApiCredentialsPage
import vinyldns.client.pages.group.list.GroupListPage
import vinyldns.client.pages.zone.list.ZoneListPage
import vinyldns.client.pages.zone.view.ZoneViewPage

object AppRouter {
  trait PropsFromAppRouter {
    case class Props(page: Page, router: RouterCtl[Page], http: Http)
  }

  val uuidRegex =
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

  private val config = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._
    (
      staticRoute("", ToHomePage) ~>
        renderR(ctl => HomePage(ToHomePage, ctl, HttpHelper))
        | // home
          staticRoute("home", ToHomePage) ~>
            renderR(ctl => HomePage(ToHomePage, ctl, HttpHelper))
        | // 404
          staticRoute("404", ToNotFound) ~>
            render(NotFoundPage())
        | // api credentials
          staticRoute("credentials", ToApiCredentialsPage) ~>
            renderR(ctl => ApiCredentialsPage(ToApiCredentialsPage, ctl, HttpHelper))
        | // group list
          staticRoute("groups", ToGroupListPage) ~>
            renderR(ctl => GroupListPage(ToGroupListPage, ctl, HttpHelper))
        | // group view
          dynamicRouteCT[ToGroupViewPage]("groups" / string(uuidRegex).caseClass[ToGroupViewPage]) ~>
            (p => renderR(ctl => GroupViewPage(p, ctl, HttpHelper)))
        | // zone list
          staticRoute("zones", ToZoneListPage) ~>
            renderR(ctl => ZoneListPage(ToZoneListPage, ctl, HttpHelper))
        | // zone: manage records tab
          dynamicRouteCT[ToZoneViewRecordsTab](("zones" / string(uuidRegex) / "records")
            .caseClass[ToZoneViewRecordsTab]) ~> (p =>
            renderR(ctl => ZoneViewPage(p, ctl, HttpHelper)))
        | // zone: edit access tab
          dynamicRouteCT[ToZoneViewAccessTab](("zones" / string(uuidRegex) / "access")
            .caseClass[ToZoneViewAccessTab]) ~> (p =>
            renderR(ctl => ZoneViewPage(p, ctl, HttpHelper)))
        | // zone: edit zone tab
          dynamicRouteCT[ToZoneViewZoneTab](("zones" / string(uuidRegex) / "edit")
            .caseClass[ToZoneViewZoneTab]) ~> (p =>
            renderR(ctl => ZoneViewPage(p, ctl, HttpHelper)))
        | // zone: change history tab
          dynamicRouteCT[ToZoneViewChangesTab](("zones" / string(uuidRegex) / "changes")
            .caseClass[ToZoneViewChangesTab]) ~> (p =>
            renderR(ctl => ZoneViewPage(p, ctl, HttpHelper)))
        | // batch change list
          staticRoute("zones" / "batchrecordchanges", ToBatchChangeListPage) ~>
            renderR(ctl => BatchChangeListPage(ToBatchChangeListPage, ctl, HttpHelper))
        | // batch change list
          staticRoute("zones" / "batchrecordchanges" / "new", ToBatchChangeCreatePage) ~>
            renderR(ctl => BatchChangeCreatePage(ToBatchChangeCreatePage, ctl, HttpHelper))
    ).notFound(redirectToPage(ToNotFound)(Redirect.Replace))
      .renderWith(layout)
  }

  private val menu = List(
    LeftNav.NavItem("Home", "fa fa-home", ToHomePage),
    LeftNav.NavItem("Zones", "fa fa-table", ToZoneListPage),
    LeftNav.NavItem("Groups", "fa fa-users", ToGroupListPage),
    LeftNav.NavItem("DNS Record Requests", "fa fa-list", ToBatchChangeListPage),
    LeftNav.NavItem("API Credentials", "fa fa-key", ToApiCredentialsPage),
  )

  // used so the alert box addNotification can be static across the app
  val alertBoxRef =
    Ref.toScalaComponent(AlertBox.component)

  private def layout(router: RouterCtl[Page], target: Resolution[Page]): VdomTagOf[Div] =
    <.div(
      ^.className := "nav-md",
      <.div(
        ^.className := "container body",
        <.div(
          ^.className := "main_container",
          TopNav(HttpHelper, router),
          LeftNav(LeftNav.Props(menu, target.page, router)),
          alertBoxRef.component(),
          Breadcrumb(Breadcrumb.Props(target.page, router)),
          target.render()
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
