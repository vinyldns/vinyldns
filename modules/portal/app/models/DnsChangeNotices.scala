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
import play.api.libs.json.{JsValue, Json}
import play.api.{ConfigLoader}

import scala.collection.JavaConverters._

case class DnsChangeNotices(notices: JsValue)

object DnsChangeNotices {
  implicit val configLoader: ConfigLoader[DnsChangeNotices] =
    new ConfigLoader[DnsChangeNotices] {
      def load(config: Config, path: String): DnsChangeNotices = {
        val notices = config.getConfigList(path).asScala.map { noticeConfig =>
          formatDnsChangeNotice(noticeConfig)
        }
        DnsChangeNotices(noticesToJson(notices.toList))
      }
    }

  def noticesToJson(notices: List[DnsChangeNotice]): JsValue =
    Json.toJson(
      notices.map { n =>
        Map(
          "status" -> n.status.toString,
          "alertType" -> n.alertType.toString,
          "text" -> n.text,
          "href" -> n.href.getOrElse(""),
          "hrefText" -> n.hrefText.getOrElse("")
        )
      }
    )

  def formatDnsChangeNotice(config: Config): DnsChangeNotice = {
    val status = DnsChangeStatus.find(config.getString("status"))
    val alertType = DnsChangeNoticeType.find(config.getString("alertType"))
    val text = config.getString("text")
    if (config.hasPath("hrefText") && config.hasPath("href")) {
      DnsChangeNotice(
        status,
        alertType,
        text,
        Some(config.getString("hrefText")),
        Some(config.getString("href")))
    } else {
      DnsChangeNotice(status, alertType, text, None, None)
    }
  }
}
case class DnsChangeNotice(
    status: DnsChangeStatus,
    alertType: DnsChangeNoticeType,
    text: String,
    hrefText: Option[String],
    href: Option[String])

object DnsChangeStatus extends Enumeration {
  type DnsChangeStatus = Value
  val Cancelled, Complete, Failed, PartialFailure, PendingProcessing, PendingReview, Rejected,
  Scheduled, Unknown = Value

  private val valueMap = DnsChangeStatus.values.map(v => v.toString -> v).toMap

  def find(status: String): DnsChangeStatus = valueMap.getOrElse(status, Unknown)
}

object DnsChangeNoticeType extends Enumeration {
  type DnsChangeNoticeType = Value
  val info, success, warning, danger = Value

  private val valueMap = DnsChangeNoticeType.values.map(v => v.toString -> v).toMap

  def find(status: String): DnsChangeNoticeType = valueMap.getOrElse(status, info)
}
