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
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.css.GlobalStyle.Styles.cursorPointer
import vinyldns.client.routes._

object Breadcrumb {
  case class Props(selectedPage: Page, router: RouterCtl[Page])

  def apply(props: Props): Unmounted[Props, Unit, Unit] = component(props)

  private val component = ScalaComponent
    .builder[Props]("Breadcrumb")
    .render_P { P =>
      <.div(
        ^.className := "right_col",
        <.ul(
          ^.className := "breadcrumb",
          toBreadcrumb(P)
        ),
        <.div(^.className := "clearfix")
      )
    }
    .build

  def toBreadcrumb(P: Props): TagMod =
    P.selectedPage match {
      case _: ToHomePage.type => homeActive
      case _: ToGroupListPage.type =>
        List(home(P), groupsActive).toTagMod
      case _: ToGroupViewPage =>
        List(home(P), groups(P), viewGroupActive).toTagMod
      case _: ToZoneListPage.type =>
        List(home(P), zonesActive).toTagMod
      case _: ToZoneViewPage =>
        List(home(P), zones(P), viewZoneActive).toTagMod
      case _ => TagMod.empty
    }

  val homeActive = <.li(^.key := "home", cursorPointer, ^.className := "active", "Home")
  def home(P: Props): TagMod =
    <.li(^.key := "home", cursorPointer, <.a("Home", P.router.setOnClick(ToHomePage)))

  val groupsActive = <.li(^.key := "groups", cursorPointer, ^.className := "active", "Groups")
  def groups(P: Props): TagMod =
    <.li(^.key := "groups", cursorPointer, <.a("Groups", P.router.setOnClick(ToGroupListPage)))

  val viewGroupActive = <.li(^.key := "viewGroup", cursorPointer, ^.className := "active", "View")

  val zonesActive = <.li(^.key := "zones", cursorPointer, ^.className := "active", "Zones")
  def zones(P: Props): TagMod =
    <.li(^.key := "zones", cursorPointer, <.a("Zones", P.router.setOnClick(ToZoneListPage)))

  val viewZoneActive = <.li(^.key := "viewZone", cursorPointer, ^.className := "active", "View")
}
