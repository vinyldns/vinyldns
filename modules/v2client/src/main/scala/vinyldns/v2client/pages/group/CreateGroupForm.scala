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

package vinyldns.v2client.pages.group

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.models.Group
import japgolly.scalajs.react.extra.Ajax
import org.scalajs.dom.document
import upickle.default._

object CreateGroupForm {
  val CssSettings = scalacss.devOrProdDefaults
  import CssSettings._

  object Style extends StyleSheet.Inline {
    import dsl._
    val content = style(textAlign.center, fontSize(12.px), minHeight(450.px), paddingTop(40.px))
  }

  case class State(group: Group)

  class Backend(bs: BackendScope[Unit, State]) {
    val csrf: String = document.getElementById("csrf").getAttribute("content")

    def createGroup(e: ReactEventFromInput): Callback = {
      def request(state: State): Callback =
        Ajax("POST", "/api/groups").setRequestContentTypeJson
          .setRequestHeader("Csrf-Token", csrf)
          .send(write(state.group))
          .asCallback
          .void

      e.preventDefaultCB >> bs.state >>= request
    }

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

    def render(s: State): VdomElement =
      <.div(
        <.h3("Create Group"),
        <.form(
          <.label("name: "),
          <.input(^.value := s.group.name, ^.onChange ==> changeName, ^.required := true),
          <.br,
          <.label("email: "),
          <.input(^.value := s.group.email, ^.onChange ==> changeEmail, ^.required := true),
          <.br,
          <.label("description: "),
          <.input(^.value := s.group.description, ^.onChange ==> changeDescription),
          <.br,
          <.button("Submit"),
          ^.onSubmit ==> createGroup
        )
      )
  }

  val createGroupForm = ScalaComponent
    .builder[Unit]("Other")
    .initialState(State(Group("", "", "")))
    .renderBackend[Backend]
    .build

  def apply(): Unmounted[Unit, State, Backend] = createGroupForm()
}
