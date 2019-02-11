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

package vinyldns.v2client.components

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.Reusability
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.models.Menu
import vinyldns.v2client.routes.AppRouter.AppPage

object LeftNav {
  val CssSettings = scalacss.devOrProdDefaults
  import CssSettings._

  object Style extends StyleSheet.Inline {

    import dsl._

    val container = style(
      listStyle := "none",
      padding.`0`,
      borderRight :=! "1px solid rgb(223, 220, 220)"
    )

    val menuItem = styleF.bool { selected =>
      styleS(
        lineHeight(48.px),
        padding :=! "0 25px",
        cursor.pointer,
        textDecoration := "none",
        mixinIfElse(selected)(color.red, fontWeight._500)(
          color.black,
          &.hover(color(c"#555555"), backgroundColor(c"#ecf0f1"))
        )
      )
    }
  }

  case class Props(menus: Vector[Menu], selectedPage: AppPage, ctrl: RouterCtl[AppPage])

  implicit val currentPageReuse = Reusability.by_==[AppPage]
  implicit val propsReuse = Reusability.by((_: Props).selectedPage)

  val component = ScalaComponent
    .builder[Props]("LeftNav")
    .render_P { P =>
      <.ul(
        ^.className := "col-xl-3 col-md-2 col-xs-1",
        Style.container,
        P.menus.toTagMod(
          item =>
            <.li(
              ^.key := item.name,
              Style.menuItem(item.route.getClass == P.selectedPage.getClass),
              item.name,
              P.ctrl.setOnClick(item.route))
        )
      )
    }
    .configure(Reusability.shouldComponentUpdate)
    .build

  def apply(props: Props): Unmounted[Props, Unit, Unit] = component(props)

}
