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
import japgolly.scalajs.react.vdom.TagMod
import japgolly.scalajs.react.vdom.html_<^.{^, _}
import upickle.default._
import vinyldns.client.css.GlobalStyle

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

  def recordDataDisplay: TagMod = // scalastyle:ignore
    this.records match {
      case aList if this.`type` == "A" || this.`type` == "AAAA" =>
        <.ul(
          ^.className := "table-cell-list",
          aList.map { rd =>
            <.li(rd.address)
          }.toTagMod
        )
      case cname if this.`type` == "CNAME" => cname.head.cname
      case dsList if this.`type` == "DS" =>
        <.ul(
          ^.className := "table-cell-list",
          dsList.map { rd =>
            s"KeyTag: ${rd.keytag} | Algorithm: ${rd.algorithm} | DigestType: ${rd.digesttype} | Digest: ${rd.digest}"
          }.toTagMod
        )
      case mxList if this.`type` == "MX" =>
        <.ul(
          ^.className := "table-cell-list",
          mxList.map { rd =>
            s"Preference: ${rd.preference} | Exhange: ${rd.exchange}"
          }.toTagMod
        )
      case nsList if this.`type` == "NS" =>
        <.ul(
          ^.className := "table-cell-list",
          nsList.map { rd =>
            <.li(rd.nsdname)
          }.toTagMod
        )
      case ptrList if this.`type` == "PTR" =>
        <.ul(
          ^.className := "table-cell-list",
          ptrList.map { rd =>
            <.li(rd.ptrdname)
          }.toTagMod
        )
      case soa if this.`type` == "SOA" =>
        <.table(
          <.tbody(
            <.tr(<.td("Mname:"), <.td(soa.head.mname)),
            <.tr(<.td("Rname:"), <.td(soa.head.rname)),
            <.tr(<.td("Serial:"), <.td(soa.head.serial)),
            <.tr(<.td("Refresh:"), <.td(soa.head.refresh)),
            <.tr(<.td("Retry:"), <.td(soa.head.retry)),
            <.tr(<.td("Expire:"), <.td(soa.head.expire)),
            <.tr(<.td(GlobalStyle.Styles.keepWhitespace, "Minimum:   "), <.td(soa.head.minimum))
          )
        )
      case spfList if this.`type` == "SPF" =>
        <.ul(
          ^.className := "table-cell-list",
          spfList.map { rd =>
            <.li(rd.text)
          }.toTagMod
        )
      case srvList if this.`type` == "SRV" =>
        <.ul(
          ^.className := "table-cell-list",
          srvList.map { rd =>
            <.li(
              s"Priority: ${rd.priority} | Weight: ${rd.weight} | Port: ${rd.port} | Target: ${rd.target}")
          }.toTagMod
        )
      case sshfpList if this.`type` == "SSHFP" =>
        <.ul(
          ^.className := "table-cell-list",
          sshfpList.map { rd =>
            <.li(
              s"Algorithm: ${rd.algorithm} | Type: ${rd.`type`} | Fingerprint: ${rd.fingerprint}")
          }.toTagMod
        )
      case _ => ""
    }
}

object RecordSet {
  implicit val rw: ReadWriter[RecordSet] = macroRW
}
