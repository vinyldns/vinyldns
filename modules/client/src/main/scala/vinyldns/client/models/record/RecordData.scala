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
import vinyldns.core.domain.record.RecordType.RecordType

import scala.util.Try

case class RecordData(
    address: Option[String] = None,
    cname: Option[String] = None,
    preference: Option[Int] = None,
    exchange: Option[String] = None,
    nsdname: Option[String] = None,
    ptrdname: Option[String] = None,
    mname: Option[String] = None,
    rname: Option[String] = None,
    serial: Option[Long] = None,
    refresh: Option[Long] = None,
    retry: Option[Long] = None,
    expire: Option[Long] = None,
    minimum: Option[Long] = None,
    text: Option[String] = None,
    priority: Option[Int] = None,
    weight: Option[Int] = None,
    port: Option[Int] = None,
    target: Option[String] = None,
    algorithm: Option[Int] = None,
    `type`: Option[Int] = None,
    fingerprint: Option[String] = None,
    keytag: Option[Int] = None,
    digesttype: Option[Int] = None,
    digest: Option[String] = None,
    order: Option[Int] = None,
    flags: Option[String] = None,
    service: Option[String] = None,
    regexp: Option[String] = None,
    replacement: Option[String] = None) {

  // convert to strings for display purposes
  def addressToString: String = this.address.getOrElse("")
  def cnameToString: String = this.cname.getOrElse("")
  def preferenceToString: String = Try(this.preference.get.toString).getOrElse("")
  def exchangeToString: String = this.exchange.getOrElse("")
  def nsdnameToString: String = this.nsdname.getOrElse("")
  def ptrdnameToString: String = this.ptrdname.getOrElse("")
  def mnameToString: String = this.mname.getOrElse("")
  def rnameToString: String = this.rname.getOrElse("")
  def serialToString: String = Try(this.serial.get.toString).getOrElse("")
  def refreshToString: String = Try(this.refresh.get.toString).getOrElse("")
  def retryToString: String = Try(this.retry.get.toString).getOrElse("")
  def expireToString: String = Try(this.expire.get.toString).getOrElse("")
  def minimumToString: String = Try(this.minimum.get.toString).getOrElse("")
  def textToString: String = this.text.getOrElse("")
  def priorityToString: String = Try(this.priority.get.toString).getOrElse("")
  def weightToString: String = Try(this.weight.get.toString).getOrElse("")
  def portToString: String = Try(this.port.get.toString).getOrElse("")
  def targetToString: String = this.target.getOrElse("")
  def algorithmToString: String = Try(this.algorithm.get.toString).getOrElse("")
  def typeToString: String = Try(this.`type`.get.toString).getOrElse("")
  def fingerprintToString: String = this.fingerprint.getOrElse("")
  def keytagToString: String = Try(this.keytag.get.toString).getOrElse("")
  def digesttypeToString: String = Try(this.digesttype.get.toString).getOrElse("")
  def digestToString: String = this.digest.getOrElse("")
  def orderToString: String = Try(this.order.get.toString).getOrElse("")
  def flagsToString: String = this.flags.getOrElse("")
  def serviceToString: String = this.service.getOrElse("")
  def regexpToString: String = this.regexp.getOrElse("")
  def replacementToString: String = this.replacement.getOrElse("")
}

object RecordData extends OptionRW {
  implicit val rw: ReadWriter[RecordData] = macroRW

  def inputToAddresses(input: String): List[RecordData] = {
    val recordData = input
      .split("\\r?\\n")
      .map(l => RecordData(address = Some(l)))
      .toList
    val withNewLine =
      if (input.endsWith("\n")) recordData :+ RecordData(address = Some("")) else recordData
    withNewLine
  }

  def addressesToInput(records: List[RecordData]): Option[String] =
    Some(records.map(_.addressToString).mkString("\n"))

  def inputToCname(input: String): List[RecordData] = List(RecordData(cname = Some(input)))

  def cnameToInput(records: List[RecordData]): Option[String] =
    Try(records.head.cnameToString).toOption

  def inputToPtrdnames(input: String): List[RecordData] = {
    val recordData = input
      .split("\\r?\\n")
      .map(l => RecordData(ptrdname = Some(l)))
      .toList
    val withNewLine =
      if (input.endsWith("\n")) recordData :+ RecordData(ptrdname = Some("")) else recordData
    withNewLine
  }

  def ptrdnamesToInput(records: List[RecordData]): Option[String] =
    Some(records.map(_.ptrdnameToString).mkString("\n"))

  def inputToNsdnames(input: String): List[RecordData] = {
    val recordData = input
      .split("\\r?\\n")
      .map(l => RecordData(nsdname = Some(l)))
      .toList
    val withNewLine =
      if (input.endsWith("\n")) recordData :+ RecordData(nsdname = Some("")) else recordData
    withNewLine
  }

  def nsdnamesToInput(records: List[RecordData]): Option[String] =
    Some(records.map(_.nsdnameToString).mkString("\n"))

  def inputToTexts(input: String): List[RecordData] = {
    val recordData = input
      .split("\\r?\\n")
      .map(l => RecordData(text = Some(l)))
      .toList
    val withNewLine =
      if (input.endsWith("\n")) recordData :+ RecordData(text = Some("")) else recordData
    withNewLine
  }

  def textsToInput(records: List[RecordData]): Option[String] =
    Some(records.map(_.textToString).mkString("\n"))

  def toDisplay(records: List[RecordData], recordType: RecordType): VdomElement = // scalastyle:ignore
    (records, recordType) match {
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
      case (naptrList, RecordType.NAPTR) =>
        <.ul(
          ^.className := "table-cell-list",
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
