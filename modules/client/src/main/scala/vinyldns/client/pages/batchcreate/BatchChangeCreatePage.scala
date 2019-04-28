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

package vinyldns.client.pages.batchcreate

import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.http.{CreateBatchChangeRoute, Http, HttpResponse}
import vinyldns.client.css.GlobalStyle
import vinyldns.client.models.batch.{BatchChangeCreateInfo, SingleChangeCreateInfo}
import vinyldns.client.models.record.RecordData
import vinyldns.client.router.AppRouter.PropsFromAppRouter
import vinyldns.client.components.AlertBox.addNotification
import upickle.default._
import vinyldns.client.router.Page

import scala.util.Try

object BatchChangeCreatePage extends PropsFromAppRouter {
  case class State(createInfo: BatchChangeCreateInfo)

  val component = ScalaComponent
    .builder[Props]("BatchCreatePage")
    .initialState(State(BatchChangeCreateInfo()))
    .renderBackend[Backend]
    .build

  def apply(page: Page, routerCtl: RouterCtl[Page], http: Http): Unmounted[Props, State, Backend] =
    component(Props(page, routerCtl, http))

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      <.div(
        GlobalStyle.Styles.height100,
        ^.className := "right_col",
        ^.role := "main",
        <.div(
          ^.className := "page-title",
          <.div(
            ^.className := "title_left",
            <.h3(<.span(^.className := "fa fa-list"), "  Create a DNS Record Request"))),
        <.div(^.className := "clearfix"),
        <.div(
          ^.className := "page-content-wrap",
          <.div(
            ^.className := "row",
            <.div(
              ^.className := "col-md-12 col-sm-12 col-xs-12",
              <.form(
                ^.onSubmit ==> { e: ReactEventFromInput =>
                  e.preventDefaultCB >>
                    submitBatchChange(P, S)
                },
                <.div(
                  ^.className := "panel panel-default",
                  <.div(
                    ^.className := "panel-heading",
                    <.div(
                      ^.className := "form-group",
                      <.label(
                        ^.className := "h5",
                        "Description"
                      ),
                      <.input(
                        ^.className := "form-control",
                        ^.onChange ==> { e: ReactEventFromInput =>
                          changeComments(e.target.value)
                        },
                        ^.value := s"${S.createInfo.comments.getOrElse("")}"
                      )
                    )
                  ),
                  <.div(
                    ^.className := "panel-body",
                    <.h4("Changes"),
                    <.table(
                      ^.className := "table",
                      toTableHeader(),
                      <.tbody(
                        S.createInfo.changes.zipWithIndex.map {
                          case (change, index) =>
                            singleChangeRow(change, index)
                        }.toTagMod
                      )
                    )
                  ),
                  <.div(
                    ^.className := "panel-footer clearfix",
                    <.button(
                      ^.className := "btn btn-primary pull-right",
                      ^.`type` := "submit",
                      "Submit"
                    )
                  )
                )
              )
            )
          )
        )
      )

    def toTableHeader(): TagMod =
      <.thead(
        <.tr(
          <.th("#"),
          <.th(^.className := "col-md-2", "Change Type"),
          <.th("Record Type"),
          <.th(
            GlobalStyle.Styles.keepWhitespace,
            ^.className := "col-md-2",
            "Input Name ",
            <.span(
              VdomAttr("data-toggle") := "tooltip",
              ^.title := "Fully Qualified Domain Name (FQDN) or IP Address, depending on Record Type",
              ^.className := "fa fa-info-circle"
            )
          ),
          <.th(
            ^.className := "col-md-1",
            GlobalStyle.Styles.keepWhitespace,
            "TTL ",
            <.span(
              VdomAttr("data-toggle") := "tooltip",
              ^.title := "Time To Live",
              ^.className := "fa fa-info-circle"
            )
          ),
          <.th(^.className := "col-md-2", "Record Data"),
          <.th,
          <.th
        )
      )

    def singleChangeRow(change: SingleChangeCreateInfo, index: Int): TagMod =
      <.tr(
        ^.className := toErrorClass(change),
        <.td(
          index + 1
        ),
        <.td(
          <.select(
            ^.className := "form-control",
            ^.required := true,
            ^.onChange ==> { e: ReactEventFromInput =>
              changeSingleChangeType(e.target.value, change, index)
            },
            ^.value := change.changeType,
            List("Add", "DeleteRecordSet").map(o => <.option(^.value := o, o)).toTagMod
          )
        ),
        <.td(
          <.select(
            ^.className := "form-control",
            ^.required := true,
            ^.onChange ==> { e: ReactEventFromInput =>
              changeSingleRecordType(e.target.value, change, index)
            },
            ^.value := change.`type`,
            List(
              "A+PTR", // +PTRs will be converted before posting the batch change
              "AAAA+PTR",
              "A",
              "AAAA",
              "CNAME",
              "PTR",
              "TXT",
              "MX"
            ).map(o => <.option(^.value := o, o)).toTagMod
          )
        ),
        <.td(
          <.input(
            ^.className := "form-control",
            ^.required := true,
            ^.onChange ==> { e: ReactEventFromInput =>
              changeSingleInputName(e.target.value, change, index)
            },
            ^.value := change.inputName
          )
        ),
        <.td(
          if (change.changeType == "Add")
            <.input(
              ^.className := "form-control",
              ^.required := true,
              ^.`type` := "Number",
              ^.onChange ==> { e: ReactEventFromInput =>
                changeSingleInputTTL(e.target.value, change, index)
              },
              ^.value := change.ttl
            )
          else TagMod.empty
        ),
        <.td(
          if (change.changeType == "Add")
            if (change.`type` != "MX")
              <.input(
                ^.className := "form-control",
                ^.required := true,
                ^.onChange ==> { e: ReactEventFromInput =>
                  changeSingleInputRecordData(e.target.value, change, index)
                },
                ^.value := toRecordDataDisplay(change)
              )
            else toMxInput(change, index)
          else TagMod.empty
        ),
        <.td(
          change.errors match {
            case Some(es) => es.map(e => <.p(e)).toTagMod
            case None => TagMod.empty
          }
        ),
        <.td(
          <.button(
            ^.className := "btn btn-danger",
            ^.`type` := "button",
            "Delete",
            ^.onClick --> bs.modState { s =>
              val singleChanges = s.createInfo.changes.patch(index, Nil, 1)
              val createInfo = s.createInfo.copy(changes = singleChanges)
              s.copy(createInfo = createInfo)
            }
          )
        )
      )

