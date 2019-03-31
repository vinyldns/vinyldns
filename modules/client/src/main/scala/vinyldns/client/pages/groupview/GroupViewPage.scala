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

package vinyldns.client.pages.groupview

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^.{^, _}
import vinyldns.client.http._
import vinyldns.client.models.membership.Group
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.css.GlobalStyle
import vinyldns.client.pages.groupview.components.{MembersTable, NewMemberForm}
import vinyldns.client.router.AppRouter.PropsFromAppRouter
import vinyldns.client.router.{Page, ToGroupViewPage}

object GroupViewPage extends PropsFromAppRouter {
  case class State(group: Option[Group] = None)

  val component = ScalaComponent
    .builder[Props]("ViewGroup")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.getGroup(e.props))
    .build

  def apply(page: Page, router: RouterCtl[Page], http: Http): Unmounted[Props, State, Backend] =
    component(Props(page, router, http))

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomNode =
      <.div(
        GlobalStyle.Styles.height100,
        ^.className := "right_col",
        ^.role := "main",
        S.group match {
          case Some(group) =>
            <.div(
              <.div(
                ^.className := "page-title",
                <.div(
                  ^.className := "title_left",
                  <.h3(<.span(^.className := "fa fa-user"), s"""  Group ${group.name}"""),
                  getIdHeader(group),
                  getEmailHeader(group),
                  getDescriptionHeader(group)
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
                        NewMemberForm(NewMemberForm.Props(P.http, group, _ => getGroup(P))),
                        <.div(^.className := "clearfix")
                      ),
                      <.div(
                        ^.className := "panel-body",
                        MembersTable(MembersTable.Props(group, P.http, _ => getGroup(P)))
                      )
                    )
                  )
                )
              )
            )
          case None =>
            <.div(
              ^.className := "page-content-wrap",
              <.div(
                ^.className := "row",
                <.p("Loading group...")
              )
            )
        }
      )

    def getGroup(P: Props): Callback = {
      val groupId = P.page.asInstanceOf[ToGroupViewPage].id
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(P.http.toNotification("getting group", httpResponse, onlyOnError = true))
      }
      val onSuccess = { (_: HttpResponse, parsed: Option[Group]) =>
        bs.modState(_.copy(group = parsed))
      }

      P.http.get(GetGroupRoute(groupId), onSuccess, onFailure)
    }

    def getDescriptionHeader(group: Group): TagMod =
      group.description match {
        case Some(d) => <.h5(s"Description: $d")
        case None => TagMod.empty
      }

    def getIdHeader(group: Group): TagMod = <.h5(s"Id: ${group.id}")

    def getEmailHeader(group: Group): TagMod = <.h5(s"Email: ${group.email}")
  }
}
