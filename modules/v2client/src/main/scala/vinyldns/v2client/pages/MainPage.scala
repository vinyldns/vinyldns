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
import vinyldns.v2client.models.Notification
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{CtorType, _}
import japgolly.scalajs.react.component.Scala.{Component, Unmounted}
import vinyldns.v2client.components.Notify
import vinyldns.v2client.css.GlobalStyle
import vinyldns.v2client.pages.MainPage.PropsFromMainPage

trait AppPage {
  def apply(propsFromMainPage: PropsFromMainPage): Unmounted[PropsFromMainPage, _, _]
}

object MainPage {
  case class State(notification: Option[Notification] = None)
  case class Alerter(set: Option[Notification] => Callback)
  case class Props(childPage: AppPage)
  case class PropsFromMainPage(alerter: Alerter)

  class Backend(bs: BackendScope[Props, State]) {
    def clearNotification: Callback =
      bs.modState(_.copy(notification = None))
    def setNotification(notification: Option[Notification]): Callback =
      bs.modState(_.copy(notification = notification))

    def render(P: Props, S: State): VdomElement =
      <.div(
        GlobalStyle.styleSheet.fullViewHeight,
        ^.className := "right_col",
        ^.role := "main",
        S.notification match {
          case Some(n) => Notify(Notify.Props(n, () => clearNotification))
          case None => <.div
        },
        P.childPage(PropsFromMainPage(Alerter(setNotification)))
      )
  }

  private val component = ScalaComponent
    .builder[Props]("MainPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(childPage: AppPage): Unmounted[Props, State, Backend] = component(Props(childPage))
}
