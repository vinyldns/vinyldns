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

package vinyldns.client.pages.zone.view.components.recordmodal.recordinput

import japgolly.scalajs.react.{BackendScope, Callback, ReactEventFromInput}
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.models.record.{RecordSetCreateInfo, RecordSetResponse}
import vinyldns.client.pages.zone.view.components.recordmodal.recordinput.MxInput.MxField.MxField
import vinyldns.client.pages.zone.view.components.recordmodal._

import scala.util.Try

object MxInput extends RecordDataInput {
  def toTagMod(
      S: RecordSetModal.State,
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State]): TagMod =
    <.div(
      ^.className := "form-group",
      <.label(
        ^.className := "col-md-3 col xs-12 control-label",
        "Record Data"
      ),
      <.div(
        ^.className := "col-md-6 col-xs-12",
        <.table(
          ^.className := "table table-condensed",
          <.thead(
            <.tr(
              <.th(^.className := "table-col-20", "Preference"),
              <.th("Exchange"),
              <.th
            )
          ),
          <.tbody(
            S.recordSet.records.zipWithIndex.map {
              case (rd, index) =>
                <.tr(
                  ^.key := index,
                  <.td(
                    <.input(
                      ^.className := s"form-control test-preference",
                      ^.`type` := "number",
                      ^.value := rd.preferenceToString,
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeMxField(bs, e.target.value, index, MxField.Preference)
                      },
                      ^.required := true
                    )
                  ),
                  <.td(
                    <.input(
                      ^.className := s"form-control test-exchange",
                      ^.value := rd.exchangeToString,
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeMxField(bs, e.target.value, index, MxField.Exchange)
                      },
                      ^.required := true
                    )
                  ),
                  <.td(
                    <.button(
                      ^.className := "btn btn-sm btn-danger fa fa-times",
                      ^.`type` := "button",
                      ^.onClick --> removeRow(bs, index)
                    )
                  )
                )
            }.toTagMod,
            <.tr(
              <.td,
              <.td,
              <.td(
                <.button(
                  ^.className := "btn btn-sm btn-info fa fa-plus test-add",
                  ^.`type` := "button",
                  ^.onClick --> addRow(bs)
                )
              )
            )
          )
        )
      )
    )

  object MxField extends Enumeration {
    type MxField = Value
    val Preference, Exchange = Value
  }

  def changeMxField(
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State],
      value: String,
      index: Int,
      field: MxField): Callback =
    bs.modState { s =>
      val newRow = field match {
        case MxField.Preference =>
          s.recordSet.records(index).copy(preference = Try(value.toInt).toOption)
        case MxField.Exchange => s.recordSet.records(index).copy(exchange = Some(value))
      }
      val newRecordData = s.recordSet.records.updated(index, newRow)

      if (s.isUpdate) {
        val record = s.recordSet.asInstanceOf[RecordSetResponse]
        s.copy(recordSet = record.copy(records = newRecordData))
      } else {
        val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
        s.copy(recordSet = record.copy(records = newRecordData))
      }
    }
}
