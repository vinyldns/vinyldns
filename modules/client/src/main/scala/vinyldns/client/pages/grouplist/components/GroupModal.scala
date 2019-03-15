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
import japgolly.scalajs.react.vdom.html_<^._
import upickle.default.write
import vinyldns.client.http.{CreateGroupRoute, Http, HttpResponse, UpdateGroupRoute}
import vinyldns.client.components._
import vinyldns.client.models.membership.{BasicGroupInfo, Group, GroupCreateInfo, Id}
import vinyldns.client.components.AlertBox.setNotification

object GroupModal {
  case class State(group: BasicGroupInfo, isUpdate: Boolean = false)
  case class Props(
      http: Http,
      close: Unit => Callback,
      refreshGroups: Unit => Callback,
      existing: Option[Group] = None)

  val component = ScalaComponent
    .builder[Props]("GroupModal")
    .initialStateFromProps { p =>
      p.existing match {
        case Some(g) =>
          State(g, isUpdate = true)
        case None => State(GroupCreateInfo())
      }
    }
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      Modal(
        Modal.Props(toTitle(S), P.close),
        <.div(
          ^.className := "modal-body",
          <.div(
            ^.className := "panel-header",
            <.p(header)
          ),
          ValidatedForm(
            ValidatedForm.Props(
              "form form-horizontal form-label-left test-create-group-form",
              generateInputFieldProps(S),
              _ => if (S.isUpdate) updateGroup(P, S) else createGroup(P, S)),
            <.div(
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
                  ^.className := "btn btn-default pull-right test-close-create-group",
                  ^.onClick --> P.close(()),
                  "Close"
                )
              )
            )
          )
        )
      )

    def toTitle(S: State): String =
      if (S.isUpdate) s"Update Group ${S.group.asInstanceOf[Group].id}"
      else "Create Group"

    def generateInputFieldProps(S: State): List[ValidatedInputField.Props] =
      List(
        ValidatedInputField.Props(
          changeName,
          inputClass = Some("test-name"),
          label = Some("Group Name"),
          helpText = Some("Group name"),
          initialValue = Some(S.group.name),
          validations =
            Some(InputFieldValidations(required = true, maxSize = Some(255), noSpaces = true))
        ),
        ValidatedInputField.Props(
          changeEmail,
          label = Some("Email"),
          inputClass = Some("test-email"),
          helpText = Some("Group contact email. Preferably a multi user distribution"),
          initialValue = Some(S.group.email),
          typ = InputType.Email,
          validations = Some(InputFieldValidations(required = true))
        ),
        ValidatedInputField.Props(
          changeDescription,
          initialValue = S.group.description,
          inputClass = Some("test-description"),
          label = Some("Description")
        )
      )

    def createGroup(P: Props, S: State): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to create group ${S.group.name}?",
        Callback.lazily {
          val user = P.http.getLoggedInUser()
          val groupWithUserId =
            S.group
              .asInstanceOf[GroupCreateInfo]
              .copy(members = Seq(Id(user.id)), admins = Seq(Id(user.id)))
          val onFailure = { httpResponse: HttpResponse =>
            setNotification(P.http.toNotification("creating group", httpResponse))
          }
          val onSuccess = { (httpResponse: HttpResponse, _: Option[Group]) =>
            setNotification(P.http.toNotification("creating group", httpResponse)) >>
              P.close(()) >>
              P.refreshGroups(())
          }
          P.http.post(CreateGroupRoute, write(groupWithUserId), onSuccess, onFailure)
        }
      )

    def updateGroup(P: Props, S: State): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to update group ${S.group.asInstanceOf[Group].id}?",
        Callback.lazily {
          val updated = S.group.asInstanceOf[Group]
          val onFailure = { httpResponse: HttpResponse =>
            setNotification(P.http.toNotification("updating group", httpResponse))
          }
          val onSuccess = { (httpResponse: HttpResponse, _: Option[Group]) =>
            setNotification(P.http.toNotification("updating group", httpResponse)) >>
              P.close(()) >>
              P.refreshGroups(())
          }
          P.http
            .put(
              UpdateGroupRoute(S.group.asInstanceOf[Group].id),
              write(updated),
              onSuccess,
              onFailure)
        }
      )

    def changeName(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val group = s.group.asInstanceOf[Group]
          val modified = group.copy(name = value)
          s.copy(group = modified)
        } else {
          val group = s.group.asInstanceOf[GroupCreateInfo]
          val modified = group.copy(name = value)
          s.copy(group = modified)
        }
      }

    def changeEmail(value: String): Callback =
      bs.modState { s =>
        if (s.isUpdate) {
          val group = s.group.asInstanceOf[Group]
          val modified = group.copy(email = value)
          s.copy(group = modified)
        } else {
          val group = s.group.asInstanceOf[GroupCreateInfo]
          val modified = group.copy(email = value)
          s.copy(group = modified)
        }
      }

    def changeDescription(value: String): Callback =
      bs.modState { s =>
        val d =
          if (value.isEmpty) None
          else Some(value)

        if (s.isUpdate) {
          val group = s.group.asInstanceOf[Group]
          val modified = group.copy(description = d)
          s.copy(group = modified)
        } else {
          val group = s.group.asInstanceOf[GroupCreateInfo]
          val modified = group.copy(description = d)
          s.copy(group = modified)
        }
      }

    private val header =
      """
        |Groups simplify setup and access to resources in Vinyl.
        | A Group consists of one or more members,
        | who are registered users of Vinyl.
        | Any member in the group can be designated as a Group Admin, which
        | allows that member full administrative access to the group, including deleting the group.
      """.stripMargin
  }
}
