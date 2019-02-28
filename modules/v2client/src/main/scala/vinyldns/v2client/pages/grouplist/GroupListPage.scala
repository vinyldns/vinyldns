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

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import upickle.default.{read, write}
import vinyldns.v2client.ajax.{ListGroupsRoute, PostGroupRoute, Request}
import vinyldns.v2client.models._
import vinyldns.v2client.pages.AppPage
import vinyldns.v2client.pages.MainPage.{Alerter, PropsFromMainPage}
import vinyldns.v2client.pages.grouplist.components.{CreateGroupModal, GroupsTable}

import scala.util.Try

object GroupListPage extends AppPage {
  case class State(groupsList: Option[GroupList] = None, showCreateGroup: Boolean = false)

  class Backend(bs: BackendScope[PropsFromMainPage, State]) {
    def listGroups(alerter: Alerter): Callback =
      Request
        .get(ListGroupsRoute())
        .onComplete { xhr =>
          alerter.set(Request.toNotification("list groups", xhr, onlyOnError = true))
          val groupsList = Try(Option(read[GroupList](xhr.responseText))).getOrElse(None)
          bs.modState(_.copy(groupsList = groupsList))
        }
        .asCallback

    def createGroup(e: ReactEventFromInput, group: Group, user: User): Callback =
      if (e.target.checkValidity()) {
        e.preventDefaultCB >> bs.props >>= { P =>
          val groupWithUserId =
            group.copy(members = Some(Seq(Id(user.id))), admins = Some(Seq(Id(user.id))))
          Request
            .post(PostGroupRoute(), write(groupWithUserId))
            .onComplete { xhr =>
              P.alerter.set(Request.toNotification("creating group", xhr))
            }
            .asCallback
        }
      } else Callback(())

    def createGroupModal(isVisible: Boolean, loggedInUser: User): TagMod =
      if (isVisible)
        CreateGroupModal(
          CreateGroupModal.Props(loggedInUser, () => makeCreateFormInvisible, createGroup))
      else <.div()

    def makeCreateFormVisible: Callback =
      bs.modState(_.copy(showCreateGroup = true))

    def makeCreateFormInvisible: Callback =
      bs.modState(_.copy(showCreateGroup = false))

    def render(P: PropsFromMainPage, S: State): VdomElement =
      <.div(
        <.div(
          ^.className := "page-title",
          <.div(
            ^.className := "title_left",
            <.h3(<.span(^.className := "fa fa-users"), "  Groups"))),
        <.div(^.className := "clearfix"),
        <.div(
          ^.className := "row",
          <.div(
            ^.className := "col-md-12 col-sm-12 col-xs-12",
            <.div(
              ^.className := "x_panel",
              <.br,
              <.div(
                ^.className := "x_title",
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
                    ^.onClick --> listGroups(P.alerter),
                    <.span(^.className := "fa fa-refresh"),
                    "  Refresh"),
                  <.div(^.className := "clearfix")
                )
              ),
              <.div(^.className := "x_content", GroupsTable(GroupsTable.Props(S.groupsList)))
            )
          )
        ),
        createGroupModal(S.showCreateGroup, P.loggedInUser)
      )
  }

  private val component = {
    ScalaComponent
      .builder[PropsFromMainPage]("GroupPage")
      .initialState(State())
      .renderBackend[Backend]
      .componentWillMount(e => e.backend.listGroups(e.props.alerter))
      .build
  }

  def apply(props: PropsFromMainPage): Unmounted[PropsFromMainPage, State, Backend] =
    component(props)
}
