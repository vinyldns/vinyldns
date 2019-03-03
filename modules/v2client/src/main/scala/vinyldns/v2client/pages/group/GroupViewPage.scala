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
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^.{^, _}
import org.scalajs.dom
import vinyldns.v2client.ajax.{GetGroupMembersRoute, GetGroupRoute, Request, UpdateGroupRoute}
import vinyldns.v2client.models.membership.{Group, MemberList}
import upickle.default.{read, write}
import vinyldns.v2client.ReactApp.SUCCESS_ALERT_TIMEOUT_MILLIS
import vinyldns.v2client.components.AlertBox
import vinyldns.v2client.css.GlobalStyle
import vinyldns.v2client.models.{Id, Notification}
import vinyldns.v2client.models.user.User
import vinyldns.v2client.pages.group.components.NewMemberForm
import vinyldns.v2client.routes.AppRouter.{Page, PropsFromAppRouter, ToGroupViewPage}

import scala.scalajs.js.timers.setTimeout
import scala.util.Try

object GroupViewPage extends PropsFromAppRouter {
  case class State(
      group: Option[Group] = None,
      members: List[User] = List(),
      notification: Option[Notification] = None,
      newUsername: Option[String] = None,
      newUserIsManager: Boolean = false)

  class Backend(bs: BackendScope[Props, State]) {
    def clearNotification: Callback =
      bs.modState(_.copy(notification = None))

    def setNotification(notification: Option[Notification]): Callback =
      notification match {
        case Some(n) if !n.isError =>
          bs.modState(_.copy(notification = notification)) >>
            Callback(setTimeout(SUCCESS_ALERT_TIMEOUT_MILLIS)(clearNotification.runNow()))
        case Some(n) if n.isError => bs.modState(_.copy(notification = notification))
        case None => Callback(())
      }

    def getGroup(P: Props): Callback = {
      val groupId = P.page.asInstanceOf[ToGroupViewPage].id
      Request
        .get(GetGroupRoute(groupId))
        .onComplete { xhr =>
          val alert =
            setNotification(Request.toNotification("getting group", xhr, onlyOnError = true))
          val group = Try(Option(read[Group](xhr.responseText))).getOrElse(None)
          alert >> bs.modState(_.copy(group = group)) >> getMembers
        }
        .asCallback
    }

    def getMembers: Callback =
      bs.state >>= { S =>
        S.group match {
          case Some(g) =>
            Request
              .get(GetGroupMembersRoute(g.id.getOrElse("")))
              .onComplete { xhr =>
                val alert =
                  setNotification(
                    Request.toNotification(
                      s"getting group members for group id ${g.id}",
                      xhr,
                      onlyOnError = true))
                val members = Try(read[MemberList](xhr.responseText).members).getOrElse(List())
                alert >> bs.modState(_.copy(members = members))
              }
              .asCallback
          case None => Callback(())
        }
      }

    def deleteMember(P: Props, S: State, user: User): Callback =
      CallbackTo[Boolean](
        dom.window.confirm(s"""Are you sure you want to remove member "${user.userName}"""")) >>= {
        confirmed =>
          if (confirmed) {
            S.group match {
              case Some(g) =>
                val newMembers = g.members.map(_.filter(id => id.id != user.id))
                val newAdmins = g.admins.map(_.filter(id => id.id != user.id))
                val updatedGroup = g.copy(members = newMembers, admins = newAdmins)
                val groupId = P.page.asInstanceOf[ToGroupViewPage].id
                Request
                  .put(UpdateGroupRoute(groupId), write(updatedGroup))
                  .onComplete { xhr =>
                    val alert =
                      setNotification(Request.toNotification(s"deleting member ${user.id}", xhr))
                    alert >> getMembers
                  }
                  .asCallback
              case None => Callback(())
            }
          } else Callback(())
      }

    def addGroupAdmin(P: Props, S: State, user: User): Callback =
      CallbackTo[Boolean](
        dom.window.confirm(
          s"""Are you sure you want to make "${user.userName}" a Group Manager?""")) >>= {
        confirmed =>
          if (confirmed) {
            S.group match {
              case Some(g) =>
                val newAdmins = g.admins.map(_ ++ Seq(Id(user.id)))
                val updatedGroup = g.copy(admins = newAdmins)
                val groupId = P.page.asInstanceOf[ToGroupViewPage].id
                Request
                  .put(UpdateGroupRoute(groupId), write(updatedGroup))
                  .onComplete { xhr =>
                    val alert =
                      setNotification(Request.toNotification(s"adding manager ${user.id}", xhr))
                    alert >> getMembers
                  }
                  .asCallback
              case None => Callback(())
            }
          } else Callback(())
      }

    def removeGroupAdmin(P: Props, S: State, user: User): Callback =
      CallbackTo[Boolean](
        dom.window.confirm(
          s"""Are you sure you no longer want "${user.userName}" to be a Group Manager?""")) >>= {
        confirmed =>
          if (confirmed) {
            S.group match {
              case Some(g) =>
                val newAdmins = g.admins.map(_.filter(id => id.id != user.id))
                val updatedGroup = g.copy(admins = newAdmins)
                val groupId = P.page.asInstanceOf[ToGroupViewPage].id
                Request
                  .put(UpdateGroupRoute(groupId), write(updatedGroup))
                  .onComplete { xhr =>
                    val alert =
                      setNotification(Request.toNotification(s"removing manager ${user.id}", xhr))
                    alert >> getMembers
                  }
                  .asCallback
              case None => Callback(())
            }
          } else Callback(())
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
        ^.onClick --> toggleFunction
      )
    }

    def hasUpdateAccess(S: State, user: User): Boolean = {
      val isManager = S.group.exists { g =>
        val admins = g.admins.getOrElse(Seq())
        admins.contains(Id(user.id))
      }
      isManager || user.isSuper
    }

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
                            P.page.asInstanceOf[ToGroupViewPage].id,
                            S.group,
                            setNotification,
                            () => getMembers)),
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
                          S.group match {
                            case Some(_) =>
                              <.tbody(S.members.map {
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
  }

  private val component = ScalaComponent
    .builder[Props]("ViewGroup")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.getGroup(e.props))
    .build

  def apply(page: Page, router: RouterCtl[Page]): Unmounted[Props, State, Backend] =
    component(Props(page, router))
}
