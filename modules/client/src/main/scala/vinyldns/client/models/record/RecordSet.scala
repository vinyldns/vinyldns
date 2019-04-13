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
import vinyldns.client.models.OptionRW
import vinyldns.core.domain.record.RecordType

import scala.util.Try

trait BasicRecordSetInfo {
  def name: String
  def zoneId: String
  def `type`: RecordType.RecordType
  def ttl: Int
  def records: List[RecordData]
  def ownerGroupId: Option[String]
}

trait RecordSetTypeRW {
  implicit val recordTypeRW: ReadWriter[RecordType.RecordType] =
    readwriter[ujson.Value]
      .bimap[RecordType.RecordType](
        fromType => ujson.Value.JsonableString(fromType.toString),
        toType => {
          val raw = toType.toString().replaceAll("^\"|\"$", "")
          RecordType.withName(raw)
        }
      )
}

case class RecordSetCreateInfo(
    zoneId: String,
    `type`: RecordType.RecordType,
    name: String,
    ttl: Int,
    records: List[RecordData],
    ownerGroupId: Option[String])
    extends BasicRecordSetInfo

object RecordSetCreateInfo extends RecordSetTypeRW with OptionRW {
  implicit val rw: ReadWriter[RecordSetCreateInfo] = macroRW

  def apply(zoneId: String): RecordSetCreateInfo =
    new RecordSetCreateInfo(zoneId, RecordType.A, "", 300, List(RecordData()), None)
}

case class RecordSet(
    id: String,
    `type`: RecordType.RecordType,
    zoneId: String,
    name: String,
    ttl: Int,
    status: String,
    records: List[RecordData],
    account: String,
    created: String,
    accessLevel: Option[String] = None,
    ownerGroupId: Option[String] = None,
    ownerGroupName: Option[String] = None)
    extends BasicRecordSetInfo {

  def canUpdate(zoneName: String): Boolean =
    (this.accessLevel == Some("Update") || this.accessLevel == Some("Delete")) &&
      this.`type` != RecordType.SOA &&
      !(this.`type` == RecordType.NS && this.name == zoneName)

  def canDelete(zoneName: String): Boolean =
    this.accessLevel == Some("Delete") &&
      this.`type` != RecordType.SOA &&
      !(this.`type` == RecordType.NS && this.name == zoneName)

  def recordDataDisplay: VdomElement = // scalastyle:ignore
    (this.records, this.`type`) match {
      case (aList, RecordType.A | RecordType.AAAA) =>
        <.ul(
          ^.className := "table-cell-list",
          aList.map { rd =>
            <.li(s"${rd.addressToString}")
          }.toTagMod
        )
      case (cname, RecordType.CNAME) =>
        <.p(s"${Try(cname.head.cnameToString).getOrElse("")}")
      case (dsList, RecordType.DS) =>
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
      case (mxList, RecordType.MX) =>
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
      case (nsList, RecordType.NS) =>
        <.ul(
          ^.className := "table-cell-list",
          nsList.map { rd =>
            <.li(s"${rd.nsdnameToString}")
          }.toTagMod
        )
      case (ptrList, RecordType.PTR) =>
        <.ul(
          ^.className := "table-cell-list",
          ptrList.map { rd =>
            <.li(s"${rd.ptrdnameToString}")
          }.toTagMod
        )
      case (soa, RecordType.SOA) =>
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
      case (spfOrTxtList, RecordType.SPF | RecordType.TXT) =>
        <.ul(
          ^.className := "table-cell-list",
          spfOrTxtList.map { rd =>
            <.li(s"${rd.textToString}")
          }.toTagMod
        )
      case (srvList, RecordType.SRV) =>
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
      case (sshfpList, RecordType.SSHFP) =>
        <.ul(
          ^.className := "table-cell-list",
          sshfpList.map { rd =>
            <.li(s"""
                 |Algorithm: ${rd.algorithmToString} |
                 | Type: ${rd.typeToString} |
                 | Fingerprint: ${rd.fingerprintToString}""".stripMargin.replaceAll("\n", ""))
          }.toTagMod
        )
      case (other, _) => <.p(other.toString())
    }
}

object RecordSet extends OptionRW with RecordSetTypeRW {
  implicit val rw: ReadWriter[RecordSet] = macroRW
}
