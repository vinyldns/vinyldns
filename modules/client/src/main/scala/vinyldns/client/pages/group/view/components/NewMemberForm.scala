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
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.component.Scala.BackendScope
import japgolly.scalajs.react.vdom.VdomElement
import upickle.default.write
import vinyldns.client.http.{Http, HttpResponse, LookupUserRoute, UpdateGroupRoute}
import vinyldns.client.css.GlobalStyle
import vinyldns.client.models.membership.{GroupResponse, Id, UserResponse}
import vinyldns.client.components.AlertBox.addNotification

object NewMemberForm {
  case class State(
      username: String = "",
      isManager: Boolean = false
  )
  case class Props(http: Http, group: GroupResponse, refreshGroup: Unit => Callback)

  val component = ScalaComponent
    .builder[Props]("NewGroupMemberForm")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      <.form(
        ^.className := "form-inline",
        ^.onSubmit ==> { e: ReactEventFromInput =>
          e.preventDefaultCB >> lookupUser(P, S)
        },
        <.div(
          ^.className := "col-md-8",
          <.div(
            ^.className := "form-group",
            <.div(
              ^.className := "input-group",
              <.input(
                ^.className := "form-control test-new-member-username",
                ^.placeholder := "Username",
                ^.required := true,
                ^.onChange ==> { e: ReactEventFromInput =>
                  val username = e.target.value
                  bs.modState(_.copy(username = username))
                }
              )
            )
          ),
          <.div(
            ^.className := "form-group",
            GlobalStyle.Styles.padLeft10,
            <.label(
              ^.className := "check",
              <.input(
                GlobalStyle.Styles.cursorPointer,
                ^.className := "test-new-member-manager",
                ^.`type` := "checkbox",
                ^.checked := S.isManager,
                ^.onChange --> bs.modState(_.copy(isManager = !S.isManager))
              ),
              " Group Manager ",
              <.span(
                GlobalStyle.Styles.cursorPointer,
                ^.className := "fa fa-info-circle",
                VdomAttr("data-toggle") := "tooltip",
                ^.title := "Add this member as a Manager. Managers can add new members, and edit or delete the Group"
              )
            )
          ),
          <.div(
            ^.className := "form-group",
            GlobalStyle.Styles.padLeft10,
            <.div(
              ^.className := "input-group",
              <.button(
                ^.className := "btn btn-sm btn-default",
                ^.`type` := "submit",
                ^.className := "btn btn-success",
                "Add Member"
              )
            )
          )
        )
      )

    def addMember(P: Props, S: State, user: Option[UserResponse]): Callback =
      user match {
        case Some(u) =>
          P.http.withConfirmation(
            s"Are you sure you want to add ${S.username} to the group?",
            Callback
              .lazily {
                val newMembers = P.group.members ++ Seq(Id(u.id))
                val newAdmins = if (S.isManager) P.group.admins ++ Seq(Id(u.id)) else P.group.admins
                val updatedGroup = P.group.copy(members = newMembers, admins = newAdmins)

                val onSuccess = { (httpResponse: HttpResponse, _: Option[GroupResponse]) =>
                  addNotification(
                    P.http.toNotification(s"adding member ${S.username}", httpResponse)) >>
                    P.refreshGroup(())
                }

                val onError = { httpResponse: HttpResponse =>
                  addNotification(
                    P.http.toNotification(s"adding member ${S.username}", httpResponse))
                }

                P.http
                  .put(UpdateGroupRoute(P.group.id), write(updatedGroup), onSuccess, onError)
              }
          )
        case _ => Callback.empty
      }

    def lookupUser(P: Props, S: State): Callback = {
      val onSuccess = { (_: HttpResponse, response: Option[UserResponse]) =>
        addMember(P, S, response)
      }
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(
          P.http
            .toNotification(s"getting user ${S.username}", httpResponse, onlyOnError = true))
      }
      P.http.get(LookupUserRoute(S.username), onSuccess, onFailure)
    }
  }
}
