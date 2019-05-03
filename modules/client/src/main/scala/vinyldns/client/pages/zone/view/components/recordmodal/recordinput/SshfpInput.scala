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
import vinyldns.client.models.record.{RecordSetCreateInfo, RecordSetResponse}
import vinyldns.client.pages.zone.view.components.recordmodal.recordinput.SshfpInput.SshfpField.SshfpField
import vinyldns.client.pages.zone.view.components.recordmodal._

import scala.util.Try

object SshfpInput extends RecordDataInput {
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
              <.th("Algorithm"),
              <.th("Type"),
              <.th("Fingerprint"),
              <.th
            )
          ),
          <.tbody(
            S.recordSet.records.zipWithIndex.map {
              case (rd, index) =>
                <.tr(
                  ^.key := index,
                  <.td(
                    <.select(
                      ^.className := s"form-control test-algorithm",
                      ^.value := rd.algorithmToString,
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeSshfpField(bs, e.target.value, index, SshfpField.Algorithm)
                      },
                      List("" -> "", "1" -> "RSA", "2" -> "DSA", "3" -> "ECDSA", "4" -> "Ed25519").map {
                        case (value, display) => <.option(^.value := value, s"($value) $display")
                      }.toTagMod,
                      ^.required := true
                    )
                  ),
                  <.td(
                    <.select(
                      ^.className := s"form-control test-rd-type",
                      ^.value := rd.typeToString,
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeSshfpField(bs, e.target.value, index, SshfpField.Type)
                      },
                      List("" -> "", "1" -> "SHA-1", "2" -> "SHA-256").map {
                        case (value, display) => <.option(^.value := value, s"($value) $display")
                      }.toTagMod,
                      ^.required := true
                    )
                  ),
                  <.td(
                    <.input(
                      ^.className := s"form-control test-fingerprint",
                      ^.value := rd.fingerprintToString,
                      ^.onChange ==> { e: ReactEventFromInput =>
                        changeSshfpField(bs, e.target.value, index, SshfpField.Fingerprint)
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

  object SshfpField extends Enumeration {
    type SshfpField = Value
    val Algorithm, Type, Fingerprint = Value
  }

  def changeSshfpField(
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State],
      value: String,
      index: Int,
      field: SshfpField): Callback =
    bs.modState { s =>
      val newRow = field match {
        case SshfpField.Algorithm =>
          s.recordSet.records(index).copy(algorithm = Try(value.toInt).toOption)
        case SshfpField.Type => s.recordSet.records(index).copy(`type` = Try(value.toInt).toOption)
        case SshfpField.Fingerprint => s.recordSet.records(index).copy(fingerprint = Some(value))
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
