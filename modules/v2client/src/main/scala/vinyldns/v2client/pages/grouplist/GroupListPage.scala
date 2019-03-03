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

package vinyldns.v2client.pages.grouplist

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.ajax.{ListGroupsRoute, RequestHelper}
import vinyldns.v2client.models.Notification
import vinyldns.v2client.models.membership.GroupList
import vinyldns.v2client.pages.grouplist.components.{CreateGroupModal, GroupsTable}
import vinyldns.v2client.ReactApp.SUCCESS_ALERT_TIMEOUT_MILLIS
import vinyldns.v2client.components.AlertBox
import vinyldns.v2client.css.GlobalStyle
import vinyldns.v2client.routes.AppRouter.{Page, PropsFromAppRouter}

import scala.scalajs.js.timers.setTimeout

object GroupListPage extends PropsFromAppRouter {
  case class State(
      groupsList: Option[GroupList] = None,
      showCreateGroup: Boolean = false,
      notification: Option[Notification] = None)

  class Backend(bs: BackendScope[Props, State]) {
    def clearNotification: Callback =
      bs.modState(_.copy(notification = None))

    def setNotification(notification: Option[Notification]): Callback =
      notification match {
        case Some(n) if !n.isError =>
          bs.modState(_.copy(notification = notification)) >>
            Callback(setTimeout(SUCCESS_ALERT_TIMEOUT_MILLIS)(clearNotification.runNow()))
        case Some(n) if n.isError => bs.modState(_.copy(notification = notification))
        case None => Callback(())
      }

    def listGroups: Callback =
      RequestHelper
        .get(ListGroupsRoute)
        .onComplete { xhr =>
          val alert =
            setNotification(RequestHelper.toNotification("list groups", xhr, onlyOnError = true))
          val groupsList = ListGroupsRoute.parse(xhr)
          alert >> bs.modState(_.copy(groupsList = groupsList))
        }
        .asCallback

    def createGroupModal(isVisible: Boolean): TagMod =
      if (isVisible)
        CreateGroupModal(
          CreateGroupModal
            .Props(setNotification, () => makeCreateFormInvisible, () => listGroups))
      else TagMod.empty

    def makeCreateFormVisible: Callback =
      bs.modState(_.copy(showCreateGroup = true))

    def makeCreateFormInvisible: Callback =
      bs.modState(_.copy(showCreateGroup = false))

    def render(P: Props, S: State): VdomElement =
      <.div(
        GlobalStyle.styleSheet.height100,
        ^.className := "right_col",
        ^.role := "main",
        S.notification match {
          case Some(n) => AlertBox(AlertBox.Props(n, () => clearNotification))
          case None => TagMod.empty
        },
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
                      ^.className := "btn btn-default",
                      ^.`type` := "button",
                      ^.onClick --> makeCreateFormVisible,
                      <.span(^.className := "fa fa-plus-square"),
                      "  Create Group"),
                    <.button(
                      ^.className := "btn btn-default",
                      ^.onClick --> listGroups,
                      <.span(^.className := "fa fa-refresh"),
                      "  Refresh"),
                    <.div(^.className := "clearfix")
                  )
                ),
                <.div(
                  ^.className := "panel-body",
                  GroupsTable(
                    GroupsTable.Props(S.groupsList, setNotification, () => listGroups, P.router)))
              )
            )
          )
        ),
        createGroupModal(S.showCreateGroup)
      )
  }

  private val component = ScalaComponent
    .builder[Props]("GroupPage")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.listGroups)
    .build

  def apply(page: Page, router: RouterCtl[Page]): Unmounted[Props, State, Backend] =
    component(Props(page, router))
}
