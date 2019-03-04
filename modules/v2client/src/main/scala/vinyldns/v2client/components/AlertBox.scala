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
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.vdom.VdomElement
import vinyldns.v2client.css.GlobalStyle
import vinyldns.v2client.models.Notification

object AlertBox {
  case class Props(notification: Notification, closeFunction: () => Callback)

  private val component = ScalaComponent
    .builder[Props]("Notify")
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, Unit, Backend] = component(props)

  class Backend {
    def render(props: Props): VdomElement =
      <.div(
        ^.className := "ui-pnotify ui-pnotify-fade-normal ui-pnotify-in ui-pnotify-fade-in ui-pnotify-move",
        GlobalStyle.styleSheet.notifyOuter,
        <.div(
          ^.className := notificationClass(props.notification.isError),
          ^.role := "alert",
          GlobalStyle.styleSheet.notifyInner,
          <.div(
            ^.className := "ui-pnotify-closer pull-right",
            GlobalStyle.styleSheet.cursorPointer,
            ^.onClick --> props.closeFunction(),
            <.span(^.className := "fa fa-remove"),
            "  close"
          ),
          <.h4(
            ^.className := "ui-pnotifiy-title",
            title(props.notification.isError)
          ),
          <.div(
            ^.className := "ui-pnotify-text",
            props.notification.customMessage.getOrElse[String](""),
            <.br,
            props.notification.ajaxResponseMessage.getOrElse[String]("")
          )
        )
      )

    def notificationClass(isError: Boolean): String = {
      val errorOrSuccess = if (isError) "alert-error" else "alert-success"
      s"alert ui-pnotify-container ui-pnotify-shadow $errorOrSuccess"
    }

    def title(isError: Boolean): String =
      if (isError) "Error"
      else "Success"
  }
}
