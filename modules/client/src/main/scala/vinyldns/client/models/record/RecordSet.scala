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

package vinyldns.client.models.record

import scalacss.ScalaCssReact._
import japgolly.scalajs.react.vdom.html_<^.{^, _}
import upickle.default._
import vinyldns.client.css.GlobalStyle

import scala.util.Try

trait BasicRecordSetInfo {
  def name: String
  def zoneId: String
  def `type`: String
  def ttl: Int
  def records: List[RecordData]
}

case class RecordSetCreateInfo(
    zoneId: String,
    `type`: String,
    name: String,
    ttl: Int,
    records: List[RecordData])
    extends BasicRecordSetInfo

object RecordSetCreateInfo {
  implicit val rw: ReadWriter[RecordSetCreateInfo] = macroRW

  def apply(zoneId: String): RecordSetCreateInfo =
    new RecordSetCreateInfo(zoneId, "A", "", 300, List(RecordData()))
}

case class RecordSet(
    id: String,
    `type`: String,
    zoneId: String,
    name: String,
    ttl: Int,
    status: String,
    records: List[RecordData],
    account: String,
    accessLevel: String,
    created: String)
    extends BasicRecordSetInfo {

  def recordDataDisplay: VdomElement = // scalastyle:ignore
    this.records match {
      case aList if this.`type` == "A" || this.`type` == "AAAA" =>
        <.ul(
          ^.className := "table-cell-list",
          aList.map { rd =>
            <.li(s"${rd.addressToString}")
          }.toTagMod
        )
      case cname if this.`type` == "CNAME" => <.p(s"${Try(cname.head.cnameToString).getOrElse("")}")
      case dsList if this.`type` == "DS" =>
        <.ul(
          ^.className := "table-cell-list",
          dsList.map { rd =>
            <.li(
              s"""
                 |KeyTag: ${rd.keytagToString} |
                 | Algorithm: ${rd.algorithmToString} |
                 | DigestType: ${rd.digesttypeToString} |
                 | Digest: ${rd.digestToString}""".stripMargin.replaceAll("\n", "")
            )
          }.toTagMod
        )
      case mxList if this.`type` == "MX" =>
        <.ul(
          ^.className := "table-cell-list",
          mxList.map { rd =>
            <.li(
              s"""
                 |Preference: ${rd.preferenceToString} |
                 | Exchange: ${rd.exchangeToString}""".stripMargin.replaceAll("\n", "")
            )
          }.toTagMod
        )
      case nsList if this.`type` == "NS" =>
        <.ul(
          ^.className := "table-cell-list",
          nsList.map { rd =>
            <.li(s"${rd.nsdnameToString}")
          }.toTagMod
        )
      case ptrList if this.`type` == "PTR" =>
        <.ul(
          ^.className := "table-cell-list",
          ptrList.map { rd =>
            <.li(s"${rd.ptrdnameToString}")
          }.toTagMod
        )
      case soa if this.`type` == "SOA" =>
        <.table(
          <.tbody(
            <.tr(<.td("Mname:"), <.td(s"${Try(soa.head.mnameToString).getOrElse("")}")),
            <.tr(<.td("Rname:"), <.td(s"${Try(soa.head.rnameToString).getOrElse("")}")),
            <.tr(<.td("Serial:"), <.td(s"${Try(soa.head.serialToString).getOrElse("")}")),
            <.tr(<.td("Refresh:"), <.td(s"${Try(soa.head.refreshToString).getOrElse("")}")),
            <.tr(<.td("Retry:"), <.td(s"${Try(soa.head.retryToString).getOrElse("")}")),
            <.tr(<.td("Expire:"), <.td(s"${Try(soa.head.expireToString).getOrElse("")}")),
            <.tr(
              <.td(GlobalStyle.Styles.keepWhitespace, "Minimum:   "),
              <.td(s"${Try(soa.head.minimumToString).getOrElse("")}"))
          )
        )
      case spfOrTxtList if this.`type` == "SPF" || this.`type` == "TXT" =>
        <.ul(
          ^.className := "table-cell-list",
          spfOrTxtList.map { rd =>
            <.li(s"${rd.textToString}")
          }.toTagMod
        )
      case srvList if this.`type` == "SRV" =>
        <.ul(
          ^.className := "table-cell-list",
          srvList.map { rd =>
            <.li(s"""
                 |Priority: ${rd.priorityToString} |
                 | Weight: ${rd.weightToString} |
                 | Port: ${rd.portToString} |
                 | Target: ${rd.targetToString}""".stripMargin.replaceAll("\n", ""))
          }.toTagMod
        )
      case sshfpList if this.`type` == "SSHFP" =>
        <.ul(
          ^.className := "table-cell-list",
          sshfpList.map { rd =>
            <.li(s"""
                 |Algorithm: ${rd.algorithmToString} |
                 | Type: ${rd.typeToString} |
                 | Fingerprint: ${rd.fingerprintToString}""".stripMargin.replaceAll("\n", ""))
          }.toTagMod
        )
      case _ => <.p
    }
}

object RecordSet {
  implicit val rw: ReadWriter[RecordSet] = macroRW
}
