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
import vinyldns.client.http.Http
import vinyldns.client.models.membership.GroupList
import vinyldns.client.models.zone.Zone
import vinyldns.client.router.Page

object ManageAccessTab {
  case class Props(
      zone: Zone,
      groupList: GroupList,
      http: Http,
      routerCtl: RouterCtl[Page],
      refreshZone: Unit => Callback)

  val component = ScalaComponent
    .builder[Props]("ManageAccessTab")
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, Unit, Backend] = component(props)

  class Backend {
    def render(P: Props): VdomElement =
      <.div(
        <.p("Any user in the Admin Group has full access to update the Zone."),
        <.p("Access Rules are additional fine grained permissions for other users and groups."),
        getSharedInfoMessage(P),
        AclTable(
          AclTable.Props(
            P.zone,
            P.groupList.groups,
            P.http,
            P.routerCtl,
            _ => P.refreshZone(())
          )
        )
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
  }
}
