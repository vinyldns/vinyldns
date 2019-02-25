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

package vinyldns.v2client.pages

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.css.GlobalStyle
import vinyldns.v2client.pages.group.{CreateGroupModal, GroupsTable}

object OtherPage {
  case class State(showCreateGroup: Boolean)

  class Backend(bs: BackendScope[Unit, State]) {
    def createGroupModal(isVisible: Boolean): TagMod =
      if (isVisible)
        CreateGroupModal(CreateGroupModal.Props(() => makeCreateFormInvisible))
      else <.div()

    def makeCreateFormVisible: Callback =
      bs.modState(_.copy(showCreateGroup = true))

    def makeCreateFormInvisible: Callback =
      bs.modState(_.copy(showCreateGroup = false))

    def render(s: State): VdomElement =
      <.div(
        ^.className := "right_col",
        ^.role := "main",
        GlobalStyle.styleSheet.maxHeight,
        <.div(
          ^.className := "page-title",
          <.div(
            ^.className := "title_left",
            <.h3(<.span(^.className := "fa fa-users"), "  Groups")
          )
        ),
        <.div(^.className := "clearfix"),
        <.div(
          ^.className := "row",
          <.div(
            ^.className := "col-md-12 col-sm-12 col-xs-12",
            <.div(
              ^.className := "x_panel",
              <.div(
                ^.className := "x_title",
                <.button(
                  ^.className := "btn btn-default",
                  ^.`type` := "button",
                  ^.onClick --> makeCreateFormVisible,
                  <.span(^.className := "fa fa-plus-square"),
                  "  Create Group"
                ),
                <.div(^.className := "clearfix")
              ),
              <.div(
                ^.className := "x_content",
                GroupsTable(),
              )
            )
          )
        ),
        createGroupModal(s.showCreateGroup),
      )
  }

  val component = {
    ScalaComponent
      .builder[Unit]("GroupPage")
      .initialState(State(false))
      .renderBackend[Backend]
      .build
  }

  def apply(): Unmounted[Unit, State, Backend] = component()
}
