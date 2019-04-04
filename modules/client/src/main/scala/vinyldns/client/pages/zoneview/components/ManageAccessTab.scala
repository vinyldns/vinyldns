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

package vinyldns.client.pages.zoneview.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.http.{Http, HttpResponse, ListGroupsRoute}
import vinyldns.client.models.membership.GroupList
import vinyldns.client.models.zone.Zone
import vinyldns.client.router.Page

import scala.util.Try

object ManageAccessTab {
  case class Props(
      zone: Zone,
      http: Http,
      routerCtl: RouterCtl[Page],
      refreshZone: Unit => Callback)
  case class State(groupList: Option[GroupList] = None)

  val component = ScalaComponent
    .builder[Props]("ManageAccessTab")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.listGroups(e.props))
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      <.div(
        S.groupList match {
          case None => <.p("Loading...")
          case _ =>
            <.div(
              <.p("Any user in the Admin Group has full access to update the Zone."),
              <.p(
                "Access Rules are additional fine grained permissions for other users and groups."),
              getSharedInfoMessage(P),
              AclTable(
                AclTable.Props(
                  P.zone,
                  Try(S.groupList.get.groups).getOrElse(List()),
                  P.http,
                  P.routerCtl,
                  _ => P.refreshZone(())
                )
              )
            )
        }
      )

    def getSharedInfoMessage(P: Props): TagMod =
      if (P.zone.shared)
        <.p(
          "This Zone is set to",
          <.strong(" SHARED:"),
          """
            | In addition to rules above, DNS Record Sets will have an "owner group" field.
            | Any user in VinylDNS can create a record and set an owner group for that entry.
            | Users can also set owner groups on unclaimed records.
            | Once an owner group is set other users cannot make updates unless they are in the
            | Admin Group or have an Access Rule.
          """.stripMargin
        )
      else TagMod.empty

    def listGroups(P: Props): Callback = {
      val onSuccess = { (_: HttpResponse, parsed: Option[GroupList]) =>
        bs.modState(_.copy(groupList = parsed))
      }
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(P.http.toNotification("listing groups", httpResponse, onlyOnError = true))
      }
      P.http.get(ListGroupsRoute(), onSuccess, onFailure)
    }
  }
}
