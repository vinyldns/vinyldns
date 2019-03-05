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

package vinyldns.client.pages.group.components

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.component.Scala.BackendScope
import japgolly.scalajs.react.vdom.VdomElement
import upickle.default.{read, write}
import vinyldns.client.ajax.{LookupUserRoute, Request, UpdateGroupRoute}
import vinyldns.client.components.{InputFieldValidations, ValidatedInputField}
import vinyldns.client.css.GlobalStyle
import vinyldns.client.models.{Id, Notification}
import vinyldns.client.models.membership.Group
import vinyldns.client.models.user.User

import scala.util.Try

object NewMemberForm {
  case class State(
      username: String = "",
      isManager: Boolean = false
  )
  case class Props(
      requestHelper: Request,
      groupId: String,
      group: Option[Group],
      setNotification: Option[Notification] => Callback,
      refreshMembers: () => Callback)

  private val component = ScalaComponent
    .builder[Props]("NewGroupMemberForm")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      <.form(
        ^.className := "col-md-10",
        ^.onSubmit ==> (e => lookupUser(e, P, S)),
        <.div(
          ^.className := "col-md-3",
          ValidatedInputField(
            ValidatedInputField.Props(
              (value: String) => bs.modState(_.copy(username = value)),
              labelSize = "",
              inputSize = "",
              placeholder = Some("username"),
              initialValue = Some(S.username),
              validations = Some(
                InputFieldValidations(
                  required = true,
                  canContainSpaces = false
                ))
            )
          )
        ),
        <.div(
          ^.className := "form-group col-md-2",
          <.label(
            ^.className := "check",
            <.input(
              GlobalStyle.styleSheet.cursorPointer,
              ^.`type` := "checkbox",
              ^.checked := S.isManager,
              ^.onChange --> bs.modState(_.copy(isManager = !S.isManager))
            ),
            " Group Manager ",
            <.span(
              GlobalStyle.styleSheet.cursorPointer,
              ^.className := "fa fa-info-circle",
              VdomAttr("data-toggle") := "tooltip",
              ^.title := "Managers can add new members, and edit or delete the Group"
            )
          )
        ),
        <.div(
          ^.className := "form-group col-md-1",
          <.button(
            ^.className := "btn btn-default",
            ^.`type` := "submit",
            ^.className := "btn btn-success",
            "Add Member"
          )
        )
      )

    def addMember(P: Props, S: State, user: Option[User]): Callback =
      (P.group, user) match {
        case (Some(g), Some(u)) =>
          P.requestHelper.withConfirmation(
            s"Are you sure you want to add ${S.username} to the group?",
            Callback.lazily {
              val newMembers = g.members.map(_ ++ Seq(Id(u.id)))
              val newAdmins = if (S.isManager) g.admins.map(_ ++ Seq(Id(u.id))) else g.admins
              val updatedGroup = g.copy(members = newMembers, admins = newAdmins)
              P.requestHelper
                .put(UpdateGroupRoute(P.groupId), write(updatedGroup))
                .onComplete { xhr =>
                  val alert =
                    P.setNotification(
                      P.requestHelper.toNotification(s"adding member ${S.username}", xhr))
                  alert >> P.refreshMembers()
                }
                .asCallback
            }
          )
        case _ => Callback.empty
      }

    def lookupUser(e: ReactEventFromInput, P: Props, S: State): Callback =
      if (!e.target.checkValidity()) e.preventDefaultCB
      else
        e.preventDefaultCB >>
          P.requestHelper
            .get(LookupUserRoute(S.username))
            .onComplete { xhr =>
              val alert = P.setNotification(
                P.requestHelper
                  .toNotification(s"getting user ${S.username}", xhr, onlyOnError = true))
              val user = Try(Option(read[User](xhr.responseText))).getOrElse(None)
              alert >> addMember(P, S, user)
            }
            .asCallback
  }
}
