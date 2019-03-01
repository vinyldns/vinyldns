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

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.v2client.ajax.{GetGroupMembersRoute, GetGroupRoute, Request}
import vinyldns.v2client.models.membership.{Group, MemberList}
import vinyldns.v2client.pages.MainContainer.PropsFromMain
import vinyldns.v2client.pages.AppPage
import upickle.default.read
import vinyldns.v2client.css.GlobalStyle
import vinyldns.v2client.models.Id
import vinyldns.v2client.models.user.User
import vinyldns.v2client.routes.AppRouter.ToGroupViewPage

import scala.util.Try

object GroupViewPage extends AppPage {
  case class State(group: Option[Group] = None, members: List[User] = List())

  class Backend(bs: BackendScope[PropsFromMain, State]) {
    def getGroup(P: PropsFromMain): Callback = {
      val groupId = P.page.asInstanceOf[ToGroupViewPage].id
      Request
        .get(GetGroupRoute(groupId))
        .onComplete { xhr =>
          val alert =
            P.alerter.set(Request.toNotification("getting group", xhr, onlyOnError = true))
          val group = Try(Option(read[Group](xhr.responseText))).getOrElse(None)
          alert >> getMembers(P, group)
        }
        .asCallback
    }

    def getMembers(P: PropsFromMain, group: Option[Group]): Callback =
      group match {
        case Some(g) =>
          Request
            .get(GetGroupMembersRoute(g.id.getOrElse("")))
            .onComplete { xhr =>
              val alert =
                P.alerter.set(
                  Request.toNotification(
                    s"getting group members for group id ${g.id}",
                    xhr,
                    onlyOnError = true))
              val members = Try(read[MemberList](xhr.responseText).members).getOrElse(List())
              alert >> bs.modState(_.copy(group = group, members = members))
            }
            .asCallback
        case None => Callback(())
      }

    def getDescriptionHeader(group: Group): TagMod =
      group.description match {
        case Some(d) => <.h5(s"Description: $d")
        case None => TagMod.empty
      }

    def getIdHeader(group: Group): TagMod =
      group.id match {
        case Some(id) => <.h5(s"Id: $id")
        case None => TagMod.empty
      }

    def getEmailHeader(group: Group): TagMod =
      <.h5(s"Email: ${group.email}")

    def toName(user: User): String =
      (user.lastName, user.firstName) match {
        case (Some(ln), Some(fn)) => s"$ln, $fn"
        case (Some(ln), None) => ln
        case (None, Some(fn)) => fn
        case (None, None) => ""
      }

    def groupManagerWidget(user: User, S: State): TagMod = {
      val isManager = S.group.exists { g =>
        val admins = g.admins.getOrElse(Seq())
        admins.contains(Id(user.id))
      }
      val hasAccess = isManager || user.isSuper
      <.input(
        GlobalStyle.styleSheet.cursorPointer,
        ^.`type` := "checkbox",
        ^.checked := hasAccess,
        ^.disabled := !hasAccess || S.members.size < 2
      )
    }

    def render(P: PropsFromMain, S: State): VdomNode =
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
              ^.className := "page-content-wrap",
              <.div(
                ^.className := "row",
                <.div(
                  ^.className := "col-md-12 col-sm-12 col-xs-12",
                  <.div(
                    ^.className := "panel panel-default",
                    <.div(
                      ^.className := "panel-body",
                      <.table(
                        ^.className := "table",
                        <.thead(<.tr(
                          <.th("Username"),
                          <.th("Name"),
                          <.th("Email"),
                          <.th(
                            "Group Manager  ",
                            <.span(
                              GlobalStyle.styleSheet.cursorPointer,
                              ^.className := "fa fa-info-circle",
                              VdomAttr("data-toggle") := "tooltip",
                              ^.title := "Managers can and add new members, and edit or delete the Group"
                            )
                          ),
                          <.th("Actions")
                        )),
                        S.group match {
                          case Some(_) =>
                            <.tbody(S.members.map { m =>
                              <.tr(
                                <.td(m.userName),
                                <.td(toName(m)),
                                <.td(m.email),
                                <.td(groupManagerWidget(m, S)),
                                <.td())
                            }.toTagMod)
                          case None => TagMod.empty
                        }
                      )
                    )
                  )
                )
              )
            )
          )
        case None =>
          <.div(
            <.div(
              ^.className := "page-title",
              <.div(
                ^.className := "title_left",
                s"Group with ID ${P.page.asInstanceOf[ToGroupViewPage].id} not found")))
      }
  }

  private val component = ScalaComponent
    .builder[PropsFromMain]("ViewGroup")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.getGroup(e.props))
    .build

  def apply(propsFromMainPage: PropsFromMain): Unmounted[PropsFromMain, State, Backend] =
    component(propsFromMainPage)
}
