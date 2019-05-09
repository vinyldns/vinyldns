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

package vinyldns.client.pages.group.view.components

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.{^, _}
import upickle.default.write
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.css.GlobalStyle
import vinyldns.client.http.{GetGroupMembersRoute, Http, HttpResponse, UpdateGroupRoute}
import vinyldns.client.models.membership.{GroupResponse, Id, MemberListResponse, UserResponse}

object MembersTable {

  case class Props(group: GroupResponse, http: Http, refreshGroup: Unit => Callback)

  case class State(memberList: Option[MemberListResponse] = None)

  val component = ScalaComponent
    .builder[Props]("MembersTable")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.getMembers(e.props))
    .componentWillReceiveProps(e => e.backend.getMembers(e.nextProps))
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      S.memberList match {
        case Some(ml) if ml.members.isEmpty =>
          <.p("No group members found")
        case None =>
          <.p("Loading group members...")
        case Some(ml) =>
          <.table(
            ^.className := "table",
            <.thead(
              <.tr(
                <.th("Username"),
                <.th("Name"),
                <.th("Email"),
                <.th(
                  "Group Manager  ",
                  <.span(
                    GlobalStyle.Styles.cursorPointer,
                    ^.className := "fa fa-info-circle",
                    VdomAttr("data-toggle") := "tooltip",
                    ^.title := "Managers can add new members, and edit or delete the Group"
                  )
                ),
                <.th("Actions")
              )),
            <.tbody(ml.members.map { m =>
              <.tr(
                <.td(m.userName),
                <.td(toName(m)),
                <.td(m.email),
                <.td(groupManagerWidget(P, m)),
                <.td(
                  ^.className := "table-form-group",
                  <.button(
                    ^.className := s"btn btn-danger btn-rounded test-delete-${m.userName}",
                    ^.`type` := "button",
                    ^.onClick --> deleteMember(P, m),
                    ^.disabled := !GroupResponse.canEdit(P.group.admins, P.http.getLoggedInUser()),
                    ^.title := s"Remove ${m.userName} from group",
                    VdomAttr("data-toggle") := "tooltip",
                    <.span(^.className := "fa fa-trash"),
                    "  Remove"
                  )
                )
              )
            }.toTagMod)
          )
      }

    def toName(user: UserResponse): String =
      (user.lastName, user.firstName) match {
        case (Some(ln), Some(fn)) => s"$ln, $fn"
        case (Some(ln), None) => ln
        case (None, Some(fn)) => fn
        case (None, None) => ""
      }

    def groupManagerWidget(P: Props, user: UserResponse): TagMod = {
      val canUpdate = GroupResponse.canEdit(P.group.admins, P.http.getLoggedInUser())
      val isManagerAlready = P.group.admins.contains(Id(user.id))

      def toggleFunction: Callback =
        if (isManagerAlready)
          removeGroupAdmin(P, user)
        else addGroupAdmin(P, user)

      <.input(
        GlobalStyle.Styles.cursorPointer,
        ^.className := s"test-manager-widget-${user.userName}",
        ^.`type` := "checkbox",
        ^.checked := isManagerAlready,
        ^.onChange --> toggleFunction,
        ^.disabled := !canUpdate,
        ^.title := s"Toggle manager status for ${user.userName}",
        VdomAttr("data-toggle") := "tooltip"
      )
    }

    def getMembers(P: Props): Callback = {
      val groupId = P.group.id
      val onError = { httpResponse: HttpResponse =>
        addNotification(
          P.http
            .toNotification(
              s"getting group members for group id $groupId",
              httpResponse,
              onlyOnError = true))
      }
      val onSuccess = { (_: HttpResponse, parsed: Option[MemberListResponse]) =>
        bs.modState(_.copy(memberList = parsed))
      }
      P.http.get(GetGroupMembersRoute(groupId), onSuccess, onError)
    }

    def deleteMember(P: Props, user: UserResponse): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to remove member ${user.userName}",
        Callback
          .lazily {

            val newMembers = P.group.members.filter(id => id.id != user.id)
            val newAdmins = P.group.admins.filter(id => id.id != user.id)
            val updatedGroup = P.group.copy(members = newMembers, admins = newAdmins)

            val onSuccess = { (httpResponse: HttpResponse, _: Option[GroupResponse]) =>
              addNotification(P.http.toNotification(s"deleting member ${user.id}", httpResponse)) >>
                P.refreshGroup(())
            }
            val onFailure = { httpResponse: HttpResponse =>
              addNotification(P.http.toNotification(s"deleting member ${user.id}", httpResponse))
            }
            P.http
              .put(UpdateGroupRoute(P.group.id), write(updatedGroup), onSuccess, onFailure)
          }
      )

    def removeGroupAdmin(P: Props, user: UserResponse): Callback =
      P.http.withConfirmation(
        s"Are you sure you no longer want ${user.userName} to be a Group Manager?",
        Callback
          .lazily {
            val newAdmins = P.group.admins.filter(id => id.id != user.id)
            val updatedGroup = P.group.copy(admins = newAdmins)

            val onSuccess = { (httpResponse: HttpResponse, _: Option[GroupResponse]) =>
              addNotification(P.http.toNotification(s"removing manager ${user.id}", httpResponse)) >>
                P.refreshGroup(())
            }
            val onFailure = { httpResponse: HttpResponse =>
              addNotification(P.http.toNotification(s"removing manager ${user.id}", httpResponse))
            }

            P.http
              .put(UpdateGroupRoute(P.group.id), write(updatedGroup), onSuccess, onFailure)
          }
      )

    def addGroupAdmin(P: Props, user: UserResponse): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to make ${user.userName} a Group Manager?",
        Callback.lazily {
          val newAdmins = P.group.admins ++ Seq(Id(user.id))
          val updatedGroup = P.group.copy(admins = newAdmins)

          val onSuccess = { (httpResponse: HttpResponse, _: Option[GroupResponse]) =>
            addNotification(P.http.toNotification(s"adding manager ${user.id}", httpResponse)) >>
              P.refreshGroup(())
          }
          val onFailure = { httpResponse: HttpResponse =>
            addNotification(P.http.toNotification(s"adding manager ${user.id}", httpResponse))
          }

          P.http.put(UpdateGroupRoute(P.group.id), write(updatedGroup), onSuccess, onFailure)
        }
      )
  }
}
