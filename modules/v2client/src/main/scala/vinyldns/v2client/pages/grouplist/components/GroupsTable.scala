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

package vinyldns.v2client.pages.grouplist.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.models.GroupList
import vinyldns.v2client.routes.AppRouter.{Page, ToGroupViewPage}

object GroupsTable {
  case class Props(groupsList: Option[GroupList], router: RouterCtl[Page])

  class Backend {
    def render(P: Props): VdomElement =
      <.div(
        P.groupsList match {
          case Some(gl) if gl.groups.nonEmpty =>
            <.table(
              ^.className := "table",
              <.thead(
                <.tr(
                  <.th("Name"),
                  <.th("Email"),
                  <.th("Description"),
                  <.th("Actions")
                )
              ),
              <.tbody(
                gl.groups.map { group =>
                  <.tr(
                    <.td(group.name),
                    <.td(group.email),
                    <.td(group.description),
                    <.td(
                      <.div(
                        ^.className := "table-form-group",
                        <.a(
                          ^.className := "btn btn-info btn-rounded",
                          P.router.setOnClick(ToGroupViewPage(group.id.get)),
                          "View"
                        )
                      )
                    )
                  )
                }.toTagMod
              )
            )
          case Some(gl) if gl.groups.isEmpty => <.p("You don't have any groups yet")
          case None => <.p
        }
      )
  }

  private val listGroupsTable = ScalaComponent
    .builder[Props](displayName = "ListGroupsTable")
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, Unit, Backend] = listGroupsTable(props)
}
