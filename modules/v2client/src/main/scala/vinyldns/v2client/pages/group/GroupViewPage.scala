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

package vinyldns.v2client.pages.group

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.ajax.{Request, getGroupRoute}
import vinyldns.v2client.models.Group
import vinyldns.v2client.pages.MainPage.PropsFromMainPage
import vinyldns.v2client.pages.AppPage
import upickle.default.read

import scala.util.Try

object GroupViewPage extends AppPage {
  case class State(group: Option[Group] = None)

  class Backend(bs: BackendScope[PropsFromMainPage, State]) {
    def getGroupId(argsFromPath: List[String]): String =
      if (argsFromPath.isEmpty) ""
      else argsFromPath.head

    def getGroup(P: PropsFromMainPage): Callback = {
      val groupId = getGroupId(P.argsFromPath)
      Request
        .get(getGroupRoute(groupId))
        .onComplete { xhr =>
          val alert =
            P.alerter.set(Request.toNotification("getting group", xhr, onlyOnError = true))
          val group = Try(Option(read[Group](xhr.responseText))).getOrElse(None)
          alert >> bs.modState(_.copy(group = group))
        }
        .asCallback
    }

    def getDescriptionHeader(group: Group): TagMod =
      if (!group.description.isEmpty) <.h5(s"Description: ${group.description}")
      else TagMod.empty

    def getIdHeader(group: Group): TagMod =
      group.id match {
        case Some(id) => <.h5(s"Id: $id")
        case None => TagMod.empty
      }

    def getEmailHeader(group: Group): TagMod =
      <.h5(s"Email: ${group.email}")

    def render(P: PropsFromMainPage, S: State): VdomNode =
      S.group match {
        case Some(group) =>
          <.div(
            <.div(
              ^.className := "page-title",
              <.div(
                ^.className := "title_left",
                <.h3(<.span(^.className := "fa fa-user"), s"""  Group "${group.name}""""),
                getIdHeader(group),
                getEmailHeader(group),
                getDescriptionHeader(group)
              )
            ),
            <.div(^.className := "clearfix"),
            <.div(
              ^.className := "row",
              <.div(
                )
            )
          )
        case None =>
          <.div(
            <.div(
              ^.className := "page-title",
              <.div(
                ^.className := "title_left",
                s"Group with ID ${getGroupId(P.argsFromPath)} not found"
              )
            )
          )
      }
  }

  private val component = ScalaComponent
    .builder[PropsFromMainPage]("ViewGroup")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.getGroup(e.props))
    .build

  def apply(propsFromMainPage: PropsFromMainPage): Unmounted[PropsFromMainPage, State, Backend] =
    component(propsFromMainPage)
}
