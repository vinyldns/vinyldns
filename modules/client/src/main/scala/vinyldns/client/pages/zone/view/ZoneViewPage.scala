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

package vinyldns.client.pages.zone.view

import scalacss.ScalaCssReact._
import vinyldns.client.models.zone.ZoneResponse
import vinyldns.client.router.AppRouter.PropsFromAppRouter
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^.{^, _}
import vinyldns.client.css.GlobalStyle
import vinyldns.client.http._
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.client.pages.zone.view.components.{
  ChangeHistoryTab,
  ManageAccessTab,
  ManageRecordSetsTab,
  ManageZoneTab
}
import vinyldns.client.router._
import vinyldns.core.domain.zone.ZoneStatus

object ZoneViewPage extends PropsFromAppRouter {
  case class State(
      zone: Option[ZoneResponse] = None,
      groupList: Option[GroupListResponse] = None,
      backendIds: Option[List[String]] = None)

  private val component = ScalaComponent
    .builder[Props]("ViewZone")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e =>
      e.backend.getZone(e.props) >> e.backend.getGroups(e.props) >>
        e.backend.getBackendIds(e.props))
    .build

  def apply(page: Page, router: RouterCtl[Page], http: Http): Unmounted[Props, State, Backend] =
    component(Props(page, router, http))

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomNode =
      <.div(
        GlobalStyle.Styles.height100,
        ^.className := "right_col",
        ^.role := "main",
        (S.zone, S.groupList, S.backendIds) match {
          case (Some(zone), Some(groupList), Some(backendIds)) =>
            <.div(
              <.div(
                ^.className := "page-title",
                <.div(
                  ^.className := "title_left",
                  <.h3(
                    <.span(^.className := "fa fa-table"),
                    s"""  Zone ${zone.name}"""
                  ),
                  <.h5(GlobalStyle.Styles.keepWhitespace, "Status: ", toStatus(zone.status)),
                  <.h5(s"Id: ${zone.id}"),
                  <.h5(
                    "Admin Group:",
                    <.a(
                      GlobalStyle.Styles.cursorPointer,
                      s""" ${zone.adminGroupName.getOrElse("")}""",
                      P.router.setOnClick(ToGroupViewPage(zone.adminGroupId))
                    )
                  )
                )
              ),
              <.div(^.className := "clearfix"),
              <.div(
                ^.className := "page-content-wrap",
                <.div(
                  ^.className := "panel panel-default panel-tabs",
                  navTabs(P),
                  <.div(
                    ^.className := "panel-body tab-content",
                    <.div(
                      ^.className := "tab-pane active",
                      tabContent(P, zone, groupList, backendIds)
                    )
                  )
                )
              ),
              <.div(^.className := "clearfix")
            )
          case _ =>
            <.div(
              ^.className := "page-content-wrap",
              <.div(
                ^.className := "row",
                <.p("Loading...")
              )
            )
        }
      )

    def navTabs(P: Props): VdomElement = {
      val zoneId = P.page.asInstanceOf[ToZoneViewPage].id

      val recordsActive = <.li(^.className := "active", <.a("Manage Records"))
      val accessActive = <.li(^.className := "active", <.a("Manage Access"))
      val zoneActive = <.li(^.className := "active", <.a("Manage Zone"))
      val changesActive = <.li(^.className := "active", <.a("Change History"))

      val records = <.li(
        GlobalStyle.Styles.cursorPointer,
        <.a("Manage Records"),
        P.router.setOnClick(ToZoneViewRecordsTab(zoneId)))
      val access = <.li(
        GlobalStyle.Styles.cursorPointer,
        <.a("Manage Access"),
        P.router.setOnClick(ToZoneViewAccessTab(zoneId)))
      val zone = <.li(
        GlobalStyle.Styles.cursorPointer,
        <.a("Manage Zone", P.router.setOnClick(ToZoneViewZoneTab(zoneId))))
      val changes = <.li(
        GlobalStyle.Styles.cursorPointer,
        <.a("Change History"),
        P.router.setOnClick(ToZoneViewChangesTab(zoneId)))

      P.page match {
        case _: ToZoneViewRecordsTab =>
          <.ul(
            ^.className := "nav nav-tabs bar_tabs",
            recordsActive,
            access,
            zone,
            changes
          )
        case _: ToZoneViewAccessTab =>
          <.ul(
            ^.className := "nav nav-tabs bar_tabs",
            records,
            accessActive,
            zone,
            changes
          )
        case _: ToZoneViewZoneTab =>
          <.ul(
            ^.className := "nav nav-tabs bar_tabs",
            records,
            access,
            zoneActive,
            changes
          )
        case _ =>
          <.ul(
            ^.className := "nav nav-tabs bar_tabs",
            records,
            access,
            zone,
            changesActive
          )
      }
    }

    def tabContent(
        P: Props,
        zone: ZoneResponse,
        groupList: GroupListResponse,
        backendIds: List[String]): VdomElement =
      P.page match {
        case _: ToZoneViewRecordsTab =>
          ManageRecordSetsTab(ManageRecordSetsTab.Props(zone, groupList, P.http, P.router))
        case _: ToZoneViewAccessTab =>
          ManageAccessTab(ManageAccessTab.Props(zone, groupList, P.http, P.router, _ => getZone(P)))
        case _: ToZoneViewZoneTab =>
          ManageZoneTab(
            ManageZoneTab.Props(zone, groupList, backendIds, P.http, P.router, _ => getZone(P)))
        case _: ToZoneViewChangesTab =>
          ChangeHistoryTab(ChangeHistoryTab.Props(zone, groupList, P.http, P.router))
        case _ =>
          <.div("not implemented")
      }

    def getZone(P: Props): Callback = {
      val zoneId = P.page.asInstanceOf[ToZoneViewPage].id
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(P.http.toNotification("getting zone", httpResponse, onlyOnError = true))
      }
      val onSuccess = { (_: HttpResponse, parsed: Option[ZoneResponse]) =>
        bs.modState(_.copy(zone = parsed))
      }

      P.http.get(GetZoneRoute(zoneId), onSuccess, onFailure)
    }

    def getGroups(P: Props): Callback = {
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(P.http.toNotification("getting groups", httpResponse, onlyOnError = true))
      }
      val onSuccess = { (_: HttpResponse, parsed: Option[GroupListResponse]) =>
        bs.modState(_.copy(groupList = parsed))
      }

      P.http.get(ListGroupsRoute(100), onSuccess, onFailure)
    }

    def getBackendIds(P: Props): Callback = {
      val onSuccess = { (_: HttpResponse, parsed: Option[List[String]]) =>
        bs.modState(_.copy(backendIds = parsed))
      }
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(
          P.http.toNotification("listing backend ids", httpResponse, onlyOnError = true))
      }
      P.http.get(GetBackendIdsRoute, onSuccess, onFailure)
    }

    def toStatus(status: ZoneStatus.ZoneStatus): TagMod =
      status match {
        case ZoneStatus.Active =>
          <.span(^.className := "label label-success", "Active")
        case ZoneStatus.Syncing =>
          <.span(^.className := "label label-warning", "Syncing")
        case ZoneStatus.Deleted =>
          <.span(^.className := "label label-danger", "Deleted")
        case _ => <.span
      }
  }
}
