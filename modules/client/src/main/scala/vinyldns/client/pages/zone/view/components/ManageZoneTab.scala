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

package vinyldns.client.pages.zone.view.components

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.css.GlobalStyle
import vinyldns.client.http.Http
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.client.models.zone.ZoneResponse
import vinyldns.client.pages.zone.list.components.ZoneModal
import vinyldns.client.router.Page
import vinyldns.client.components.JsNative.toReadableTimestamp

object ManageZoneTab {
  case class Props(
      zone: ZoneResponse,
      groupList: GroupListResponse,
      http: Http,
      routerCtl: RouterCtl[Page],
      refreshZone: Unit => Callback)

  case class State(
      showUpdateZone: Boolean = false
  )

  val component = ScalaComponent
    .builder[Props]("ManageZone")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] =
    component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomNode =
      <.div(
        ^.className := "panel",
        <.div(
          ^.className := "panel-body",
          <.table(
            GlobalStyle.Styles.noWrap,
            ^.className := "table table-striped table-sm table-condensed",
            <.tbody(
              <.tr(
                <.th("Email:"),
                <.td(P.zone.email)
              ),
              <.tr(
                <.th("Type:"),
                <.td(if (P.zone.shared) "Shared" else "Private")
              ),
              <.tr(
                <.th("Created:"),
                <.td(toReadableTimestamp(P.zone.created))
              ),
              <.tr(
                <.th("Latest Update:"),
                <.td(toReadableTimestamp(P.zone.updated))
              ),
              <.tr(
                <.th("Latest Sync:"),
                <.td(toReadableTimestamp(P.zone.latestSync))
              ),
              <.tr(
                <.th("DNS Connection:"),
                <.td(
                  P.zone.connection match {
                    case Some(c) => s"${c.keyName} | ${c.primaryServer}"
                    case None => "default"
                  }
                )
              ),
              <.tr(
                <.th("DNS Transfer Connection:"),
                <.td(
                  P.zone.transferConnection match {
                    case Some(c) => s"${c.keyName} | ${c.primaryServer}"
                    case None => "default"
                  }
                )
              )
            )
          ),
          <.button(
            ^.`type` := "button",
            ^.className := "btn btn-primary pull-right test-update",
            ^.onClick --> makeUpdateFormVisible,
            <.span(^.className := "fa fa-edit"),
            " Edit",
          )
        ),
        zoneUpdateModal(P, S)
      )

    def zoneUpdateModal(P: Props, S: State): TagMod =
      if (S.showUpdateZone)
        ZoneModal(
          ZoneModal
            .Props(
              P.http,
              _ => makeUpdateFormInvisible,
              _ => P.refreshZone(()),
              P.groupList,
              Some(P.zone)))
      else TagMod.empty

    def makeUpdateFormVisible: Callback =
      bs.modState(_.copy(showUpdateZone = true))

    def makeUpdateFormInvisible: Callback =
      bs.modState(_.copy(showUpdateZone = false))
  }
}
