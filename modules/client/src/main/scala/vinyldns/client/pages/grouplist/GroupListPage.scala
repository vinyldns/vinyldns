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
import vinyldns.client.http.{Http, HttpResponse, ListGroupsRoute}
import vinyldns.client.models.Notification
import vinyldns.client.models.membership.GroupList
import vinyldns.client.pages.grouplist.components.{CreateGroupModal, GroupsTable}
import vinyldns.client.css.GlobalStyle
import vinyldns.client.routes.AppRouter.{Page, PropsFromAppRouter}
import vinyldns.client.components.AlertBox.setNotification

object GroupListPage extends PropsFromAppRouter {
  case class State(
      groupsList: Option[GroupList] = None,
      showCreateGroup: Boolean = false,
      notification: Option[Notification] = None)

  private val component = ScalaComponent
    .builder[Props]("GroupPage")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.listGroups(e.props))
    .build

  def apply(page: Page, router: RouterCtl[Page], http: Http): Unmounted[Props, State, Backend] =
    component(Props(page, router, http))

  class Backend(bs: BackendScope[Props, State]) {
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
                    <.button(
                      ^.className := "btn btn-default test-create-group",
                      ^.`type` := "button",
                      ^.onClick --> makeCreateFormVisible,
                      <.span(^.className := "fa fa-plus-square"),
                      "  Create Group"
                    ),
                    <.button(
                      ^.className := "btn btn-default test-refresh-groups",
                      ^.onClick --> listGroups(P),
                      <.span(^.className := "fa fa-refresh"),
                      "  Refresh"),
                    <.div(^.className := "clearfix")
                  )
                ),
                <.div(
                  ^.className := "panel-body",
                  GroupsTable(GroupsTable
                    .Props(P.http, S.groupsList, setNotification, () => listGroups(P), P.router)))
              )
            )
          )
        ),
        createGroupModal(P, S.showCreateGroup)
      )

    def listGroups(P: Props): Callback = {
      val onSuccess = { (_: HttpResponse, parsed: Option[GroupList]) =>
        bs.modState(_.copy(groupsList = parsed))
      }
      val onFailure = { httpResponse: HttpResponse =>
        setNotification(P.http.toNotification("list groups", httpResponse, onlyOnError = true))
      }
      P.http.get(ListGroupsRoute, onSuccess, onFailure)
    }

    def createGroupModal(P: Props, isVisible: Boolean): TagMod =
      if (isVisible)
        CreateGroupModal(
          CreateGroupModal
            .Props(P.http, () => makeCreateFormInvisible, () => listGroups(P)))
      else TagMod.empty

    def makeCreateFormVisible: Callback =
      bs.modState(_.copy(showCreateGroup = true))

    def makeCreateFormInvisible: Callback =
      bs.modState(_.copy(showCreateGroup = false))
  }
}
