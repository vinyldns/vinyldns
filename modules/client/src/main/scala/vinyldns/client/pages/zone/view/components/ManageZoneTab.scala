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
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.css.GlobalStyle
import vinyldns.client.http.{DeleteZoneRoute, Http, HttpResponse}
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.client.models.zone.ZoneResponse
import vinyldns.client.pages.zone.list.components.ZoneModal
import vinyldns.client.router.{Page, ToZoneListPage}
import vinyldns.client.components.JsNative.toReadableTimestamp

object ManageZoneTab {
  case class Props(
      zone: ZoneResponse,
      groupList: GroupListResponse,
      backendIds: List[String],
      http: Http,
      routerCtl: RouterCtl[Page],
      refreshZone: Unit => Callback,
      canEdit: Boolean)

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
                <.th("Backend ID:"),
                <.td(
                  s"""${P.zone.backendId.getOrElse("")}"""
                )
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
          <.div(
            ^.className := "btn-group pull-right",
            <.button(
              ^.`type` := "button",
              ^.className := "btn btn-warning test-update",
              ^.onClick --> makeUpdateFormVisible,
              ^.disabled := !P.canEdit,
              <.span(^.className := "fa fa-edit"),
              " Edit"
            ),
            <.button(
              ^.className := "btn btn-danger btn-rounded test-abandon",
              ^.`type` := "button",
              ^.onClick --> deleteZone(P),
              ^.disabled := !P.canEdit,
              ^.title := s"Abandon zone ${P.zone.name}",
              VdomAttr("data-toggle") := "tooltip",
              <.span(^.className := "fa fa-trash"),
              " Abandon"
            )
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
              P.backendIds,
              Some(P.zone)))
      else TagMod.empty

    def deleteZone(P: Props): Callback =
      P.http.withConfirmation(
        s"""
           |Are you sure you want to abandon zone ${P.zone.name}? You can re-connect to the zone at a later date.
           |
           |Abandoning a zone does not delete any of its DNS records, the zone will still exist in DNS.
           """.stripMargin,
        Callback
          .lazily {
            val onSuccess = { (httpResponse: HttpResponse, _: Option[ZoneResponse]) =>
              addNotification(P.http.toNotification(s"deleting zone ${P.zone.name}", httpResponse)) >>
                P.routerCtl.set(ToZoneListPage)
            }
            val onFailure = { httpResponse: HttpResponse =>
              addNotification(P.http.toNotification(s"deleting zone ${P.zone.name}", httpResponse))
            }
            P.http.delete(DeleteZoneRoute(P.zone.id), onSuccess, onFailure)
          }
      )

    def makeUpdateFormVisible: Callback =
      bs.modState(_.copy(showUpdateZone = true))

    def makeUpdateFormInvisible: Callback =
      bs.modState(_.copy(showUpdateZone = false))
  }
}
