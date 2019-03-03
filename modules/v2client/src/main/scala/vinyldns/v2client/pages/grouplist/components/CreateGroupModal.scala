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

package vinyldns.v2client.pages.grouplist.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import upickle.default.write
import vinyldns.v2client.ajax.{PostGroupRoute, Request}
import vinyldns.v2client.components.{InputFieldValidations, Modal, ValidatedInputField}
import vinyldns.v2client.models.{Id, Notification}
import vinyldns.v2client.models.membership.Group
import vinyldns.v2client.ReactApp.loggedInUser

object CreateGroupModal {
  case class State(group: Group)
  case class Props(
      setNotification: Option[Notification] => Callback,
      close: () => Callback,
      refreshGroups: () => Callback)
  class Backend(bs: BackendScope[Props, State]) {
    def createGroup(e: ReactEventFromInput, P: Props, S: State): Callback =
      if (e.target.checkValidity()) {
        e.preventDefaultCB >> {
          val groupWithUserId =
            S.group
              .copy(
                members = Some(Seq(Id(loggedInUser.id))),
                admins = Some(Seq(Id(loggedInUser.id))))
          Request
            .post(PostGroupRoute(), write(groupWithUserId))
            .onComplete { xhr =>
              val alert = P.setNotification(Request.toNotification("creating group", xhr))
              val cleanUp =
                if (!Request.isError(xhr.status))
                  P.close() >> P.refreshGroups()
                else Callback(())
              alert >> cleanUp
            }
            .asCallback
        }
      } else e.preventDefaultCB

    def changeName(value: String): CallbackTo[Unit] =
      bs.modState { s =>
        val g = s.group.copy(name = value)
        s.copy(group = g)
      }

    def changeEmail(value: String): CallbackTo[Unit] =
      bs.modState { s =>
        val g = s.group.copy(email = value)
        s.copy(group = g)
      }

    def changeDescription(value: String): CallbackTo[Unit] =
      bs.modState { s =>
        val g = s.group.copy(description = Some(value))
        s.copy(group = g)
      }

    private val header =
      """
        |Groups simplify setup and access to resources in Vinyl.
        | A Group consists of one or more members,
        | who are registered users of Vinyl.
        | Any member in the group can be designated as a Group Admin, which
        | allows that member full administrative access to the group, including deleting the group.
      """.stripMargin

    def render(P: Props, S: State): VdomElement =
      Modal(
        Modal.Props("Create Group", P.close),
        <.div(
          ^.className := "modal-body",
          <.div(
            ^.className := "panel-header",
            <.p(header)
          ),
          <.form(
            ^.className := "form form-horizontal form-label-left",
            ^.onSubmit ==> (e => createGroup(e, P, S)),
            ValidatedInputField(
              ValidatedInputField.Props(
                changeName,
                label = Some("Name"),
                helpText = Some("Group name. Cannot contain spaces"),
                validations = Some(
                  InputFieldValidations(
                    required = true,
                    maxSize = Some(255),
                    canContainSpaces = false))
              )
            ),
            ValidatedInputField(
              ValidatedInputField.Props(
                changeEmail,
                label = Some("Email"),
                helpText = Some("Group contact email. Preferably a multi user distribution"),
                isEmail = true,
                validations = Some(InputFieldValidations(required = true))
              )
            ),
            ValidatedInputField(
              ValidatedInputField.Props(
                changeDescription,
                label = Some("Description"),
              )
            ),
            <.div(^.className := "ln_solid"),
            <.div(
              ^.className := "form-group",
              <.button(
                ^.`type` := "submit",
                ^.className := "btn btn-success pull-right",
                "Submit"
              ),
              <.button(
                ^.`type` := "button",
                ^.className := "btn btn-default pull-right",
                ^.onClick --> P.close(),
                "Close"
              )
            )
          )
        )
      )
  }

  private val component = ScalaComponent
    .builder[Props]("CreateGroupForm")
    .initialState(State(Group()))
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)
}
