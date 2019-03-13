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

package vinyldns.client.pages.grouplist.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.components.AlertBox.setNotification
import vinyldns.client.http.{DeleteGroupRoute, Http, HttpResponse, ListGroupsRoute}
import vinyldns.client.models.membership.{Group, GroupList}
import vinyldns.client.routes.AppRouter.{Page, ToGroupViewPage}

object GroupsTable {
  case class Props(http: Http, router: RouterCtl[Page])

  case class State(
      groupsList: Option[GroupList] = None,
      groupNameFilter: Option[String] = None,
      showUpdateGroup: Boolean = false,
      toBeUpdated: Option[Group] = None)

  val component = ScalaComponent
    .builder[Props](displayName = "ListGroupsTable")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.listGroups(e.props, e.state))
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      <.div(
        S.groupsList match {
          case Some(gl) if gl.groups.nonEmpty || S.groupNameFilter.isDefined =>
            <.div(
              <.div(
                ^.className := "panel-heading",
                <.div(
                  ^.className := "btn-group",
                  <.button(
                    ^.className := "btn btn-default test-refresh-groups",
                    ^.onClick --> listGroups(P, S),
                    ^.`type` := "button",
                    <.span(^.className := "fa fa-refresh"),
                    "  Refresh")
                ),
                <.form(
                  ^.className := "pull-right input-group test-search-form",
                  ^.onSubmit ==> { e: ReactEventFromInput =>
                    e.preventDefaultCB >> listGroups(P, S)
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
                    ),
                  )
                ),
              ),
              <.div(^.className := "clearfix"),
              <.div(
                ^.className := "panel-body",
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
                    gl.groups.map(toTableRow(P, S, _)).toTagMod
                  )
                )
              ),
              updateGroupModal(P, S)
            )
          case Some(gl) if gl.groups.isEmpty => <.p("You don't have any groups yet")
          case None => <.p("Loading your groups...")
        }
      )

    def updateGroupModal(P: Props, S: State): TagMod =
      if (S.showUpdateGroup)
        GroupModal(
          GroupModal
            .Props(
              P.http,
              _ => makeUpdateFormInvisible,
              _ => listGroups(P, S),
              existing = S.toBeUpdated))
      else TagMod.empty

    def makeUpdateFormVisible(toBeUpdated: Group): Callback =
      bs.modState(_.copy(toBeUpdated = Some(toBeUpdated), showUpdateGroup = true))

    def makeUpdateFormInvisible: Callback =
      bs.modState(_.copy(showUpdateGroup = false))

    def listGroups(P: Props, S: State): Callback = {
      val onSuccess = { (_: HttpResponse, parsed: Option[GroupList]) =>
        bs.modState(_.copy(groupsList = parsed))
      }
      val onFailure = { httpResponse: HttpResponse =>
        setNotification(P.http.toNotification("list groups", httpResponse, onlyOnError = true))
      }
      P.http.get(ListGroupsRoute(S.groupNameFilter), onSuccess, onFailure)
    }

    def toTableRow(P: Props, S: State, group: Group): TagMod =
      <.tr(
        <.td(^.className := "col-md-3", group.name),
        <.td(^.className := "col-md-3", group.email),
        <.td(^.className := "col-md-3", group.description),
        <.td(
          ^.className := "col-md-3",
          <.div(
            ^.className := "btn-group",
            <.a(
              ^.className := "btn btn-info btn-rounded test-view",
              P.router.setOnClick(ToGroupViewPage(group.id)),
              ^.title := s"View group ${group.name}",
              VdomAttr("data-toggle") := "tooltip",
              <.span(^.className := "fa fa-eye"),
              " View"
            ),
            <.button(
              ^.className := "btn btn-warning btn-rounded test-edit",
              ^.`type` := "button",
              ^.onClick --> makeUpdateFormVisible(group),
              ^.title := s"Edit group ${group.name}",
              VdomAttr("data-toggle") := "tooltip",
              <.span(^.className := "fa fa-edit"),
              " Edit"
            ),
            <.button(
              ^.className := "btn btn-danger btn-rounded test-delete",
              ^.`type` := "button",
              ^.onClick --> deleteGroup(P, S, group),
              ^.title := s"Delete group ${group.name}",
              VdomAttr("data-toggle") := "tooltip",
              <.span(^.className := "fa fa-trash"),
              " Delete"
            )
          )
        )
      )

    def deleteGroup(P: Props, S: State, group: Group): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to delete group ${group.name}?",
        Callback
          .lazily {
            val onSuccess = { (httpResponse: HttpResponse, _: Option[Group]) =>
              setNotification(P.http.toNotification(s"deleting group ${group.name}", httpResponse)) >>
                listGroups(P, S)
            }
            val onFailure = { httpResponse: HttpResponse =>
              setNotification(P.http.toNotification(s"deleting group ${group.name}", httpResponse))
            }
            P.http.delete(DeleteGroupRoute(group.id), onSuccess, onFailure)
          }
      )

    def updateGroupNameFilter(value: String): Callback =
      if (value.isEmpty) bs.modState(_.copy(groupNameFilter = None))
      else bs.modState(_.copy(groupNameFilter = Some(value)))
  }
}
