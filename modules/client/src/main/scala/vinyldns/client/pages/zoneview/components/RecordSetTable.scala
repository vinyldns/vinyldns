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
import vinyldns.client.css.GlobalStyle
import vinyldns.client.http.{Http, HttpResponse, ListRecordSetsRoute}
import vinyldns.client.models.Pagination
import vinyldns.client.models.record.{RecordSet, RecordSetList}
import vinyldns.client.models.zone.Zone
import vinyldns.client.router.Page
import vinyldns.client.components.AlertBox.addNotification

import scala.util.Try

object RecordSetTable {
  case class Props(zone: Zone, http: Http, routerCtl: RouterCtl[Page])
  case class State(
      recordSetList: Option[RecordSetList] = None,
      nameFilter: Option[String] = None,
      pagination: Pagination[String] = Pagination(),
      maxItems: Int = 100,
      showRecordModal: Boolean = false)

  val component = ScalaComponent
    .builder[Props]("RecordSetTable")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.listRecordSets(e.props, e.state))
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      <.div(
        <.div(
          ^.className := "panel panel-default",
          <.div(
            ^.className := "panel-heading",
            <.h3(^.className := "panel-title", "DNS Records"),
            <.br,
            // create recordset button
            <.div(
              ^.className := "btn-group",
              <.button(
                ^.className := "btn btn-default test-create-recordset",
                ^.`type` := "button",
                ^.onClick --> makeRecordModalVisible,
                <.span(^.className := "fa fa-plus-square"),
                "  Create Record"
              ),
              createRecordSetModal(P, S),
              // refresh button
              <.button(
                ^.className := "btn btn-default test-refresh-recordsets",
                ^.`type` := "button",
                ^.onClick --> { resetPageInfo >> listRecordSets(P, S) },
                <.span(^.className := "fa fa-refresh"),
                "  Refresh"
              )
            ),
            // search bar
            <.form(
              ^.className := "pull-right input-group test-search-form",
              ^.onSubmit ==> { e: ReactEventFromInput =>
                e.preventDefaultCB >> resetPageInfo >> listRecordSets(P, S)
              },
              <.div(
                ^.className := "input-group",
                <.span(
                  ^.className := "input-group-btn",
                  <.button(
                    ^.className := "btn btn-primary btn-left-round",
                    ^.`type` := "submit",
                    <.span(^.className := "fa fa-search")
                  )
                ),
                <.input(
                  ^.className := "form-control test-nameFilter",
                  ^.placeholder := "Record Name",
                  ^.onChange ==> { e: ReactEventFromInput =>
                    updateNameFilter(e.target.value)
                  }
                )
              )
            )
          ),
          <.div(
            ^.className := "panel-body",
            S.recordSetList match {
              case Some(rl)
                  if rl.recordSets.nonEmpty || rl.recordNameFilter.isDefined || S.pagination.pageNumber != 1 =>
                <.div(
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
                              listRecordSets(P, s)
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
                        ^.className := "btn btn-round btn-default",
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
                        ^.className := "btn btn-round btn-default",
                        ^.`type` := "button",
                        ^.onClick --> nextPage(P, S),
                        ^.disabled := rl.nextId.isEmpty,
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
                        <.th("Name"),
                        <.th("Type"),
                        <.th("Time To Live (s)"),
                        <.th("Record Data"),
                        <.th("Actions")
                      )
                    ),
                    <.tbody(
                      rl.recordSets.map(toTableRow(_)).toTagMod
                    )
                  )
                )
              case Some(rl) if rl.recordSets.isEmpty =>
                <.p("No dns records found")
              case None =>
                <.p("Loading records...")
            }
          )
        ),
      )

    def toTableRow(recordSet: RecordSet): TagMod =
      <.tr(
        <.td(recordSet.name),
        <.td(recordSet.`type`),
        <.td(recordSet.ttl),
        <.td(recordSet.recordDataDisplay),
        <.td("actions")
      )

    def listRecordSets(P: Props, S: State, startFrom: Option[String] = None): Callback = {
      val onSuccess = { (_: HttpResponse, parsed: Option[RecordSetList]) =>
        bs.modState(_.copy(recordSetList = parsed))
      }
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(P.http.toNotification("list dns records", httpResponse, onlyOnError = true))
      }
      P.http.get(
        ListRecordSetsRoute(P.zone.id, S.maxItems, S.nameFilter, startFrom),
        onSuccess,
        onFailure)
    }

    def updateNameFilter(value: String): Callback =
      if (value.isEmpty) bs.modState(_.copy(nameFilter = None))
      else bs.modState(_.copy(nameFilter = Some(value)))

    def resetPageInfo: Callback =
      bs.modState(s => s.copy(pagination = Pagination()))

    def nextPage(P: Props, S: State): Callback =
      S.recordSetList
        .map { rl =>
          bs.modState({ s =>
            s.copy(pagination = s.pagination.next(rl.startFrom))
          }, bs.state >>= { s =>
            listRecordSets(P, s, rl.nextId)
          })
        }
        .getOrElse(Callback.empty)

    def previousPage(P: Props): Callback =
      bs.modState(
        { s =>
          s.copy(pagination = s.pagination.previous())
        },
        bs.state >>= { s =>
          listRecordSets(P, s, s.pagination.popped)
        }
      )

    def createRecordSetModal(P: Props, S: State): TagMod =
      if (S.showRecordModal)
        RecordSetModal(
          RecordSetModal
            .Props(P.http, P.zone, _ => makeRecordModalInvisible, _ => listRecordSets(P, S)))
      else TagMod.empty

    def makeRecordModalVisible: Callback =
      bs.modState(_.copy(showRecordModal = true))

    def makeRecordModalInvisible: Callback =
      bs.modState(_.copy(showRecordModal = false))
  }
}
