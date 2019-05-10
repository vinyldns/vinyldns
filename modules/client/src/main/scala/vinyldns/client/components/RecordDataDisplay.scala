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
  case class Props(recordData: List[RecordData], recordType: RecordType)
  case class State(showMore: Boolean = false)

  val component = ScalaComponent
    .builder[Props]("RecordDataDisplay")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(props: Props): Unmounted[Props, State, Backend] = component(props)

  class Backend(bs: BackendScope[Props, State]) {
    def render(P: Props, S: State): VdomElement =
      toDisplayList(P.recordData, P.recordType, S.showMore)

    def toDisplayList(
        records: List[RecordData],
        recordType: RecordType,
        showMore: Boolean): VdomElement = {
      val recordsToShow = if (showMore) records else records.take(4)

      val elements = (recordsToShow, recordType) match {
        case (aList, RecordType.A | RecordType.AAAA) =>
          aList.map { rd =>
            <.li(s"${rd.addressToString}")
          }
        case (dsList, RecordType.DS) =>
          dsList.map { rd =>
            <.li(
              s"""
                   |KeyTag: ${rd.keytagToString} |
                 | Algorithm: ${rd.algorithmToString} |
                 | DigestType: ${rd.digesttypeToString} |
                 | Digest: ${rd.digestToString}""".stripMargin.replaceAll("\n", "")
            )
          }
        case (naptrList, RecordType.NAPTR) =>
          naptrList.map { rd =>
            <.li(
              s"""
                   |Order: ${rd.orderToString} |
                 | Preference: ${rd.preferenceToString} |
                 | Flags: ${rd.flagsToString} |
                 | Service: ${rd.serviceToString} |
                 | Regexp: ${rd.regexpToString} |
                 | Replacement: ${rd.replacementToString}""".stripMargin.replaceAll("\n", "")
            )
          }
        case (nsList, RecordType.NS) =>
          nsList.map { rd =>
            <.li(s"${rd.nsdnameToString}")
          }
        case (mxList, RecordType.MX) =>
          mxList.map { rd =>
            <.li(
              s"""
                   |Preference: ${rd.preferenceToString} |
                 | Exchange: ${rd.exchangeToString}""".stripMargin.replaceAll("\n", "")
            )
          }
        case (ptrList, RecordType.PTR) =>
          ptrList.map { rd =>
            <.li(s"${rd.ptrdnameToString}")
          }
        case (spfOrTxtList, RecordType.SPF | RecordType.TXT) =>
          spfOrTxtList.map { rd =>
            <.li(s"${rd.textToString}")
          }
        case (srvList, RecordType.SRV) =>
          srvList.map { rd =>
            <.li(s"""
                      |Priority: ${rd.priorityToString} |
                 | Weight: ${rd.weightToString} |
                 | Port: ${rd.portToString} |
                 | Target: ${rd.targetToString}""".stripMargin.replaceAll("\n", ""))
          }
        case (sshfpList, RecordType.SSHFP) =>
          sshfpList.map { rd =>
            <.li(s"""
                      |Algorithm: ${rd.algorithmToString} |
                 | Type: ${rd.typeToString} |
                 | Fingerprint: ${rd.fingerprintToString}""".stripMargin.replaceAll("\n", ""))
          }
        case (cname, RecordType.CNAME) =>
          cname.map { rd =>
            <.li(s"${rd.cnameToString}")
          }
        case (soa, RecordType.SOA) =>
          soa.map { rd =>
            <.table(
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
          }
        case (other, _) =>
          other.map { rd =>
            <.li(rd.toString())
          }
      }

      if (records.length > 4)
        <.ul(
          ^.className := "table-cell-list",
          elements.toTagMod,
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
          elements.toTagMod
        )
    }
  }
}
