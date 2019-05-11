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

package vinyldns.client.components

import vinyldns.client.models.record.RecordData
import scalacss.ScalaCssReact._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import vinyldns.client.css.GlobalStyle
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.record.RecordType.RecordType

object RecordDataDisplay {
  case class Props(recordData: List[RecordData], recordType: RecordType, recordId: String)
  case class State(showMore: Boolean = false)

  val component = ScalaComponent
    .builder[Props]("RecordDataDisplay")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      toDisplayList(P.recordData, P.recordType, P.recordId, S.showMore)

    def toDisplayList(
        records: List[RecordData],
        recordType: RecordType,
        recordId: String,
        showMore: Boolean): VdomElement = {
      val recordsToShow = if (showMore) records else records.take(4)

      val elements = recordType match {
        case RecordType.A | RecordType.AAAA => toDisplayA(recordsToShow, recordId)
        case RecordType.DS => toDisplayDS(recordsToShow, recordId)
        case RecordType.NAPTR => toDisplayNAPTR(recordsToShow, recordId)
        case RecordType.NS => toDisplayNS(recordsToShow, recordId)
        case RecordType.MX => toDisplayMX(recordsToShow, recordId)
        case RecordType.PTR => toDisplayPTR(recordsToShow, recordId)
        case RecordType.SPF | RecordType.TXT => toDisplayTXT(recordsToShow, recordId)
        case RecordType.SRV => toDisplaySRV(recordsToShow, recordId)
        case RecordType.SSHFP => toDisplaySSHFP(recordsToShow, recordId)
        case RecordType.CNAME => toDisplayCNAME(recordsToShow, recordId)
        case RecordType.SOA => toDisplaySOA(recordsToShow, recordId)
        case _ => <.li("unsupported type")
      }

      if (records.length > 4)
        <.ul(
          ^.className := "table-cell-list",
          elements,
          if (showMore)
            <.li(
              <.a(
                ^.className := "test-less",
                GlobalStyle.Styles.cursorPointer,
                ^.onClick --> bs.modState(_.copy(showMore = false)),
                "less...")
            )
          else
            <.li(
              <.a(
                ^.className := "test-more",
                GlobalStyle.Styles.cursorPointer,
                ^.onClick --> bs.modState(_.copy(showMore = true)),
                "more...")
            )
        )
      else
        <.ul(
          ^.className := "table-cell-list",
          elements
        )
    }

    def toDisplayA(records: List[RecordData], recordId: String): TagMod =
      records.zipWithIndex.map {
        case (rd, index) =>
          <.li(^.key := s"$recordId-$index", s"${rd.addressToString}")
      }.toTagMod

    def toDisplayDS(records: List[RecordData], recordId: String): TagMod =
      records.zipWithIndex.map {
        case (rd, index) =>
          <.li(
            ^.key := s"$recordId-$index",
            s"""
               |KeyTag: ${rd.keytagToString} |
                 | Algorithm: ${rd.algorithmToString} |
                 | DigestType: ${rd.digesttypeToString} |
                 | Digest: ${rd.digestToString}""".stripMargin.replaceAll("\n", "")
          )
      }.toTagMod

    def toDisplayNAPTR(records: List[RecordData], recordId: String): TagMod =
      records.zipWithIndex.map {
        case (rd, index) =>
          <.li(
            ^.key := s"$recordId-$index",
            s"""
               |Order: ${rd.orderToString} |
                 | Preference: ${rd.preferenceToString} |
                 | Flags: ${rd.flagsToString} |
                 | Service: ${rd.serviceToString} |
                 | Regexp: ${rd.regexpToString} |
                 | Replacement: ${rd.replacementToString}""".stripMargin.replaceAll("\n", "")
          )
      }.toTagMod

    def toDisplayNS(records: List[RecordData], recordId: String): TagMod =
      records.zipWithIndex.map {
        case (rd, index) =>
          <.li(^.key := s"$recordId-$index", s"${rd.nsdnameToString}")
      }.toTagMod

    def toDisplayMX(records: List[RecordData], recordId: String): TagMod =
      records.zipWithIndex.map {
        case (rd, index) =>
          <.li(
            ^.key := s"$recordId-$index",
            s"""
               |Preference: ${rd.preferenceToString} |
                 | Exchange: ${rd.exchangeToString}""".stripMargin.replaceAll("\n", "")
          )
      }.toTagMod

    def toDisplayPTR(records: List[RecordData], recordId: String): TagMod =
      records.zipWithIndex.map {
        case (rd, index) =>
          <.li(^.key := s"$recordId-$index", s"${rd.ptrdnameToString}")
      }.toTagMod

    def toDisplayTXT(records: List[RecordData], recordId: String): TagMod =
      records.zipWithIndex.map {
        case (rd, index) =>
          <.li(^.key := s"$recordId-$index", s"${rd.textToString}")
      }.toTagMod

    def toDisplaySRV(records: List[RecordData], recordId: String): TagMod =
      records.zipWithIndex.map {
        case (rd, index) =>
          <.li(
            ^.key := s"$recordId-$index",
            s"""
                |Priority: ${rd.priorityToString} |
                 | Weight: ${rd.weightToString} |
                 | Port: ${rd.portToString} |
                 | Target: ${rd.targetToString}""".stripMargin.replaceAll("\n", "")
          )
      }.toTagMod

    def toDisplaySSHFP(records: List[RecordData], recordId: String): TagMod =
      records.zipWithIndex.map {
        case (rd, index) =>
          <.li(
            ^.key := s"$recordId-$index",
            s"""
                |Algorithm: ${rd.algorithmToString} |
                 | Type: ${rd.typeToString} |
                 | Fingerprint: ${rd.fingerprintToString}""".stripMargin.replaceAll("\n", "")
          )
      }.toTagMod

    def toDisplayCNAME(records: List[RecordData], recordId: String): TagMod =
      records.zipWithIndex.map {
        case (rd, index) =>
          <.li(^.key := s"$recordId-$index", s"${rd.cnameToString}")
      }.toTagMod

    def toDisplaySOA(records: List[RecordData], recordId: String): TagMod =
      records.zipWithIndex.map {
        case (rd, index) =>
          <.table(
            ^.key := s"$recordId-$index",
            <.tbody(
              <.tr(<.td("Mname:"), <.td(s"${rd.mnameToString}")),
              <.tr(<.td("Rname:"), <.td(s"${rd.rnameToString}")),
              <.tr(<.td("Serial:"), <.td(s"${rd.serialToString}")),
              <.tr(<.td("Refresh:"), <.td(s"${rd.refreshToString}")),
              <.tr(<.td("Retry:"), <.td(s"${rd.retryToString}")),
              <.tr(<.td("Expire:"), <.td(s"${rd.expireToString}")),
              <.tr(
                <.td(GlobalStyle.Styles.keepWhitespace, "Minimum:   "),
                <.td(s"${rd.minimumToString}"))
            )
          )
      }.toTagMod
  }
}
