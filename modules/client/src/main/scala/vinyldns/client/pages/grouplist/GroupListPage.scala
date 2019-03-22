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

package vinyldns.client.pages.grouplist

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.http.Http
import vinyldns.client.pages.grouplist.components.{GroupModal, GroupsTable}
import vinyldns.client.css.GlobalStyle
import vinyldns.client.routes.AppRouter.{Page, PropsFromAppRouter}

object GroupListPage extends PropsFromAppRouter {
  case class State(showCreateGroup: Boolean = false)

  private val component = ScalaComponent
    .builder[Props]("GroupListPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(page: Page, router: RouterCtl[Page], http: Http): Unmounted[Props, State, Backend] =
    component(Props(page, router, http))

  class Backend(bs: BackendScope[Props, State]) {
    val refToTable = Ref.toScalaComponent(GroupsTable.component)

    def render(P: Props, S: State): VdomElement =
      <.div(
        GlobalStyle.styleSheet.height100,
        ^.className := "right_col",
        ^.role := "main",
        <.div(
          ^.className := "page-title",
          <.div(
            ^.className := "title_left",
            <.h3(<.span(^.className := "fa fa-users"), "  Groups"))),
        <.div(^.className := "clearfix"),
        <.div(
          ^.className := "page-content-wrap",
          <.div(
            ^.className := "row",
            <.div(
              ^.className := "col-md-12 col-sm-12 col-xs-12",
              <.div(
                ^.className := "panel panel-default",
                <.div(
                  ^.className := "panel-heading",
                  <.div(
                    ^.className := "btn-group",
                    // create button
                    <.button(
                      ^.className := "btn btn-default test-create-group",
                      ^.`type` := "button",
                      ^.onClick --> makeCreateFormVisible,
                      <.span(^.className := "fa fa-plus-square"),
                      "  Create Group"
                    ),
                    // refresh button
                    <.button(
                      ^.className := "btn btn-default test-refresh-groups",
                      ^.onClick --> { resetPageInfo >> refreshGroupsTable },
                      ^.`type` := "button",
                      <.span(^.className := "fa fa-refresh"),
                      "  Refresh"
                    )
                  ),
                  // search bar
                  <.form(
                    ^.className := "pull-right input-group test-search-form",
                    ^.onSubmit ==> { e: ReactEventFromInput =>
                      e.preventDefaultCB >> resetPageInfo >> refreshGroupsTable
                    },
                    <.div(
                      ^.className := "input-group",
                      <.span(
                        ^.className := "input-group-btn",
                        <.button(
                          ^.className := "btn btn-primary btn-left-round",
                          ^.`type` := "submit",
                          <.span(
                            ^.className := "fa fa-search"
                          )
                        )
                      ),
                      <.input(
                        ^.className := "form-control test-groupNameFilter",
                        ^.placeholder := "Group Name",
                        ^.onChange ==> { e: ReactEventFromInput =>
                          updateGroupNameFilter(e.target.value)
                        }
                      )
                    )
                  )
                ),
                // table
                refToTable.component(GroupsTable.Props(P.http, P.router))
              )
            )
          )
        ),
        createGroupModal(P, S.showCreateGroup)
      )

    def refreshGroupsTable: Callback =
      refToTable.get
        .map { mounted =>
          mounted.backend.listGroups(mounted.props, mounted.state)
        }
        .getOrElse(Callback.empty)
        .runNow()

    def resetPageInfo: Callback =
      refToTable.get
        .map { mounted =>
          mounted.backend.resetPageInfo
        }
        .getOrElse(Callback.empty)
        .runNow()

    def updateGroupNameFilter(value: String): Callback =
      refToTable.get
        .map { mounted =>
          mounted.backend.updateGroupNameFilter(value)
        }
        .getOrElse(Callback.empty)
        .runNow()

    def createGroupModal(P: Props, isVisible: Boolean): TagMod =
      if (isVisible)
        GroupModal(
          GroupModal
            .Props(P.http, _ => makeCreateFormInvisible, _ => refreshGroupsTable))
      else TagMod.empty

    def makeCreateFormVisible: Callback =
      bs.modState(_.copy(showCreateGroup = true))

    def makeCreateFormInvisible: Callback =
      bs.modState(_.copy(showCreateGroup = false))
  }
}
