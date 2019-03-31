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

package vinyldns.client.pages.zoneview.components.RecordDataInput

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ReactEventFromInput}
import vinyldns.client.models.record.{RecordSet, RecordSetCreateInfo}
import vinyldns.client.pages.zoneview.components.RecordDataInput.SrvInput.SrvField.SrvField
import vinyldns.client.pages.zoneview.components.RecordSetModal

import scala.util.Try

object SrvInput extends RecordDataInput {
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
              <.th(^.className := "table-col-20", "Priority"),
              <.th(^.className := "table-col-20", "Weight"),
              <.th(^.className := "table-col-20", "Port"),
              <.th("Target"),
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
                      ^.className := s"form-control test-priority-$index",
                      ^.`type` := "number",
                      ^.value := Try(rd.priority.get.toString).getOrElse(""),
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeSrvField(bs, e.target.value, index, SrvField.Priority)
                      },
                      ^.required := true
                    )
                  ),
                  <.td(
                    <.input(
                      ^.className := s"form-control test-weight-$index",
                      ^.`type` := "number",
                      ^.value := Try(rd.weight.get.toString).getOrElse(""),
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeSrvField(bs, e.target.value, index, SrvField.Weight)
                      },
                      ^.required := true
                    )
                  ),
                  <.td(
                    <.input(
                      ^.className := s"form-control test-port-$index",
                      ^.`type` := "number",
                      ^.value := Try(rd.port.get.toString).getOrElse(""),
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeSrvField(bs, e.target.value, index, SrvField.Port)
                      },
                      ^.required := true
                    )
                  ),
                  <.td(
                    <.input(
                      ^.className := s"form-control test-target-$index",
                      ^.value := rd.target.getOrElse(""),
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeSrvField(bs, e.target.value, index, SrvField.Target)
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
              <.td,
              <.td,
              <.td(
                <.button(
                  ^.className := "btn btn-sm btn-info fa fa-plus",
                  ^.`type` := "button",
                  ^.onClick --> addRow(bs)
                )
              )
            )
          )
        )
      )
    )

  object SrvField extends Enumeration {
    type SrvField = Value
    val Priority, Weight, Port, Target = Value
  }

  def changeSrvField(
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State],
      value: String,
      index: Int,
      field: SrvField): Callback =
    bs.modState { s =>
      val newRow = field match {
        case SrvField.Priority =>
          s.recordSet.records(index).copy(priority = Try(value.toInt).toOption)
        case SrvField.Weight => s.recordSet.records(index).copy(weight = Try(value.toInt).toOption)
        case SrvField.Port => s.recordSet.records(index).copy(port = Try(value.toInt).toOption)
        case SrvField.Target => s.recordSet.records(index).copy(target = Some(value))
      }
      val newRecordData = s.recordSet.records.updated(index, newRow)

      if (s.isUpdate) {
        val record = s.recordSet.asInstanceOf[RecordSet]
        s.copy(recordSet = record.copy(records = newRecordData))
      } else {
        val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
        s.copy(recordSet = record.copy(records = newRecordData))
      }
    }
}
