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

package vinyldns.client.pages.zoneview.components

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.css.GlobalStyle
import vinyldns.client.http.{Http, HttpResponse, ListRecordSetChangesRoute}
import vinyldns.client.models.Pagination
import vinyldns.client.models.membership.GroupList
import vinyldns.client.models.record.{RecordSet, RecordSetChange, RecordSetChangeList}
import vinyldns.client.models.zone.Zone
import vinyldns.client.pages.zoneview.components.recordmodal.RecordSetModal
import vinyldns.client.router.Page
import vinyldns.core.domain.record.{RecordSetChangeStatus, RecordSetChangeType}

import scala.util.Try

object RecordSetChangeTable {
  case class Props(
      zone: Zone,
      groupList: GroupList,
      http: Http,
      routerCtl: RouterCtl[Page],
      recentOnly: Boolean = false)
  case class State(
      recordSetChangeList: Option[RecordSetChangeList] = None,
      pagination: Pagination = Pagination(),
      maxItems: Int,
      showViewRecordSetModal: Boolean = false,
      toBeViewed: Option[RecordSet] = None
  )

  val component = ScalaComponent
    .builder[Props]("RecordSetChangeTable")
    .initialStateFromProps { p =>
      if (p.recentOnly) State(maxItems = 5)
      else State(maxItems = 100)
    }
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.listRecordSetChanges(e.props, e.state))
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      <.div(
        <.div(
          ^.className := "panel panel-default",
          <.div(
            ^.className := "panel-heading",
            <.h3(^.className := "panel-title", toTitle(P)),
            <.br,
            <.div(
              ^.className := "btn-group",
              // refresh button
              <.button(
                ^.className := "btn btn-default test-refresh-recordsetchanges",
                ^.`type` := "button",
                ^.onClick --> { resetPageInfo >> listRecordSetChanges(P, S) },
                <.span(^.className := "fa fa-refresh"),
                "  Refresh"
              )
            )
          ),
          <.div(
            ^.className := "panel-body",
            S.recordSetChangeList match {
              case Some(rcl) if rcl.recordSetChanges.nonEmpty || S.pagination.pageNumber != 1 =>
                <.div(
                  if (P.recentOnly) <.span
                  else
                    <.span(
                      // items per page
                      <.span(
                        <.label(
                          GlobalStyle.Styles.keepWhitespace,
                          ^.className := "control-label",
                          "Items per page:  "),
                        <.select(
                          ^.onChange ==> { e: ReactEventFromInput =>
                            val maxItems = Try(e.target.value.toInt).getOrElse(100)
                            bs.modState(
                              _.copy(maxItems = maxItems),
                              resetPageInfo >>
                                bs.state >>= { s =>
                                listRecordSetChanges(P, s)
                              })
                          },
                          ^.value := S.maxItems,
                          List(100, 50, 25, 5, 1).map { o =>
                            <.option(^.key := o, o)
                          }.toTagMod,
                        )
                      ),
                      <.span(
                        ^.className := "btn-group pull-right",
                        // paginate
                        <.button(
                          ^.className := "btn btn-round btn-default test-previous-page",
                          ^.onClick --> previousPage(P),
                          ^.`type` := "button",
                          ^.disabled := S.pagination.pageNumber <= 1,
                          <.span(
                            ^.className := "fa fa-arrow-left"
                          ),
                          if (S.pagination.pageNumber > 1) s"  Page ${S.pagination.pageNumber - 1}"
                          else TagMod.empty
                        ),
                        <.button(
                          ^.className := "btn btn-round btn-default test-next-page",
                          ^.`type` := "button",
                          ^.onClick --> nextPage(P, S),
                          ^.disabled := rcl.nextId.isEmpty,
                          s"Page ${S.pagination.pageNumber + 1}  ",
                          <.span(
                            ^.className := "fa fa-arrow-right"
                          )
                        )
                      )
                    ),
                  <.table(
                    ^.className := "table",
                    <.thead(
                      <.tr(
                        List(
                          "Time",
                          "RecordSet Name",
                          "RecordSet Type",
                          "Change Type",
                          "User",
                          "Status",
                          "Additional Info").map(h => <.th(h)).toTagMod
                      )
                    ),
                    <.tbody(
                      rcl.recordSetChanges.map(toTableRow).toTagMod
                    )
                  ),
                  viewRecordSetModal(P, S)
                )
              case Some(rcl) if rcl.recordSetChanges.isEmpty =>
                <.p("No changes found")
              case None =>
                <.p("Loading change history...")
            }
          )
        )
      )

    def listRecordSetChanges(P: Props, S: State, startFrom: Option[String] = None): Callback = {
      val onSuccess = { (_: HttpResponse, parsed: Option[RecordSetChangeList]) =>
        bs.modState(_.copy(recordSetChangeList = parsed))
      }
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(
          P.http.toNotification("listing record set changes", httpResponse, onlyOnError = true))
      }
      P.http.get(ListRecordSetChangesRoute(P.zone.id, S.maxItems, startFrom), onSuccess, onFailure)
    }

    def toTitle(P: Props): String =
      if (P.recentOnly) "Recent Changes"
      else "DNS Record Set Change History"

    def toTableRow(change: RecordSetChange): TagMod =
      <.tr(
        <.td(change.created),
        <.td(change.recordSet.name),
        <.td(change.recordSet.`type`.toString),
        <.td(change.changeType.toString),
        <.td(change.userName),
        <.td(toStatus(change.status)),
        <.td(
          <.p(s"${change.systemMessage.getOrElse("")}"),
          toAdditionalInfo(change)
        )
      )

    def toAdditionalInfo(change: RecordSetChange): TagMod =
      change.changeType match {
        case RecordSetChangeType.Create =>
          <.a(
            GlobalStyle.Styles.cursorPointer,
            "View created recordset",
            ^.onClick --> makeViewRecordModalVisible(Some(change.recordSet))
          )
        case RecordSetChangeType.Update =>
          <.div(
            <.a(
              GlobalStyle.Styles.cursorPointer,
              "View old recordset",
              ^.onClick --> makeViewRecordModalVisible(change.updates)
            ),
            <.br,
            <.a(
              GlobalStyle.Styles.cursorPointer,
              "View new recordset",
              ^.onClick --> makeViewRecordModalVisible(Some(change.recordSet))
            )
          )
        case RecordSetChangeType.Delete =>
          <.a(
            GlobalStyle.Styles.cursorPointer,
            "View deleted recordset",
            ^.onClick --> makeViewRecordModalVisible(Some(change.recordSet))
          )
        case _ => TagMod.empty
      }

    def toStatus(status: RecordSetChangeStatus.RecordSetChangeStatus): TagMod =
      status match {
        case RecordSetChangeStatus.Complete =>
          <.span(^.className := "label label-success", RecordSetChangeStatus.Complete.toString)
        case RecordSetChangeStatus.Pending =>
          <.span(^.className := "label label-warning", RecordSetChangeStatus.Pending.toString)
        case RecordSetChangeStatus.Failed =>
          <.span(^.className := "label label-danger", RecordSetChangeStatus.Failed.toString)
        case _ => <.span
      }

    def resetPageInfo: Callback =
      bs.modState(s => s.copy(pagination = Pagination()))

    def nextPage(P: Props, S: State): Callback =
      S.recordSetChangeList
        .map { rl =>
          bs.modState({ s =>
            s.copy(pagination = s.pagination.next(rl.startFrom))
          }, bs.state >>= { s =>
            listRecordSetChanges(P, s, rl.nextId)
          })
        }
        .getOrElse(Callback.empty)

    def previousPage(P: Props): Callback =
      bs.modState(
        { s =>
          s.copy(pagination = s.pagination.previous())
        },
        bs.state >>= { s =>
          listRecordSetChanges(P, s, s.pagination.popped)
        }
      )

    def viewRecordSetModal(P: Props, S: State): TagMod =
      if (S.showViewRecordSetModal)
        RecordSetModal(
          RecordSetModal
            .Props(
              P.http,
              P.zone,
              P.groupList,
              _ => makeViewRecordModalInvisible,
              _ => Callback.empty,
              _ => Callback.empty,
              S.toBeViewed,
              readOnly = true
            ))
      else TagMod.empty

    def makeViewRecordModalVisible(recordSet: Option[RecordSet]): Callback =
      bs.modState(_.copy(toBeViewed = recordSet, showViewRecordSetModal = true))

    def makeViewRecordModalInvisible: Callback =
      bs.modState(_.copy(showViewRecordSetModal = false))
  }
}
