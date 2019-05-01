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

package vinyldns.client.pages.batchlist.components

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.components.AlertBox.addNotification
import vinyldns.client.http._
import vinyldns.client.css.GlobalStyle
import vinyldns.client.models.Pagination
import vinyldns.client.models.batch.{BatchChangeList, BatchChangeSummary}
import vinyldns.client.router.Page
import vinyldns.core.domain.batch.BatchChangeStatus

import scala.util.Try

object BatchChangesTable {
  case class Props(http: Http, router: RouterCtl[Page])

  case class State(
      batchChangeList: Option[BatchChangeList] = None,
      pagination: Pagination = Pagination(),
      maxItems: Int = 100)

  val component = ScalaComponent
    .builder[Props]("BatchChangesTable")
    .initialState(State())
    .renderBackend[Backend]
    .componentWillMount(e => e.backend.listBatchChanges(e.props, e.state))
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      <.div(
        S.batchChangeList match {
          case Some(bcl) if bcl.batchChanges.nonEmpty || S.pagination.pageNumber != 1 =>
            <.div(
              <.div(
                ^.className := "panel-heading",
                // items per page
                <.span(
                  <.label(
                    GlobalStyle.Styles.keepWhitespace,
                    ^.className := "control-label",
                    "Items per page:  "),
                  <.select(
                    ^.value := S.maxItems,
                    ^.onChange ==> { e: ReactEventFromInput =>
                      val maxItems = Try(e.target.value.toInt).getOrElse(100)
                      bs.modState(
                        _.copy(maxItems = maxItems),
                        resetPageInfo >>
                          bs.state >>= { s =>
                          listBatchChanges(P, s)
                        })
                    },
                    List(100, 50, 25, 5, 1).map { o =>
                      <.option(^.key := o, o)
                    }.toTagMod,
                  )
                ),
                <.span(
                  // paginate
                  ^.className := "btn-group pull-right",
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
                    ^.onClick --> nextPage(P, S),
                    ^.`type` := "button",
                    ^.disabled := bcl.nextId.isEmpty,
                    s"Page ${S.pagination.pageNumber + 1}  ",
                    <.span(
                      ^.className := "fa fa-arrow-right"
                    )
                  )
                )
              ),
              <.div(^.className := "clearfix"),
              <.div(
                ^.className := "panel-body",
                <.table(
                  ^.className := "table",
                  <.thead(
                    <.tr(
                      <.th("Time"),
                      <.th("Batch ID"),
                      <.th("Change Count"),
                      <.th("Status"),
                      <.th("Description"),
                      <.th("Actions")
                    )
                  ),
                  <.tbody(
                    bcl.batchChanges.map(toTableRow).toTagMod
                  )
                )
              )
            )
          case Some(bcl) if bcl.batchChanges.isEmpty => <.p("You don't have any batch changes yet")
          case None => <.p("Loading your batch changes...")
        }
      )

    def toTableRow(change: BatchChangeSummary): TagMod =
      <.tr(
        <.td(change.createdTimestamp),
        <.td(change.id),
        <.td(change.totalChanges),
        <.td(toStatus(change.status)),
        <.td(s"${change.comments.getOrElse("")}"),
        <.td("actions")
      )

    def listBatchChanges(P: Props, S: State, startFrom: Option[String] = None): Callback = {
      val onSuccess = { (_: HttpResponse, parsed: Option[BatchChangeList]) =>
        bs.modState(_.copy(batchChangeList = parsed))
      }
      val onFailure = { httpResponse: HttpResponse =>
        addNotification(
          P.http.toNotification("listing batch changes", httpResponse, onlyOnError = true))
      }
      P.http.get(ListBatchChangesRoute(S.maxItems, startFrom), onSuccess, onFailure)
    }

    def resetPageInfo: Callback =
      bs.modState(s => s.copy(pagination = Pagination()))

    def nextPage(P: Props, S: State): Callback =
      S.batchChangeList
        .map { gl =>
          bs.modState({ s =>
            s.copy(pagination = s.pagination.next(gl.startFrom))
          }, bs.state >>= { s =>
            listBatchChanges(P, s, gl.nextId)
          })
        }
        .getOrElse(Callback.empty)

    def previousPage(P: Props): Callback =
      bs.modState(
        { s =>
          s.copy(pagination = s.pagination.previous())
        },
        bs.state >>= { s =>
          listBatchChanges(P, s, s.pagination.popped)
        }
      )

    def toStatus(status: BatchChangeStatus.BatchChangeStatus): TagMod =
      status match {
        case BatchChangeStatus.Complete =>
          <.span(^.className := "label label-success", ^.name := status.toString, "Complete")
        case BatchChangeStatus.Pending =>
          <.span(^.className := "label label-warning", ^.name := status.toString, "Pending")
        case BatchChangeStatus.Failed =>
          <.span(^.className := "label label-danger", ^.name := status.toString, "Failure")
        case BatchChangeStatus.PartialFailure =>
          <.span(^.className := "label label-danger", ^.name := status.toString, "Partial Failure")
        case _ => <.span(status.toString)
      }
  }
}
