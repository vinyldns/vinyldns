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

package vinyldns.client.pages.batch.view

import vinyldns.client.models.batch.{BatchChangeResponse, SingleChangeResponse}
import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^.{^, _}
import vinyldns.client.http._
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.css.GlobalStyle
import vinyldns.client.router.AppRouter.PropsFromAppRouter
import vinyldns.client.router.{Page, ToBatchChangeViewPage, ToGroupViewPage}
import vinyldns.client.components.JsNative.toReadableTimestamp
import vinyldns.core.domain.batch.SingleChangeStatus

object BatchChangeViewPage extends PropsFromAppRouter {
  case class State(batchChange: Option[BatchChangeResponse] = None, filter: Option[String] = None)

  val component = ScalaComponent
    .builder[Props]("ViewBatchChange")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.getBatchChange(e.props))
    .build

  def apply(page: Page, router: RouterCtl[Page], http: Http): Unmounted[Props, State, Backend] =
    component(Props(page, router, http))

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomNode =
      <.div(
        GlobalStyle.Styles.height100,
        ^.className := "right_col",
        ^.role := "main",
        S.batchChange match {
          case Some(bc) =>
            val filteredChanges = S.filter match {
              case Some(f) => bc.changes.filter(_.inputName.contains(f))
              case None => bc.changes
            }
            <.div(
              <.div(
                ^.className := "page-title",
                <.div(
                  ^.className := "title_left",
                  <.h3(<.span(^.className := "fa fa-list"), "  Batch Change"),
                  <.h5(s"ID: ${bc.id}"),
                  toOwnerGroupHeader(P, bc),
                  <.h5(s"Created: ${toReadableTimestamp(bc.createdTimestamp)}"),
                  toDescriptionHeader(bc)
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
                        "Changes",
                        <.div(^.className := "clearfix")
                      ),
                      <.div(
                        ^.className := "panel-body",
                        // refresh button
                        <.button(
                          ^.className := "btn btn-default test-refresh",
                          ^.`type` := "button",
                          ^.onClick --> getBatchChange(P),
                          <.span(^.className := "fa fa-refresh"),
                          GlobalStyle.Styles.keepWhitespace,
                          "  Refresh"
                        ),
                        // search bar
                        <.form(
                          ^.className := "input-group pull-right col-md-3",
                          ^.onSubmit ==> { e: ReactEventFromInput =>
                            e.preventDefaultCB
                          },
                          <.div(
                            ^.className := "input-group",
                            <.span(
                              ^.className := "input-group-btn",
                              <.button(
                                ^.className := "btn btn-primary btn-left-round",
                                ^.`type` := "button",
                                <.span(
                                  ^.className := "fa fa-search"
                                )
                              )
                            ),
                            <.input(
                              ^.className := "form-control test-filter",
                              ^.onChange ==> { e: ReactEventFromInput =>
                                changeFilter(e.target.value)
                              }
                            )
                          )
                        ),
                        <.table(
                          ^.className := "table",
                          <.thead(
                            <.tr(
                              <.th("Change Type"),
                              <.th("Input Name"),
                              <.th("RecordSet Name"),
                              <.th("Zone Name"),
                              <.th("Record Type"),
                              <.th("RecordData"),
                              <.th("Time To Live (s)"),
                              <.th("Status"),
                              <.th("Additional Info")
                            )
                          ),
                          <.tbody(
                            filteredChanges.map(toTableRow).toTagMod
                          )
                        )
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
                <.p("Loading...")
              )
            )
        }
      )

    def toTableRow(singleChange: SingleChangeResponse): TagMod =
      <.tr(
        <.td(singleChange.changeType),
        <.td(singleChange.inputName),
        <.td(singleChange.recordName),
        <.td(singleChange.zoneName),
        <.td(singleChange.`type`.toString),
        <.td(singleChange.recordDataDisplay),
        <.td(s"""${singleChange.ttl.getOrElse("")}"""),
        <.td(toStatus(singleChange.status)),
        <.td(s"""${singleChange.systemMessage.getOrElse("")}""")
      )

    def toDescriptionHeader(batchChange: BatchChangeResponse): TagMod =
      batchChange.comments match {
        case Some(c) => <.h5(s"Description: $c")
        case None => TagMod.empty
      }

    def toOwnerGroupHeader(P: Props, batchChange: BatchChangeResponse): TagMod =
      batchChange.ownerGroupId match {
        case Some(id) =>
          val name = batchChange.ownerGroupName.getOrElse("")
          <.h5(
            "Owner Group: ",
            <.a(
              GlobalStyle.Styles.cursorPointer,
              s"$name ($id)",
              P.router.setOnClick(ToGroupViewPage(id))
            )
          )
        case None => TagMod.empty
      }

    def changeFilter(value: String): Callback =
      bs.modState { s =>
        if (value.trim.isEmpty) s.copy(filter = None)
        else s.copy(filter = Some(value.trim))
      }

    def toStatus(status: SingleChangeStatus.SingleChangeStatus): TagMod =
      status match {
        case SingleChangeStatus.Complete =>
          <.span(^.className := "label label-success", ^.name := status.toString, "Complete")
        case SingleChangeStatus.Pending =>
          <.span(^.className := "label label-warning", ^.name := status.toString, "Pending")
        case SingleChangeStatus.Failed =>
          <.span(^.className := "label label-danger", ^.name := status.toString, "Failure")
        case _ => <.span(status.toString)
      }

    def getBatchChange(P: Props): Callback = {
      val batchChangeId = P.page.asInstanceOf[ToBatchChangeViewPage].id
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(
          P.http.toNotification("getting batch change", httpResponse, onlyOnError = true))
      }
      val onSuccess = { (_: HttpResponse, parsed: Option[BatchChangeResponse]) =>
        bs.modState(_.copy(batchChange = parsed))
      }

      P.http.get(GetBatchChangeRoute(batchChangeId), onSuccess, onFailure)
    }
  }
}
