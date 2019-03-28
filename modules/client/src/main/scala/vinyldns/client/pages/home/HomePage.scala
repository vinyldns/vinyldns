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

package vinyldns.client.pages.home

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.http.Http
import vinyldns.client.css.GlobalStyle
import vinyldns.client.routes.AppRouter.PropsFromAppRouter
import vinyldns.client.routes.Page

object HomePage extends PropsFromAppRouter {
  private val component =
    ScalaComponent
      .builder[Props]("HomePage")
      .render_P { _ =>
        <.div(
          GlobalStyle.Styles.height100,
          ^.className := "right_col",
          ^.role := "main",
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

  def apply(page: Page, router: RouterCtl[Page], http: Http): Unmounted[Props, Unit, Unit] =
    component(Props(page, router, http))
}
