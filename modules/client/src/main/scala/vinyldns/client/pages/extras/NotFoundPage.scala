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

package vinyldns.client.pages.extras

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.BaseUrl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.css.GlobalStyle

object NotFoundPage {
  private val component =
    ScalaComponent
      .builder[Unit]("NotFound")
      .render { _ =>
        <.div(
          GlobalStyle.Styles.height100,
          ^.className := "right_col",
          ^.role := "main",
          <.div(^.className := "clearfix"),
          <.div(
            ^.className := "page-content-wrap",
            <.div(
              ^.className := "row",
              <.p(
                ^.textAlign.center,
                <.img(
                  ^.src := (BaseUrl.fromWindowOrigin / "public/images/404.png").value
                ),
                <.b,
                <.h4("Oops! That page doesn't appear to be in our records...")
              )
            )
          )
        )
      }
      .build

  def apply(): Unmounted[Unit, Unit, Unit] = component()
}
