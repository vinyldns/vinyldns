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

package vinyldns.client.components

import scalacss.ScalaCssReact._
import japgolly.scalajs.react.CtorType.ChildArg
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.component.Scala.Unmounted
import vinyldns.client.css.GlobalStyle

object Modal {
  case class Props(title: String, close: Unit => Callback)

  private val component = ScalaComponent
    .builder[Props]("Modal")
    .render_PC { (props: Props, propsChildren: PropsChildren) =>
      <.div(
        <.div(
          ^.className := "modal fade in modal-backdrop",
          GlobalStyle.styleSheet.overrideDisplay
        ),
        <.div(
          GlobalStyle.styleSheet.overrideDisplay,
          ^.className := "modal fade in",
          ^.role := "dialog",
          <.div(
            ^.className := "modal-dialog modal-lg",
            <.div(
              ^.className := "modal-content",
              <.div(
                ^.className := "modal-header",
                <.button(
                  ^.className := "close",
                  ^.`type` := "button",
                  ^.onClick --> props.close(()),
                  <.span("x")
                ),
                <.h4(
                  ^.className := "modal-title",
                  props.title
                )
              ),
              propsChildren
            )
          )
        )
      )
    }
    .build

  def apply(props: Props, children: ChildArg): Unmounted[Props, Unit, Unit] =
    component(props)(children)
}
