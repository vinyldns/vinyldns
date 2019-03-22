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
import vinyldns.client.routes.AppRouter.{Page, PropsFromAppRouter, ToZoneViewPage}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^.{^, _}
import vinyldns.client.css.GlobalStyle
import vinyldns.client.http.{GetZoneRoute, Http, HttpResponse}
import vinyldns.client.components.AlertBox.addNotification

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
    def render(S: State): VdomNode =
      <.div(
        GlobalStyle.styleSheet.height100,
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
                  getIdHeader(zone),
                  getEmailHeader(zone)
                )
              ),
              <.div(^.className := "clearfix"),
              <.div(
                ^.className := "page-content-wrap",
                <.div(
                  ^.className := "row",
                  <.div(
                    ^.className := "col-md-12 col-sm-12 col-xs-12"
                  )
                )
              )
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

    def getIdHeader(zone: Zone): TagMod = <.h5(s"Id: ${zone.id}")
    def getEmailHeader(zone: Zone): TagMod = <.h5(s"Email: ${zone.email}")
  }
}
