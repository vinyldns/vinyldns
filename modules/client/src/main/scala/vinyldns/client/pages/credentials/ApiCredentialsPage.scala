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

package vinyldns.client.pages.credentials

import scalacss.ScalaCssReact._
import vinyldns.client.router.AppRouter.PropsFromAppRouter
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^.{^, _}
import vinyldns.client.css.GlobalStyle
import vinyldns.client.http.{Http, HttpResponse, RegenerateCredentialsRoute}
import vinyldns.client.router.Page
import vinyldns.client.components.AlertBox.addNotification
import org.scalajs.dom

object ApiCredentialsPage extends PropsFromAppRouter {
  val component = ScalaComponent
    .builder[Props]("Credentials")
    .renderBackend[Backend]
    .build

  def apply(page: Page, router: RouterCtl[Page], http: Http): Unmounted[Props, _, Backend] =
    component(Props(page, router, http))

  class Backend {
    def render(P: Props): VdomNode =
      <.div(
        GlobalStyle.Styles.height100,
        ^.className := "right_col",
        ^.role := "main",
        <.div(
          <.div(
            ^.className := "page-title",
            <.div(
              ^.className := "title_left",
              <.h3(<.span(^.className := "fa fa-key"), "  API Credentials")
            )
          ),
          <.div(^.className := "clearfix"),
          <.div(
            ^.className := "page-content-wrap",
            <.div(
              ^.className := "row",
              <.div(
                ^.className := "col-md-12 col-sm-12 col-xs-12",
                <.div(
                  ^.className := "panel panel-default",
                  <.div(
                    ^.className := "panel-heading",
                    "Download",
                    <.div(^.className := "clearfix")
                  ),
                  <.div(
                    ^.className := "panel-body",
                    <.p(
                      """
                        |API Credentials can be used to make requests directly to the VinylDNS API.
                      """.stripMargin
                    ),
                    <.button(
                      ^.`type` := "button",
                      <.a(
                        ^.href := s"/download-creds-file/${P.http.getLoggedInUser().userName}-vinyldns-credentials.csv",
                        <.span(^.className := "fa fa-save"),
                        "  Download"
                      )
                    )
                  )
                ),
                <.div(
                  ^.className := "panel panel-default",
                  <.div(
                    ^.className := "panel-heading",
                    "Regenerate",
                    <.div(^.className := "clearfix")
                  ),
                  <.div(
                    ^.className := "panel-body",
                    <.p(regenerateWarning),
                    <.button(
                      ^.className := "test-regenerate",
                      ^.onClick --> regenerateCredentials(P),
                      ^.`type` := "button",
                      <.span(^.className := "fa fa-refresh"),
                      "  Regenerate"
                    )
                  )
                )
              )
            )
          )
        )
      )

    def regenerateCredentials(P: Props): Callback =
      P.http.withConfirmation(
        regenerateWarning,
        Callback.lazily {
          val onFailure = { httpResponse: HttpResponse =>
            addNotification(P.http.toNotification("regenerating credentials", httpResponse))
          }
          val onSuccess = { (_: HttpResponse, _: Option[Unit]) =>
            Callback(dom.window.location.reload())
          }

          P.http.post(RegenerateCredentialsRoute, "", onSuccess, onFailure)
        }
      )

    private val regenerateWarning =
      """
        |Warning: changing your API credentials will break any existing tools and scripts using
        | your current credentials. You must update your credentials anywhere that you use them outside
        | of this web client.
      """.stripMargin
  }
}
