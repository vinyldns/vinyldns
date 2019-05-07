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

package vinyldns.client.pages.zone.view.components

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.css.GlobalStyle
import vinyldns.client.http.{DeleteRecordSetRoute, Http, HttpResponse, ListRecordSetsRoute}
import vinyldns.client.models.Pagination
import vinyldns.client.models.record.{
  RecordSetChangeResponse,
  RecordSetListResponse,
  RecordSetResponse
}
import vinyldns.client.models.zone.ZoneResponse
import vinyldns.client.router.{Page, ToGroupViewPage}
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.pages.zone.view.components.recordmodal.RecordSetModal
import vinyldns.client.components.JsNative._
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.record.RecordType.RecordType

import scala.util.Try

object RecordSetTable {
  case class Props(
      zone: ZoneResponse,
      groupList: GroupListResponse,
      http: Http,
      routerCtl: RouterCtl[Page],
      refreshChanges: Unit => Callback)
  case class State(
      recordSetList: Option[RecordSetListResponse] = None,
      nameFilter: Option[String] = None,
      pagination: Pagination = Pagination(),
      maxItems: Int = 100,
      showCreateRecordModal: Boolean = false,
      showUpdateRecordModal: Boolean = false,
      toBeUpdated: Option[RecordSetResponse] = None)

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
            <.h3(^.className := "panel-title", "DNS Record Sets"),
            <.br,
            // create recordset button
            <.div(
              ^.className := "btn-group",
              <.button(
                ^.className := "btn btn-default test-create-recordset",
                ^.`type` := "button",
                ^.onClick --> makeCreateRecordModalVisible,
                <.span(^.className := "fa fa-plus-square"),
                "  Create Record Set"
              ),
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
                        <.th(
                          "Record Owner Group  ",
                          <.span(
                            GlobalStyle.Styles.cursorPointer,
                            ^.className := "fa fa-info-circle",
                            VdomAttr("data-toggle") := "tooltip",
                            ^.title :=
                              """
                                | When a Zone is set to SHARED, then any Group can create/update records and set
                                | an Owner Group to manage it. Zone Admins will always have full access, and Zone
                                | Access Rules will apply as normal. If the Zone is PRIVATE, then this field has no
                                | effect.
                              """.stripMargin
                          )
                        ),
                        <.th("Actions")
                      )
                    ),
                    <.tbody(
                      rl.recordSets.map(toTableRow(P, S, _)).toTagMod
                    )
                  )
                )
              case Some(rl) if rl.recordSets.isEmpty =>
                <.p("No DNS records found")
              case None =>
                <.p("Loading records...")
            }
          )
        ),
        createRecordSetModal(P, S),
        updateRecordSetModal(P, S)
      )

    def toTableRow(P: Props, S: State, recordSet: RecordSetResponse): TagMod =
      <.tr(
        <.td(toRecordSetName(recordSet.name, recordSet.`type`, P.zone.name)),
        <.td(recordSet.`type`.toString),
        <.td(recordSet.ttl),
        <.td(recordSet.recordDataDisplay),
        <.td(
          (recordSet.ownerGroupId, recordSet.ownerGroupName) match {
            case (Some(id), Some(name)) =>
              <.a(
                GlobalStyle.Styles.cursorPointer,
                name,
                P.routerCtl.setOnClick(ToGroupViewPage(id))
              )
            case _ => TagMod.empty
          }
        ),
        <.td(
          <.div(
            ^.className := "btn-group",
            if (recordSet.canUpdate(P.zone.name))
              <.button(
                ^.className := "btn btn-info btn-rounded test-edit",
                ^.`type` := "button",
                ^.onClick --> makeUpdateRecordModalVisible(recordSet),
                ^.title := s"Update record set (name: ${recordSet.name}, id: ${recordSet.id})",
                VdomAttr("data-toggle") := "tooltip",
                <.span(^.className := "fa fa-edit"),
                " Update"
              )
            else TagMod.empty,
            if (recordSet.canDelete(P.zone.name))
              <.button(
                ^.className := "btn btn-danger btn-rounded test-delete",
                ^.`type` := "button",
                ^.onClick --> deleteRecordSet(P, S, recordSet),
                ^.title := s"Delete record set (name: ${recordSet.name}, id: ${recordSet.id})",
                VdomAttr("data-toggle") := "tooltip",
                <.span(^.className := "fa fa-trash"),
                " Delete"
              )
            else TagMod.empty
          )
        )
      )

    def toRecordSetName(recordName: String, recordType: RecordType, zoneName: String): TagMod =
      if (RecordSetResponse.labelHasInvalidDot(recordName, recordType, zoneName))
        <.div(
          ^.className := "text-danger",
          ^.title := s"Dotted hosts are invalid! Please delete or update without a '.'",
          VdomAttr("data-toggle") := "tooltip",
          s"$recordName ",
          <.span(^.className := "fa fa-warning")
        )
      else <.div(recordName)

    def listRecordSets(P: Props, S: State, startFrom: Option[String] = None): Callback = {
      val onSuccess = { (_: HttpResponse, parsed: Option[RecordSetListResponse]) =>
        bs.modState(_.copy(recordSetList = parsed))
      }
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(
          P.http.toNotification("listing dns records", httpResponse, onlyOnError = true))
      }
      P.http.get(
        ListRecordSetsRoute(P.zone.id, S.maxItems, S.nameFilter, startFrom),
        onSuccess,
        onFailure)
    }

    def deleteRecordSet(P: Props, S: State, recordSet: RecordSetResponse): Callback =
      P.http.withConfirmation(
        s"Are you sure you want to delete record set with name ${recordSet.name} and id ${recordSet.id}",
        Callback.lazily {
          val alertMessage = s"deleting record set (name: ${recordSet.name}, id: ${recordSet.id})"
          val onSuccess = { (httpResponse: HttpResponse, _: Option[RecordSetChangeResponse]) =>
            addNotification(P.http.toNotification(alertMessage, httpResponse)) >>
              withDelay(
                HALF_SECOND_IN_MILLIS,
                resetPageInfo >> listRecordSets(P, S) >> P.refreshChanges(()))
          }
          val onFailure = { httpResponse: HttpResponse =>
            addNotification(P.http.toNotification(alertMessage, httpResponse))
          }
          P.http.delete(DeleteRecordSetRoute(P.zone.id, recordSet.id), onSuccess, onFailure)
        }
      )

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
      if (S.showCreateRecordModal)
        RecordSetModal(
          RecordSetModal
            .Props(
              P.http,
              P.zone,
              P.groupList,
              _ => makeCreateRecordModalInvisible,
              _ => listRecordSets(P, S),
              _ => P.refreshChanges(())))
      else TagMod.empty

    def makeCreateRecordModalVisible: Callback =
      bs.modState(_.copy(showCreateRecordModal = true))

    def makeCreateRecordModalInvisible: Callback =
      bs.modState(_.copy(showCreateRecordModal = false))

    def updateRecordSetModal(P: Props, S: State): TagMod =
      if (S.showUpdateRecordModal)
        RecordSetModal(
          RecordSetModal
            .Props(
              P.http,
              P.zone,
              P.groupList,
              _ => makeUpdateRecordModalInvisible,
              _ => listRecordSets(P, S),
              _ => P.refreshChanges(()),
              existing = S.toBeUpdated))
      else TagMod.empty

    def makeUpdateRecordModalVisible(toBeUpdated: RecordSetResponse): Callback =
      bs.modState(_.copy(toBeUpdated = Some(toBeUpdated), showUpdateRecordModal = true))

    def makeUpdateRecordModalInvisible: Callback =
      bs.modState(_.copy(showUpdateRecordModal = false))
  }
}