    def changeSingleChangeType(
        value: String,
        change: SingleChangeCreateInfo,
        index: Int): Callback =
      bs.modState { s =>
        val newSingleChange = change.copy(changeType = value)
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeSingleRecordType(
        value: String,
        change: SingleChangeCreateInfo,
        index: Int): Callback =
      bs.modState { s =>
        val newSingleChange = change.copy(`type` = value, record = RecordData())
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeSingleInputName(value: String, change: SingleChangeCreateInfo, index: Int): Callback =
      bs.modState { s =>
        val newSingleChange = change.copy(inputName = value)
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeSingleInputTTL(value: String, change: SingleChangeCreateInfo, index: Int): Callback =
      bs.modState { s =>
        val newSingleChange = change.copy(ttl = Try(value.toInt).getOrElse(0))
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeSingleInputRecordData(
        value: String,
        change: SingleChangeCreateInfo,
        index: Int): Callback =
      bs.modState { s =>
        // MX is handled separately since it has multiple parts
        val recordData =
          change.`type` match {
            case address
                if address == "A" || address == "A+PTR" || address == "AAAA" || address == "AAAA+PTR" =>
              RecordData(address = Some(value))
            case cname if cname == "CNAME" => RecordData(cname = Some(value))
            case ptrdname if ptrdname == "PTR" => RecordData(ptrdname = Some(value))
            case text if text == "TXT" => RecordData(text = Some(value))
          }
        val newSingleChange = change.copy(record = recordData)
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeMxPreference(value: String, change: SingleChangeCreateInfo, index: Int): Callback =
      bs.modState { s =>
        val recordData = change.record.copy(preference = Some(Try(value.toInt).getOrElse(0)))
        val newSingleChange = change.copy(record = recordData)
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeMxExchange(value: String, change: SingleChangeCreateInfo, index: Int): Callback =
      bs.modState { s =>
        val recordData = change.record.copy(exchange = Some(value))
        val newSingleChange = change.copy(record = recordData)
        val newSingleChanges = s.createInfo.changes.patch(index, List(newSingleChange), 1)
        val newCreateInfo = s.createInfo.copy(changes = newSingleChanges)
        s.copy(createInfo = newCreateInfo)
      }

    def changeComments(value: String): Callback =
      bs.modState { s =>
        val comments =
          if (value.isEmpty) None
          else Some(value)

        s.copy(createInfo = s.createInfo.copy(comments = comments))
      }

    def toRecordDataDisplay(change: SingleChangeCreateInfo): String =
      change.`type` match {
        case address
            if address == "A" || address == "A+PTR" || address == "AAAA" || address == "AAAA+PTR" =>
          change.record.addressToString
        case cname if cname == "CNAME" => change.record.cnameToString
        case ptrdname if ptrdname == "PTR" => change.record.ptrdnameToString
        case text if text == "TXT" => change.record.textToString
      }

    def toMxInput(change: SingleChangeCreateInfo, index: Int): TagMod =
      <.div(
        <.label(
          ^.className := "batch-label",
          "Preference"
        ),
        <.input(
          ^.className := "form-control",
          ^.required := true,
          ^.`type` := "Number",
          ^.onChange ==> { e: ReactEventFromInput =>
            changeMxPreference(e.target.value, change, index)
          },
          ^.value := change.record.preferenceToString
        ),
        <.br,
        <.label(
          ^.className := "batch-label",
          "Exchange"
        ),
        <.input(
          ^.className := "form-control",
          ^.required := true,
          ^.onChange ==> { e: ReactEventFromInput =>
            changeMxExchange(e.target.value, change, index)
          },
          ^.value := change.record.exchangeToString
        ),
      )

    def toErrorClass(change: SingleChangeCreateInfo): String =
      change.errors match {
        case Some(errors) => if (errors.isEmpty) "" else "changeError"
        case None => ""
      }

    def submitBatchChange(P: Props, S: State): Callback =
      P.http.withConfirmation(
        "Are you sure you want to make this DNS Record Request?",
        Callback.lazily {
          val onSuccess = { (httpResponse: HttpResponse, _: Option[BatchChangeCreateInfo]) =>
            addNotification(
              P.http.toNotification("creating batch request", httpResponse)
            )
          }
          val onFailure = {
            httpResponse: HttpResponse =>
              val withErrors = CreateBatchChangeRoute.parse(httpResponse) match {
                case Some(batchChangeErrors) =>
                  S.createInfo.copy(changes = batchChangeErrors.changes)
                case None => S.createInfo
              }

              addNotification(P.http.toNotification(s"creating batch request", httpResponse)) >>
                bs.modState(_.copy(createInfo = withErrors))
          }
          P.http.post(CreateBatchChangeRoute, write(S.createInfo), onSuccess, onFailure)
        }
      )
  }
}
