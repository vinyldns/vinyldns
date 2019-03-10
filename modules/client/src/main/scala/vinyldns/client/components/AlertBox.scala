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
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.vdom.VdomElement
import vinyldns.client.ReactApp.SUCCESS_ALERT_TIMEOUT_MILLIS
import vinyldns.client.css.GlobalStyle
import vinyldns.client.models.Notification
import vinyldns.client.routes.AppRouter

import scala.scalajs.js.timers.setTimeout

object AlertBox {
  case class State(notification: Option[Notification] = None)

  val component = ScalaComponent
    .builder[Unit]("AlertBox")
    .initialState(State())
    .renderBackend[Backend]
    .build

  class Backend(bs: BackendScope[Unit, State]) {
    def render(S: State): VdomElement =
      S.notification match {
        case Some(n) =>
          <.div(
            ^.className := "ui-pnotify ui-pnotify-fade-normal ui-pnotify-in ui-pnotify-fade-in ui-pnotify-move",
            GlobalStyle.styleSheet.notifyOuter,
            <.div(
              ^.className := notificationClass(n.isError),
              ^.role := "alert",
              GlobalStyle.styleSheet.notifyInner,
              <.div(
                ^.className := "ui-pnotify-closer pull-right",
                GlobalStyle.styleSheet.cursorPointer,
                ^.onClick --> bs.modState(_.copy(notification = None)),
                <.span(^.className := "fa fa-remove"),
                "  close"
              ),
              <.h4(
                ^.className := "ui-pnotifiy-title",
                title(n.isError)
              ),
              <.div(
                ^.className := "ui-pnotify-text",
                n.customMessage.getOrElse[String](""),
                <.br,
                n.ajaxResponseMessage.getOrElse[String]("")
              )
            )
          )
        case None =>
          <.div
      }

    def notificationClass(isError: Boolean): String = {
      val errorOrSuccess = if (isError) "alert-error" else "alert-success"
      s"alert ui-pnotify-container ui-pnotify-shadow $errorOrSuccess"
    }

    def title(isError: Boolean): String =
      if (isError) "Error"
      else "Success"

    def clearNotification: Callback =
      bs.modState(_.copy(notification = None))

    def setNotification(notification: Option[Notification]): Callback =
      notification match {
        case Some(n) if !n.isError =>
          bs.modState(_.copy(notification = notification)) >>
            Callback(setTimeout(SUCCESS_ALERT_TIMEOUT_MILLIS)(clearNotification.runNow()))
        case Some(n) if n.isError => bs.modState(_.copy(notification = notification))
        case None => Callback.empty
      }
  }

  def setNotification(notification: Option[Notification]): Callback =
    AppRouter.alertBoxRef.get
      .map(mounted => mounted.backend.setNotification(notification))
      .getOrElse[Callback](Callback.empty)
      .runNow()

  def clearNotification(): Callback =
    AppRouter.alertBoxRef.get
      .map(mounted => mounted.backend.clearNotification)
      .getOrElse[Callback](Callback.empty)
      .runNow()
}
