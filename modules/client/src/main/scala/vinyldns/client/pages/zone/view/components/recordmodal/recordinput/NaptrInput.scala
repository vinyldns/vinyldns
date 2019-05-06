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

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ReactEventFromInput}
import vinyldns.client.models.record.{RecordData, RecordSetCreateInfo, RecordSetResponse}
import vinyldns.client.pages.zone.view.components.recordmodal._
import vinyldns.client.pages.zone.view.components.recordmodal.recordinput.NaptrInput.NaptrField.NaptrField

import scala.util.Try

object NaptrInput extends RecordDataInput {
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
        ^.className := "col-md-8 col-xs-12",
        <.table(
          ^.className := "table table-condensed",
          <.thead(
            <.tr(
              <.th("Order"),
              <.th("Preference"),
              <.th("Flags"),
              <.th("Service"),
              <.th("Regexp"),
              <.th("Replacement"),
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
                      ^.className := s"form-control test-order",
                      ^.`type` := "number",
                      ^.value := rd.orderToString,
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeNaptrField(bs, e.target.value, index, NaptrField.Order, rd)
                      },
                      ^.required := true
                    )
                  ),
                  <.td(
                    <.input(
                      ^.className := s"form-control test-preference",
                      ^.`type` := "number",
                      ^.value := rd.preferenceToString,
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeNaptrField(bs, e.target.value, index, NaptrField.Preference, rd)
                      },
                      ^.required := true
                    )
                  ),
                  <.td(
                    <.input(
                      ^.className := s"form-control test-flags",
                      ^.value := rd.flagsToString,
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeNaptrField(bs, e.target.value, index, NaptrField.Flags, rd)
                      }
                    )
                  ),
                  <.td(
                    <.input(
                      ^.className := s"form-control test-service",
                      ^.value := rd.serviceToString,
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeNaptrField(bs, e.target.value, index, NaptrField.Service, rd)
                      }
                    )
                  ),
                  <.td(
                    <.input(
                      ^.className := s"form-control test-regexp",
                      ^.value := rd.regexpToString,
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeNaptrField(bs, e.target.value, index, NaptrField.Regexp, rd)
                      }
                    )
                  ),
                  <.td(
                    <.input(
                      ^.className := s"form-control test-replacement",
                      ^.value := rd.replacementToString,
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeNaptrField(bs, e.target.value, index, NaptrField.Replacement, rd)
                      }
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
              <.td,
              <.td,
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

  object NaptrField extends Enumeration {
    type NaptrField = Value
    val Order, Preference, Flags, Service, Regexp, Replacement = Value
  }

  /*
    The way NAPTR is implemented in the API is that all fields have to be there in the JSON.
    Empty Strings are valid, so they are set with empty Strings as defaults so they are included in the JSON if blank.

    Eventually the API should be looked at since not all those fields are required
   */
  def setDefaults(
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State],
      recordData: RecordData,
      index: Int): Callback = {
    val flags = if (recordData.flags.isEmpty) Some("") else recordData.flags
    val service = if (recordData.service.isEmpty) Some("") else recordData.service
    val regexp = if (recordData.regexp.isEmpty) Some("") else recordData.regexp
    val replacement = if (recordData.replacement.isEmpty) Some("") else recordData.replacement

    val withDefaults =
      recordData.copy(flags = flags, service = service, regexp = regexp, replacement = replacement)
    if (withDefaults.equals(recordData)) Callback.empty
    else
      bs.modState { s =>
        val newRecordData = s.recordSet.records.updated(index, withDefaults)
        if (s.isUpdate) {
          val record = s.recordSet.asInstanceOf[RecordSetResponse]
          s.copy(recordSet = record.copy(records = newRecordData))
        } else {
          val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
          s.copy(recordSet = record.copy(records = newRecordData))
        }
      }
  }

  def changeNaptrField(
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State],
      value: String,
      index: Int,
      field: NaptrField,
      recordData: RecordData): Callback =
    setDefaults(bs, recordData, index) >> bs.modState { s =>
      val newRow = field match {
        case NaptrField.Order => s.recordSet.records(index).copy(order = Try(value.toInt).toOption)
        case NaptrField.Preference =>
          s.recordSet.records(index).copy(preference = Try(value.toInt).toOption)
        case NaptrField.Flags => s.recordSet.records(index).copy(flags = Some(value))
        case NaptrField.Service => s.recordSet.records(index).copy(service = Some(value))
        case NaptrField.Regexp => s.recordSet.records(index).copy(regexp = Some(value))
        case NaptrField.Replacement => s.recordSet.records(index).copy(replacement = Some(value))
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
