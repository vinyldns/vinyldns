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

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.http.Http
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.client.models.zone.ZoneResponse
import vinyldns.client.router.Page

object ChangeHistoryTab {
  case class Props(
      zone: ZoneResponse,
      groupList: GroupListResponse,
      http: Http,
      routerCtl: RouterCtl[Page])

  val component = ScalaComponent
    .builder[Props]("ChangeHistoryTab")
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, Unit, Backend] = component(props)

  class Backend {
    def render(P: Props): VdomElement =
      <.div(
        RecordSetChangeTable(RecordSetChangeTable.Props(P.zone, P.groupList, P.http, P.routerCtl))
      )
  }
}
