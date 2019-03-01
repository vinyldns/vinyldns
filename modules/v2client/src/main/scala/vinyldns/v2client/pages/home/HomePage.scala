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

package vinyldns.v2client.pages.home

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.pages.AppPage
import vinyldns.v2client.pages.MainContainer.PropsFromMain

object HomePage extends AppPage {
  private val component =
    ScalaComponent
      .builder[PropsFromMain]("HomePage")
      .render_P { _ =>
        <.div(
          <.div(
            ^.className := "page-title",
            <.div(
              ^.className := "title_left",
              <.h3(<.span(^.className := "fa fa-home"), "  Home")
            )
          ),
          <.div(^.className := "clearfix"),
          <.div(
            ^.className := "row",
            <.div(
              ^.className := "col-md-12 col-sm-12 col-xs-12",
              <.div(
                ^.className := "x_panel"
              )
            )
          )
        )
      }
      .build

  def apply(props: PropsFromMain): Unmounted[PropsFromMain, Unit, Unit] = component(props)
}
