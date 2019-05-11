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

import java.util.UUID

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.vdom.VdomElement
import vinyldns.client.css.GlobalStyle
import vinyldns.client.router.AppRouter
import vinyldns.client.components.JsNative._

object AlertBox {
  case class Notification(
      customMessage: Option[String] = None,
      ajaxResponseMessage: Option[String] = None,
      isError: Boolean = false
  )

  case class State(notifications: Map[String, Notification] = Map.empty)

  val component = ScalaComponent
    .builder[Unit]("AlertBox")
    .initialState(State())
    .renderBackend[Backend]
    .build

  class Backend(bs: BackendScope[Unit, State]) {
    def render(S: State): VdomElement =
      <.div(
        GlobalStyle.Styles.alertBox,
        S.notifications.map {
          case (key, n) =>
            <.div(
              ^.key := key,
              ^.className := "ui-pnotify ui-pnotify-fade-normal ui-pnotify-in ui-pnotify-fade-in ui-pnotify-move",
              GlobalStyle.Styles.notifyOuter,
              <.div(
                ^.className := notificationClass(n.isError),
                ^.role := "alert",
                GlobalStyle.Styles.notifyInner,
                <.div(
                  ^.className := "ui-pnotify-closer pull-right",
                  GlobalStyle.Styles.cursorPointer,
                  ^.onClick --> bs.modState { s =>
                    s.copy(notifications = s.notifications - key)
                  },
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
        }.toTagMod
      )

    def notificationClass(isError: Boolean): String = {
      val errorOrSuccess = if (isError) "alert-error" else "alert-success"
      s"alert ui-pnotify-container ui-pnotify-shadow $errorOrSuccess"
    }

    def title(isError: Boolean): String =
      if (isError) "Error"
      else "Success"

    def removeNotification(key: String): Callback =
      bs.modState(s => s.copy(notifications = s.notifications - key))

    def addNotification(notification: Option[Notification]): Callback =
      notification match {
        case Some(n) if !n.isError =>
          val key = UUID.randomUUID().toString
          val entry = Map(key -> n)
          bs.modState(s => s.copy(notifications = s.notifications ++ entry)) >>
            withDelay(FIVE_SECONDS_IN_MILLIS, removeNotification(key))

        case Some(n) if n.isError =>
          val key = UUID.randomUUID().toString
          val entry = Map(key -> n)
          bs.modState(s => s.copy(notifications = s.notifications ++ entry))

        case None => Callback.empty
      }

  }

  def addNotification(notification: Option[Notification]): Callback =
    AppRouter.alertBoxRef.get
      .map(mounted => mounted.backend.addNotification(notification))
      .getOrElse[Callback](Callback.empty)
      .runNow()
}
