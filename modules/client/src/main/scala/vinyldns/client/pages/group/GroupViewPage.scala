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
import org.scalajs.dom.raw.XMLHttpRequest
import vinyldns.client.ajax.{GetGroupMembersRoute, GetGroupRoute, Request, UpdateGroupRoute}
import vinyldns.client.models.membership.{Group, MemberList}
import upickle.default.write
import vinyldns.client.ReactApp.SUCCESS_ALERT_TIMEOUT_MILLIS
import vinyldns.client.components.AlertBox
import vinyldns.client.css.GlobalStyle
import vinyldns.client.models.{Id, Notification}
import vinyldns.client.models.user.User
import vinyldns.client.pages.group.components.NewMemberForm
import vinyldns.client.routes.AppRouter.{Page, PropsFromAppRouter, ToGroupViewPage}

import scala.scalajs.js.timers.setTimeout

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

  def apply(
      page: Page,
      router: RouterCtl[Page],
      requestHelper: Request): Unmounted[Props, State, Backend] =
    component(Props(page, router, requestHelper))

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomNode =
      <.div(
        GlobalStyle.styleSheet.height100,
        ^.className := "right_col",
        ^.role := "main",
        S.notification match {
          case Some(n) => AlertBox(AlertBox.Props(n, () => clearNotification))
          case None => TagMod.empty
        },
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
                            P.requestHelper,
                            P.page.asInstanceOf[ToGroupViewPage].id,
                            S.group,
                            setNotification,
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

    def getGroup(P: Props): Callback = {
      val groupId = P.page.asInstanceOf[ToGroupViewPage].id
      val onFailure = { xhr: XMLHttpRequest =>
        setNotification(P.requestHelper.toNotification("getting group", xhr, onlyOnError = true))
      }
      val onSuccess = { (_: XMLHttpRequest, parsed: Option[Group]) =>
        bs.modState(_.copy(group = parsed)) >> getMembers(P)
      }

      P.requestHelper.get(GetGroupRoute(groupId), onSuccess, onFailure)
    }

    def getMembers(P: Props): Callback = {
      val groupId = P.page.asInstanceOf[ToGroupViewPage].id
      val onError = { xhr: XMLHttpRequest =>
        setNotification(P.requestHelper
          .toNotification(s"getting group members for group id $groupId", xhr, onlyOnError = true))
      }
      val onSuccess = { (_: XMLHttpRequest, parsed: Option[MemberList]) =>
        bs.modState(_.copy(memberList = parsed))
      }
      P.requestHelper.get(GetGroupMembersRoute(groupId), onSuccess, onError)
    }

    def deleteMember(P: Props, S: State, user: User): Callback =
      P.requestHelper.withConfirmation(
        s"Are you sure you want to remove member ${user.userName}",
        Callback
          .lazily {
            S.group match {
              case Some(g) =>
                val newMembers = g.members.map(_.filter(id => id.id != user.id))
                val newAdmins = g.admins.map(_.filter(id => id.id != user.id))
                val updatedGroup = g.copy(members = newMembers, admins = newAdmins)
                val groupId = P.page.asInstanceOf[ToGroupViewPage].id

                val onSuccess = { (xhr: XMLHttpRequest, _: Option[Group]) =>
                  setNotification(
                    P.requestHelper.toNotification(s"deleting member ${user.id}", xhr)) >>
                    getMembers(P)
                }
                val onFailure = { xhr: XMLHttpRequest =>
                  setNotification(
                    P.requestHelper.toNotification(s"deleting member ${user.id}", xhr))
                }
                P.requestHelper
                  .put(UpdateGroupRoute(groupId), write(updatedGroup), onSuccess, onFailure)
              case None => Callback.empty
            }
          }
      )

    def addGroupAdmin(P: Props, S: State, user: User): Callback =
      P.requestHelper.withConfirmation(
        s"Are you sure you want to make ${user.userName} a Group Manager?",
        Callback.lazily {
          S.group match {
            case Some(g) =>
              val newAdmins = g.admins.map(_ ++ Seq(Id(user.id)))
              val updatedGroup = g.copy(admins = newAdmins)
              val groupId = P.page.asInstanceOf[ToGroupViewPage].id

              val onSuccess = { (xhr: XMLHttpRequest, _: Option[Group]) =>
                setNotification(P.requestHelper.toNotification(s"adding manager ${user.id}", xhr)) >>
                  getMembers(P)
              }
              val onFailure = { xhr: XMLHttpRequest =>
                setNotification(P.requestHelper.toNotification(s"adding manager ${user.id}", xhr))
              }

              P.requestHelper
                .put(UpdateGroupRoute(groupId), write(updatedGroup), onSuccess, onFailure)
            case None => Callback.empty
          }
        }
      )

    def removeGroupAdmin(P: Props, S: State, user: User): Callback =
      P.requestHelper.withConfirmation(
        s"Are you sure you no longer want ${user.userName} to be a Group Manager?",
        Callback
          .lazily {
            S.group match {
              case Some(g) =>
                val newAdmins = g.admins.map(_.filter(id => id.id != user.id))
                val updatedGroup = g.copy(admins = newAdmins)
                val groupId = P.page.asInstanceOf[ToGroupViewPage].id

                val onSuccess = { (xhr: XMLHttpRequest, _: Option[Group]) =>
                  setNotification(
                    P.requestHelper.toNotification(s"removing manager ${user.id}", xhr)) >>
                    getMembers(P)
                }
                val onFailure = { xhr: XMLHttpRequest =>
                  setNotification(
                    P.requestHelper.toNotification(s"removing manager ${user.id}", xhr))
                }

                P.requestHelper
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

    def groupManagerWidget(P: Props, S: State, user: User): TagMod = {
      val hasAccess = hasUpdateAccess(S, user)
      val isManager = S.group.exists { g =>
        val admins = g.admins.getOrElse(Seq())
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
        val admins = g.admins.getOrElse(Seq())
        admins.contains(Id(user.id))
      }
      isManager || user.isSuper
    }
  }
}
