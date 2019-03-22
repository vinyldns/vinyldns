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

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.http.{DeleteGroupRoute, Http, HttpResponse, ListGroupsRoute}
import vinyldns.client.models.membership.{Group, GroupList}
import vinyldns.client.routes.AppRouter.{Page, ToGroupViewPage}
import vinyldns.client.components.JsNative._
import vinyldns.client.css.GlobalStyle
import vinyldns.client.models.Pagination

import scala.util.Try

object GroupsTable {
  case class Props(http: Http, router: RouterCtl[Page])

  case class State(
      groupsList: Option[GroupList] = None,
      groupNameFilter: Option[String] = None,
      showUpdateGroup: Boolean = false,
      toBeUpdated: Option[Group] = None,
      pagination: Pagination[String] = Pagination(),
      maxItems: Int = 100)

  val component = ScalaComponent
    .builder[Props]("ListGroupsTable")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.listGroups(e.props, e.state))
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      <.div(
        S.groupsList match {
          case Some(gl)
              if gl.groups.nonEmpty || gl.groupNameFilter.isDefined || S.pagination.pageNumber != 1 =>
            <.div(
              <.div(
                ^.className := "panel-heading",
                // items per page
                <.span(
                  <.label(
                    GlobalStyle.styleSheet.keepWhitespace,
                    ^.className := "control-label",
                    "Items per page:  "),
                  <.select(
                    ^.onChange ==> { e: ReactEventFromInput =>
                      val maxItems = Try(e.target.value.toInt).getOrElse(100)
                      bs.modState(
                        _.copy(maxItems = maxItems),
                        resetPageInfo >>
                          bs.state >>= { s =>
                          listGroups(P, s)
                        })
                    },
                    List(100, 50, 25, 5, 1).map { o =>
                      <.option(^.key := o, ^.selected := S.maxItems == o, o)
                    }.toTagMod,
                  )
                ),
                <.span(
                  // paginate
                  ^.className := "btn-group pull-right",
                  <.button(
                    ^.className := "btn btn-round btn-default test-previous-page",
                    ^.onClick --> previousPage(P),
                    ^.`type` := "button",
                    ^.disabled := S.pagination.pageNumber <= 1,
                    <.span(
                      ^.className := "fa fa-arrow-left"
                    ),
                    if (S.pagination.pageNumber > 1) s"  Page ${S.pagination.pageNumber - 1}"
                    else TagMod.empty
                  ),
                  <.button(
                    ^.className := "btn btn-round btn-default test-next-page",
                    ^.onClick --> nextPage(P, S),
                    ^.`type` := "button",
                    ^.disabled := gl.nextId.isEmpty,
                    s"Page ${S.pagination.pageNumber + 1}  ",
                    <.span(
                      ^.className := "fa fa-arrow-right"
                    )
                  )
                )
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

    def listGroups(P: Props, S: State, startFrom: Option[String] = None): Callback = {
      val onSuccess = { (_: HttpResponse, parsed: Option[GroupList]) =>
        bs.modState(_.copy(groupsList = parsed))
      }
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(P.http.toNotification("list groups", httpResponse, onlyOnError = true))
      }
      P.http.get(ListGroupsRoute(S.maxItems, S.groupNameFilter, startFrom), onSuccess, onFailure)
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
              addNotification(P.http.toNotification(s"deleting group ${group.name}", httpResponse)) >>
                withDelay(HALF_SECOND_IN_MILLIS, listGroups(P, S))
            }
            val onFailure = { httpResponse: HttpResponse =>
              addNotification(P.http.toNotification(s"deleting group ${group.name}", httpResponse))
            }
            P.http.delete(DeleteGroupRoute(group.id), onSuccess, onFailure)
          }
      )

    def updateGroupNameFilter(value: String): Callback =
      if (value.isEmpty) bs.modState(_.copy(groupNameFilter = None))
      else bs.modState(_.copy(groupNameFilter = Some(value)))

    def resetPageInfo: Callback =
      bs.modState(s => s.copy(pagination = Pagination()))

    def nextPage(P: Props, S: State): Callback =
      S.groupsList
        .map { gl =>
          bs.modState({ s =>
            s.copy(pagination = s.pagination.next(gl.startFrom))
          }, bs.state >>= { s =>
            listGroups(P, s, gl.nextId)
          })
        }
        .getOrElse(Callback.empty)

    def previousPage(P: Props): Callback =
      bs.modState(
        { s =>
          s.copy(pagination = s.pagination.previous())
        },
        bs.state >>= { s =>
          listGroups(P, s, s.pagination.popped)
        }
      )
  }
}
