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
import vinyldns.v2client.components.Modal
import vinyldns.v2client.models.Group

object CreateGroupModal {
  case class State(group: Group)
  case class Props(close: () => Callback, create: Group => Callback)
  class Backend(bs: BackendScope[Props, State]) {
    def changeName(e: ReactEventFromInput): CallbackTo[Unit] = {
      val name = e.target.value
      bs.modState { s =>
        val g = s.group.copy(name = name)
        s.copy(group = g)
      }
    }

    def changeEmail(e: ReactEventFromInput): CallbackTo[Unit] = {
      val email = e.target.value
      bs.modState { s =>
        val g = s.group.copy(email = email)
        s.copy(group = g)
      }
    }

    def changeDescription(e: ReactEventFromInput): CallbackTo[Unit] = {
      val description = e.target.value
      bs.modState { s =>
        val g = s.group.copy(description = description)
        s.copy(group = g)
      }
    }

    def render(P: Props, S: State): VdomElement =
      Modal(
        Modal.Props("Create Group", P.close),
        <.div(
          ^.className := "modal-body",
          <.div(
            ^.className := "form form-horizontal form-label-left",
            <.div(
              ^.className := "form-group",
              <.label(
                ^.className := "control-label col-md-3 col-sm-3 col-xs-12",
                "Name"
              ),
              <.div(
                ^.className := "col-md-6 col-sm-6 col-xs-12",
                <.input(
                  ^.className := "form-control ",
                  ^.`type` := "text",
                  ^.value := S.group.name,
                  ^.onChange ==> changeName
                )
              )
            ),
            <.div(
              ^.className := "form-group",
              <.label(
                ^.className := "control-label col-md-3 col-sm-3 col-xs-12",
                "Email"
              ),
              <.div(
                ^.className := "col-md-6 col-sm-6 col-xs-12",
                <.input(
                  ^.className := "form-control",
                  ^.`type` := "email",
                  ^.value := S.group.email,
                  ^.onChange ==> changeEmail
                )
              )
            ),
            <.div(
              ^.className := "form-group",
              <.label(
                ^.className := "control-label col-md-3 col-sm-3 col-xs-12",
                "Description"
              ),
              <.div(
                ^.className := "col-md-6 col-sm-6 col-xs-12",
                <.input(
                  ^.className := "form-control",
                  ^.`type` := "text",
                  ^.value := S.group.description,
                  ^.onChange ==> changeDescription
                )
              )
            ),
            <.div(^.className := "ln_solid"),
            <.div(
              ^.className := "form-group",
              <.button(
                ^.`type` := "button",
                ^.className := "btn btn-success pull-right",
                ^.onClick --> P.create(S.group),
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
