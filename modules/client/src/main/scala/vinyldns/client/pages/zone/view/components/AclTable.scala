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

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.http._
import vinyldns.client.models.zone.{ACLRule, Rules, ZoneResponse}
import vinyldns.client.router.{Page, ToGroupViewPage}
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.components.JsNative._
import upickle.default.write
import vinyldns.client.css.GlobalStyle
import vinyldns.client.models.membership.GroupResponse

object AclTable {
  case class Props(
      zone: ZoneResponse,
      groups: List[GroupResponse],
      http: Http,
      routerCtl: RouterCtl[Page],
      refreshZone: Unit => Callback)
  case class State(
      showAclCreateModal: Boolean = false,
      showAclUpdateModal: Boolean = false,
      toBeUpdated: Option[(ACLRule, Int)] = None)

  val component = ScalaComponent
    .builder[Props]("AclTable")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      <.div(
        <.div(
          ^.className := "panel panel-default",
          <.div(
            ^.className := "panel-heading",
            <.h3(^.className := "panel-title", "Zone Access Rules"),
            <.br,
            // create ACL button
            <.div(
              ^.className := "btn-group",
              <.button(
                ^.className := "btn btn-default test-create-acl",
                ^.`type` := "button",
                ^.onClick --> makeCreateAclModalVisible,
                <.span(^.className := "fa fa-plus-square"),
                "  Create Access Rule"
              ),
              // refresh button
              <.button(
                ^.className := "btn btn-default test-refresh-zone",
                ^.`type` := "button",
                ^.onClick --> P.refreshZone(()),
                <.span(^.className := "fa fa-refresh"),
                "  Refresh"
              )
            )
          ),
          <.div(
            ^.className := "panel-body",
            P.zone.acl.rules match {
              case empty if empty.isEmpty => <.p("No rules found")
              case rl =>
                <.div(
                  <.table(
                    ^.className := "table",
                    <.thead(
                      <.tr(
                        <.th("User/Group"),
                        <.th("Access Level"),
                        <.th("Record Types"),
                        <.th("Record Mask"),
                        <.th("Description"),
                        <.th("Actions")
                      )
                    ),
                    <.tbody(
                      rl.zipWithIndex.map { case (r, i) => toTableRow(P, r, i) }.toTagMod
                    )
                  )
                )
            }
          )
        ),
        createAclModal(P, S),
        updateAclModal(P, S)
      )

    def toTableRow(P: Props, rule: ACLRule, index: Int): TagMod =
      <.tr(
        ^.key := index,
        <.td(toDisplayName(P, rule)),
        <.td(ACLRule.toAccessLevelDisplay(rule.accessLevel)),
        <.td(
          if (rule.recordTypes.nonEmpty)
            <.ul(
              rule.recordTypes.zipWithIndex.map {
                case (t, i) =>
                  <.li(^.key := i, t.toString)
              }.toTagMod
            )
          else <.p("All")
        ),
        <.td(s"""${rule.recordMask.getOrElse("")}"""),
        <.td(s"""${rule.description.getOrElse("")}"""),
        <.td(
          <.div(
            ^.className := "btn-group",
            <.button(
              ^.className := "btn btn-info btn-rounded test-edit",
              ^.`type` := "button",
              ^.onClick --> makeUpdateAclModalVisible(rule, index),
              <.span(^.className := "fa fa-edit"),
              " Update"
            ),
            <.button(
              ^.className := "btn btn-danger btn-rounded test-delete",
              ^.`type` := "button",
              <.span(^.className := "fa fa-trash"),
              ^.onClick --> deleteAclRule(P, rule, index),
              " Delete"
            )
          )
        )
      )

    def toDisplayName(P: Props, rule: ACLRule): TagMod =
      (rule.userId, rule.groupId, rule.displayName) match {
        case (None, Some(groupId), Some(name)) =>
          <.a(
            GlobalStyle.Styles.cursorPointer,
            s"Group $name",
            P.routerCtl.setOnClick(ToGroupViewPage(groupId))
          )
        case (Some(_), None, Some(name)) =>
          <.p(s"User $name")
        case _ => <.p("Error: Malformed Access Rule")
      }

    def deleteAclRule(P: Props, rule: ACLRule, index: Int): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to delete zone access rule ${rule.displayName.getOrElse("")}",
        Callback.lazily {
          val updatedRuleList = P.zone.acl.rules.patch(index, Nil, 1)
          val updatedZone = P.zone.copy(acl = Rules(updatedRuleList))

          val alertMessage = s"deleting access rule ${rule.displayName.getOrElse("")}"
          val onSuccess = { (httpResponse: HttpResponse, _: Option[ZoneResponse]) =>
            addNotification(P.http.toNotification(alertMessage, httpResponse)) >>
              withDelay(HALF_SECOND_IN_MILLIS, P.refreshZone(()))
          }
          val onFailure = { httpResponse: HttpResponse =>
            addNotification(P.http.toNotification(alertMessage, httpResponse))
          }
          P.http.put(UpdateZoneRoute(P.zone.id), write(updatedZone), onSuccess, onFailure)
        }
      )

    def createAclModal(P: Props, S: State): TagMod =
      if (S.showAclCreateModal)
        AclModal(
          AclModal.Props(
            P.zone,
            P.http,
            P.groups,
            _ => makeCreateAclModalInvisible,
            _ => P.refreshZone(())
          )
        )
      else TagMod.empty

    def makeCreateAclModalVisible: Callback =
      bs.modState(_.copy(showAclCreateModal = true))

    def makeCreateAclModalInvisible: Callback =
      bs.modState(_.copy(showAclCreateModal = false))

    def updateAclModal(P: Props, S: State): TagMod =
      if (S.showAclUpdateModal)
        AclModal(
          AclModal.Props(
            P.zone,
            P.http,
            P.groups,
            _ => makeUpdateAclModalInvisible,
            _ => P.refreshZone(()),
            S.toBeUpdated
          )
        )
      else TagMod.empty

    def makeUpdateAclModalVisible(toBeUpdated: ACLRule, index: Int): Callback =
      bs.modState(_.copy(toBeUpdated = Some(toBeUpdated, index), showAclUpdateModal = true))

    def makeUpdateAclModalInvisible: Callback =
      bs.modState(_.copy(showAclUpdateModal = false))
  }
}
