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

package vinyldns.client.pages.group

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^.{^, _}
import vinyldns.client.http._
import vinyldns.client.models.membership.{Group, MemberList}
import upickle.default.write
import vinyldns.client.components.AlertBox.setNotification
import vinyldns.client.css.GlobalStyle
import vinyldns.client.models.{Id, Notification}
import vinyldns.client.models.user.User
import vinyldns.client.pages.group.components.NewMemberForm
import vinyldns.client.routes.AppRouter.{Page, PropsFromAppRouter, ToGroupViewPage}

object GroupViewPage extends PropsFromAppRouter {
  case class State(
      group: Option[Group] = None,
      memberList: Option[MemberList] = None,
      notification: Option[Notification] = None,
      newUsername: Option[String] = None,
      newUserIsManager: Boolean = false)

  private val component = ScalaComponent
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
        GlobalStyle.styleSheet.height100,
        ^.className := "right_col",
        ^.role := "main",
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
                        ^.className := "panel-heading",
                        NewMemberForm(
                          NewMemberForm.Props(
                            P.http,
                            P.page.asInstanceOf[ToGroupViewPage].id,
                            S.group,
                            () => getMembers(P))),
                        <.div(^.className := "clearfix")
                      ),
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
                                ^.title := "Managers can add new members, and edit or delete the Group"
                              )
                            ),
                            <.th("Actions")
                          )),
                          S.memberList match {
                            case Some(ml) =>
                              <.tbody(ml.members.map {
                                m =>
                                  <.tr(
                                    <.td(m.userName),
                                    <.td(toName(m)),
                                    <.td(m.email),
                                    <.td(groupManagerWidget(P, S, m)),
                                    <.td(
                                      ^.className := "table-form-group",
                                      <.button(
                                        ^.className := "btn btn-danger btn-rounded",
                                        ^.`type` := "button",
                                        ^.onClick --> deleteMember(P, S, m),
                                        ^.title := s"Remove ${m.userName} from group",
                                        VdomAttr("data-toggle") := "tooltip",
                                        <.span(^.className := "fa fa-trash")
                                      )
                                    )
                                  )
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
                  s"Group with ID ${P.page.asInstanceOf[ToGroupViewPage].id} not found")),
              <.div(^.className := "clearfix")
            )
        }
      )

    def getGroup(P: Props): Callback = {
      val groupId = P.page.asInstanceOf[ToGroupViewPage].id
      val onFailure = { httpResponse: HttpResponse =>
        setNotification(P.http.toNotification("getting group", httpResponse, onlyOnError = true))
      }
      val onSuccess = { (_: HttpResponse, parsed: Option[Group]) =>
        bs.modState(_.copy(group = parsed)) >> getMembers(P)
      }

      P.http.get(GetGroupRoute(groupId), onSuccess, onFailure)
    }

    def getMembers(P: Props): Callback = {
      val groupId = P.page.asInstanceOf[ToGroupViewPage].id
      val onError = { httpResponse: HttpResponse =>
        setNotification(
          P.http
            .toNotification(
              s"getting group members for group id $groupId",
              httpResponse,
              onlyOnError = true))
      }
      val onSuccess = { (_: HttpResponse, parsed: Option[MemberList]) =>
        bs.modState(_.copy(memberList = parsed))
      }
      P.http.get(GetGroupMembersRoute(groupId), onSuccess, onError)
    }

    def deleteMember(P: Props, S: State, user: User): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to remove member ${user.userName}",
        Callback
          .lazily {
            S.group match {
              case Some(g) =>
                val newMembers = g.members.filter(id => id.id != user.id)
                val newAdmins = g.admins.filter(id => id.id != user.id)
                val updatedGroup = g.copy(members = newMembers, admins = newAdmins)
                val groupId = P.page.asInstanceOf[ToGroupViewPage].id

                val onSuccess = { (httpResponse: HttpResponse, _: Option[Group]) =>
                  setNotification(
                    P.http.toNotification(s"deleting member ${user.id}", httpResponse)) >>
                    getMembers(P)
                }
                val onFailure = { httpResponse: HttpResponse =>
                  setNotification(
                    P.http.toNotification(s"deleting member ${user.id}", httpResponse))
                }
                P.http
                  .put(UpdateGroupRoute(groupId), write(updatedGroup), onSuccess, onFailure)
              case None => Callback.empty
            }
          }
      )

    def addGroupAdmin(P: Props, S: State, user: User): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to make ${user.userName} a Group Manager?",
        Callback.lazily {
          S.group match {
            case Some(g) =>
              val newAdmins = g.admins ++ Seq(Id(user.id))
              val updatedGroup = g.copy(admins = newAdmins)
              val groupId = P.page.asInstanceOf[ToGroupViewPage].id

              val onSuccess = { (httpResponse: HttpResponse, _: Option[Group]) =>
                setNotification(P.http.toNotification(s"adding manager ${user.id}", httpResponse)) >>
                  getMembers(P)
              }
              val onFailure = { httpResponse: HttpResponse =>
                setNotification(P.http.toNotification(s"adding manager ${user.id}", httpResponse))
              }

              P.http
                .put(UpdateGroupRoute(groupId), write(updatedGroup), onSuccess, onFailure)
            case None => Callback.empty
          }
        }
      )

    def removeGroupAdmin(P: Props, S: State, user: User): Callback =
      P.http.withConfirmation(
        s"Are you sure you no longer want ${user.userName} to be a Group Manager?",
        Callback
          .lazily {
            S.group match {
              case Some(g) =>
                val newAdmins = g.admins.filter(id => id.id != user.id)
                val updatedGroup = g.copy(admins = newAdmins)
                val groupId = P.page.asInstanceOf[ToGroupViewPage].id

                val onSuccess = { (httpResponse: HttpResponse, _: Option[Group]) =>
                  setNotification(
                    P.http.toNotification(s"removing manager ${user.id}", httpResponse)) >>
                    getMembers(P)
                }
                val onFailure = { httpResponse: HttpResponse =>
                  setNotification(
                    P.http.toNotification(s"removing manager ${user.id}", httpResponse))
                }

                P.http
                  .put(UpdateGroupRoute(groupId), write(updatedGroup), onSuccess, onFailure)
              case None => Callback.empty
            }
          }
      )

    def getDescriptionHeader(group: Group): TagMod =
      group.description match {
        case Some(d) => <.h5(s"Description: $d")
        case None => TagMod.empty
      }

    def getIdHeader(group: Group): TagMod = <.h5(s"Id: ${group.id}")

    def getEmailHeader(group: Group): TagMod = <.h5(s"Email: ${group.email}")

    def toName(user: User): String =
      (user.lastName, user.firstName) match {
        case (Some(ln), Some(fn)) => s"$ln, $fn"
        case (Some(ln), None) => ln
        case (None, Some(fn)) => fn
        case (None, None) => ""
      }

    def groupManagerWidget(P: Props, S: State, user: User): TagMod = {
      val hasAccess = hasUpdateAccess(S, user)
      val isManager = S.group.exists { g =>
        val admins = g.admins
        admins.contains(Id(user.id))
      }

      def toggleFunction: Callback =
        if (isManager)
          removeGroupAdmin(P, S, user)
        else addGroupAdmin(P, S, user)

      <.input(
        GlobalStyle.styleSheet.cursorPointer,
        ^.`type` := "checkbox",
        ^.checked := hasAccess,
        ^.onChange --> toggleFunction
      )
    }

    def hasUpdateAccess(S: State, user: User): Boolean = {
      val isManager = S.group.exists { g =>
        val admins = g.admins
        admins.contains(Id(user.id))
      }
      isManager || user.isSuper
    }
  }
}
