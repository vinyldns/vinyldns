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

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.TagMod
import vinyldns.client.models.record.{RecordData, RecordSetCreateInfo, RecordSetResponse}
import vinyldns.client.pages.zone.view.components.recordmodal._

trait RecordDataInput {
  def toTagMod(
      S: RecordSetModal.State,
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State]): TagMod

  def removeRow(
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State],
      index: Int): Callback =
    bs.modState { s =>
      if (s.isUpdate) {
        val record = s.recordSet.asInstanceOf[RecordSetResponse]
        val newRecordData = s.recordSet.records.patch(index, Nil, 1)

        s.copy(recordSet = record.copy(records = newRecordData))
      } else {
        val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
        val newRecordData = s.recordSet.records.patch(index, Nil, 1)

        s.copy(recordSet = record.copy(records = newRecordData))
      }
    }

  def addRow(bs: BackendScope[RecordSetModal.Props, RecordSetModal.State]): Callback =
    bs.modState { s =>
      if (s.isUpdate) {
        val record = s.recordSet.asInstanceOf[RecordSetResponse]
        val newRecordData = s.recordSet.records :+ RecordData()

        s.copy(recordSet = record.copy(records = newRecordData))
      } else {
        val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
        val newRecordData = s.recordSet.records :+ RecordData()

        s.copy(recordSet = record.copy(records = newRecordData))
      }
    }
}

object RecordDataInput {
  def changeAddress(
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State],
      value: String): Callback =
    bs.modState { s =>
      val updatedRecords = RecordData.inputToAddresses(value)
      if (s.isUpdate) {
        val record = s.recordSet.asInstanceOf[RecordSetResponse]
        val modified = record.copy(records = updatedRecords)
        s.copy(recordSet = modified)
      } else {
        val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
        val modified = record.copy(records = updatedRecords)
        s.copy(recordSet = modified)
      }
    }

  def changeCname(
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State],
      value: String): Callback =
    bs.modState { s =>
      val recordData = RecordData.inputToCname(value)
      if (s.isUpdate) {
        val record = s.recordSet.asInstanceOf[RecordSetResponse]
        val modified = record.copy(records = recordData)
        s.copy(recordSet = modified)
      } else {
        val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
        val modified = record.copy(records = recordData)
        s.copy(recordSet = modified)
      }
    }

  def changePtrDName(
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State],
      value: String): Callback =
    bs.modState { s =>
      val recordData = RecordData.inputToPtrdnames(value)
      if (s.isUpdate) {
        val record = s.recordSet.asInstanceOf[RecordSetResponse]
        val modified = record.copy(records = recordData)
        s.copy(recordSet = modified)
      } else {
        val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
        val modified = record.copy(records = recordData)
        s.copy(recordSet = modified)
      }
    }

  def changeNsDName(
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State],
      value: String): Callback =
    bs.modState { s =>
      val recordData = RecordData.inputToNsdnames(value)
      if (s.isUpdate) {
        val record = s.recordSet.asInstanceOf[RecordSetResponse]
        val modified = record.copy(records = recordData)
        s.copy(recordSet = modified)
      } else {
        val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
        val modified = record.copy(records = recordData)
        s.copy(recordSet = modified)
      }
    }

  def changeText(
      bs: BackendScope[RecordSetModal.Props, RecordSetModal.State],
      value: String): Callback =
    bs.modState { s =>
      val recordData = RecordData.inputToTexts(value)
      if (s.isUpdate) {
        val record = s.recordSet.asInstanceOf[RecordSetResponse]
        val modified = record.copy(records = recordData)
        s.copy(recordSet = modified)
      } else {
        val record = s.recordSet.asInstanceOf[RecordSetCreateInfo]
        val modified = record.copy(records = recordData)
        s.copy(recordSet = modified)
      }
    }
}
