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

package models

import com.typesafe.config.Config
import models.DnsChangeNoticeType.DnsChangeNoticeType
import models.DnsChangeStatus.DnsChangeStatus
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.ConfigLoader
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.EnumerationReader._

import scala.collection.JavaConverters._

case class DnsChangeNotices(notices: JsValue)

object DnsChangeNotices {
  implicit val dnsChangeNoticeWrites: Writes[DnsChangeNotice] = Json.writes[DnsChangeNotice]
  implicit val configLoader: ConfigLoader[DnsChangeNotices] =
    new ConfigLoader[DnsChangeNotices] {
      def load(config: Config, path: String): DnsChangeNotices = {
        val notices = config
          .getConfigList(path)
          .asScala
          .map(formatDnsChangeNotice)
        DnsChangeNotices(Json.toJson(notices))
      }
    }

  def formatDnsChangeNotice(config: Config): DnsChangeNotice = {
    val status = config.as[DnsChangeStatus]("status")
    val alertType = config.as[DnsChangeNoticeType]("alertType")
    val text = config.getString("text")
    val hrefText = config.getOrElse[String]("hrefText", "")
    val href = config.getOrElse[String]("href", "")
    DnsChangeNotice(status, alertType, text, hrefText, href)
  }
}
case class DnsChangeNotice(
    status: DnsChangeStatus,
    alertType: DnsChangeNoticeType,
    text: String,
    hrefText: String,
    href: String
)

object DnsChangeStatus extends Enumeration {
  type DnsChangeStatus = Value
  val Cancelled, Complete, Failed, PartialFailure, PendingProcessing, PendingReview, Rejected,
      Scheduled = Value
}

object DnsChangeNoticeType extends Enumeration {
  type DnsChangeNoticeType = Value
  val info, success, warning, danger = Value
}
