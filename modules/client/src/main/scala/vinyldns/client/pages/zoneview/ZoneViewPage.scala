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

package vinyldns.client.pages.zoneview

import scalacss.ScalaCssReact._
import vinyldns.client.models.zone.Zone
import vinyldns.client.routes.AppRouter.PropsFromAppRouter
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^.{^, _}
import vinyldns.client.css.GlobalStyle
import vinyldns.client.http.{GetZoneRoute, Http, HttpResponse}
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.pages.zoneview.components.RecordsTab
import vinyldns.client.routes._

object ZoneViewPage extends PropsFromAppRouter {
  case class State(zone: Option[Zone] = None)

  private val component = ScalaComponent
    .builder[Props]("ViewZone")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.getZone(e.props))
    .build

  def apply(page: Page, router: RouterCtl[Page], http: Http): Unmounted[Props, State, Backend] =
    component(Props(page, router, http))

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomNode =
      <.div(
        GlobalStyle.Styles.height100,
        ^.className := "right_col",
        ^.role := "main",
        S.zone match {
          case Some(zone) =>
            <.div(
              <.div(
                ^.className := "page-title",
                <.div(
                  ^.className := "title_left",
                  <.h3(<.span(^.className := "fa fa-table"), s"""  Zone ${zone.name}"""),
                  <.h5(s"Id: ${zone.id}")
                )
              ),
              <.div(^.className := "clearfix"),
              <.div(
                ^.className := "page-content-wrap",
                <.div(
                  ^.className := "panel panel-default panel-tabs",
                  navTabs(P),
                  <.div(
                    ^.className := "panel-body tab-content",
                    <.div(
                      ^.className := "tab-pane active",
                      tabContent(P, zone)
                    )
                  )
                )
              ),
              <.div(^.className := "clearfix")
            )
          case None =>
            <.div(
              ^.className := "page-content-wrap",
              <.div(
                ^.className := "row",
                <.p("Loading zone...")
              )
            )
        }
      )

    def navTabs(P: Props): VdomElement = {
      val zoneId = P.page.asInstanceOf[ToZoneViewPage].id

      val recordsActive = <.li(^.className := "active", <.a("Manage Records"))
      val zoneActive = <.li(^.className := "active", <.a("Manage Zone"))
      val changesActive = <.li(^.className := "active", <.a("Change History"))

      val records = <.li(
        GlobalStyle.Styles.cursorPointer,
        <.a("Manage Records"),
        P.router.setOnClick(ToZoneViewRecordsTab(zoneId)))
      val zone =
        <.li(
          GlobalStyle.Styles.cursorPointer,
          <.a("Manage Zone", P.router.setOnClick(ToZoneViewZoneTab(zoneId))))
      val changes = <.li(
        GlobalStyle.Styles.cursorPointer,
        <.a("Change History"),
        P.router.setOnClick(ToZoneViewChangesTab(zoneId)))

      P.page match {
        case _: ToZoneViewRecordsTab =>
          <.ul(
            ^.className := "nav nav-tabs bar_tabs",
            recordsActive,
            zone,
            changes
          )
        case _: ToZoneViewZoneTab =>
          <.ul(
            ^.className := "nav nav-tabs bar_tabs",
            records,
            zoneActive,
            changes
          )
        case _ =>
          <.ul(
            ^.className := "nav nav-tabs bar_tabs",
            records,
            zone,
            changesActive
          )
      }
    }

    def tabContent(P: Props, zone: Zone): VdomElement =
      P.page match {
        case _: ToZoneViewRecordsTab =>
          RecordsTab(RecordsTab.Props(zone, P.http, P.router))
        case _ =>
          <.div("not implemented")
      }

    def getZone(P: Props): Callback = {
      val zoneId = P.page.asInstanceOf[ToZoneViewPage].id
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(P.http.toNotification("getting zone", httpResponse, onlyOnError = true))
      }
      val onSuccess = { (_: HttpResponse, parsed: Option[Zone]) =>
        bs.modState(_.copy(zone = parsed))
      }

      P.http.get(GetZoneRoute(zoneId), onSuccess, onFailure)
    }
  }
}
