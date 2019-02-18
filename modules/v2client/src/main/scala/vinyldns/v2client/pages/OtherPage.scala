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

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.pages.group.CreateGroupForm

object OtherPage {
  val CssSettings = scalacss.devOrProdDefaults
  import CssSettings._

  object Style extends StyleSheet.Inline {
    import dsl._
    val content = style(textAlign.center, fontSize(30.px), minHeight(450.px), paddingTop(40.px))
  }

  val component = {
    ScalaComponent.builder
      .static("HomePage")(<.div(CreateGroupForm()))
      .build
  }

  def apply(): Unmounted[Unit, Unit, Unit] = component()
}
