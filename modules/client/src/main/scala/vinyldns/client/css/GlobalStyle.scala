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

package vinyldns.client.css

object GlobalStyle {

  val CssSettings = scalacss.devOrProdDefaults
  import CssSettings._

  object Styles extends StyleSheet.Inline {
    import dsl._
    // These are additive, and you can plop them in any html tag to inherit the class,
    // as long as the file has "import scalacss.ScalaCssReact._"

    val height100 = style(height :=! "100%")
    val width100 = style(width :=! "100%")

    val displayBlock = style(display.block.important)
    val cursorPointer = style(cursor.pointer)

    val alertBox = style(
      right :=! "36px",
      position.absolute
    )
    val notifyOuter = style(
      width :=! "300px",
      cursor.auto,
      wordWrap.breakWord,
      position.relative,
      zIndex :=! "10000"
    )
    val notifyInner = style(minHeight :=! "16px")

    val positionFixed = style(position.fixed)

    val overflow = style(overflowY.scroll)

    val keepWhitespace = style(whiteSpace.pre)

    val noop = style()
  }
}
