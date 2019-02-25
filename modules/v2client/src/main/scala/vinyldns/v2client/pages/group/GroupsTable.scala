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

package vinyldns.v2client.pages.group

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.Ajax
import vinyldns.v2client.ReactApp.csrf
import vinyldns.v2client.models.GroupList
import upickle.default._

object GroupsTable {
  val CssSettings = scalacss.devOrProdDefaults

  case class State(groups: Option[GroupList])

  class Backend(bs: BackendScope[Unit, State]) {

    def listGroups: Callback =
      Ajax("GET", "/api/groups")
        .setRequestHeader("Csrf-Token", csrf)
        .send
        .onComplete { xhr =>
          val groupList = read[GroupList](xhr.responseText)
          bs.modState(_.copy(groups = Some(groupList)))
        }
        .asCallback

    def render(state: State): VdomElement =
      <.div(
        <.button(
          ^.onClick --> listGroups,
          ^.className := "btn btn-default",
          <.span(^.className := "fa fa-refresh"),
          "  Refresh"
        ),
        <.table(
          ^.className := "table",
          <.thead(
            <.tr(
              <.th("Name"),
              <.th("Email"),
              <.th("Description")
            )
          ),
          <.tbody(
            state.groups match {
              case Some(groupList) =>
                groupList.groups.map { group =>
                  <.tr(
                    <.td(group.name),
                    <.td(group.email),
                    <.td(group.description)
                  )
                }.toTagMod
              case None => <.tr()
            }
          )
        )
      )
  }

  val listGroupsTable = ScalaComponent
    .builder[Unit](displayName = "ListGroupsTable")
    .initialState(State(None))
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.listGroups)
    .build

  def apply(): Unmounted[Unit, State, Backend] = listGroupsTable()
}
