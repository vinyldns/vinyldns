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

import com.typesafe.config.ConfigException
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}

class DnsChangeNoticesSpec extends Specification with Mockito {
  "DnsChangeNotices" should {
    "load valid notices from config" in {
      val config = Map(
        "dnsChangeNotices" -> List(
          Map(
            "status" -> "Cancelled",
            "alertType" -> "info",
            "text" -> "No href or hrefText provided."
          ),
          Map(
            "status" -> "Complete",
            "alertType" -> "success",
            "text" -> "href and hrefText provided.",
            "href" -> "http://example.com",
            "hrefText" -> "See more."
          ),
          Map(
            "status" -> "PendingReview",
            "alertType" -> "warning",
            "text" -> "No hrefText provided.",
            "href" -> "http://example.com"
          ),
          Map(
            "status" -> "Failed",
            "alertType" -> "danger",
            "text" -> "No href provided.",
            "hrefText" -> "See more."
          )
        )
      )

      val configFormatted = Json.toJson(
        List(
          Map(
            "status" -> "Cancelled",
            "alertType" -> "info",
            "text" -> "No href or hrefText provided.",
            "href" -> "",
            "hrefText" -> ""
          ),
          Map(
            "status" -> "Complete",
            "alertType" -> "success",
            "text" -> "href and hrefText provided.",
            "href" -> "http://example.com",
            "hrefText" -> "See more."
          ),
          Map(
            "status" -> "PendingReview",
            "alertType" -> "warning",
            "text" -> "No hrefText provided.",
            "href" -> "http://example.com",
            "hrefText" -> ""
          ),
          Map(
            "status" -> "Failed",
            "alertType" -> "danger",
            "text" -> "No href provided.",
            "href" -> "",
            "hrefText" -> "See more."
          )
        ))

      val dnsChangeNotices = Configuration.from(config).get[DnsChangeNotices]("dnsChangeNotices")
      dnsChangeNotices.notices must beEqualTo(configFormatted)
    }

    "load valid notices from config" in {
      val config = Map(
        "dnsChangeNotices" -> List()
      )

      val configFormatted = Json.arr()
      val dnsChangeNotices = Configuration.from(config).get[DnsChangeNotices]("dnsChangeNotices")
      dnsChangeNotices.notices must beEqualTo(configFormatted)
    }

    "error if no dnsChangeNotices key is in the config" in {
      Configuration
        .from(Map())
        .get[DnsChangeNotices]("dnsChangeNotices") must
        throwA[ConfigException.Missing]("No configuration setting found for key 'dnsChangeNotices'")
    }

    "error if the dnsChangeNotices value is not a list" in {
      Configuration
        .from(Map("dnsChangeNotices" -> "invalid"))
        .get[DnsChangeNotices]("dnsChangeNotices") must
        throwA[ConfigException.WrongType]("dnsChangeNotices has type STRING rather than LIST")
    }

    "error if no text value is given" in {
      val config = Map(
        "dnsChangeNotices" -> List(
          Map(
            "status" -> "Cancelled",
            "alertType" -> "info"
          )
        )
      )

      Configuration
        .from(config)
        .get[DnsChangeNotices]("dnsChangeNotices") must
        throwA[ConfigException.Missing]("No configuration setting found for key 'text'")
    }

    "error if the given text value is not a string" in {
      val config = Map(
        "dnsChangeNotices" -> List(
          Map(
            "text" -> List("all done."),
            "status" -> "Cancelled",
            "alertType" -> "info"
          )
        )
      )

      Configuration
        .from(config)
        .get[DnsChangeNotices]("dnsChangeNotices") must
        throwA[ConfigException.WrongType]("text has type LIST rather than STRING")
    }

    "error if no status is given" in {
      val config = Map(
        "dnsChangeNotices" -> List(
          Map(
            "alertType" -> "info",
            "text" -> "Invalid status value"
          )
        )
      )

      Configuration
        .from(config)
        .get[DnsChangeNotices]("dnsChangeNotices") must
        throwA[ConfigException.Missing]("No configuration setting found for key 'status'")
    }

    "error if an invalid status is given" in {
      val config = Map(
        "dnsChangeNotices" -> List(
          Map(
            "status" -> "Invalid",
            "alertType" -> "info",
            "text" -> "Invalid status value"
          )
        )
      )

      Configuration
        .from(config)
        .get[DnsChangeNotices]("dnsChangeNotices") must
        throwA[ConfigException.BadValue]
    }

    "error if no alertType is given" in {
      val config = Map(
        "dnsChangeNotices" -> List(
          Map(
            "status" -> "Complete",
            "text" -> "Invalid alertType value"
          )
        )
      )

      Configuration
        .from(config)
        .get[DnsChangeNotices]("dnsChangeNotices") must
        throwA[ConfigException.Missing]("No configuration setting found for key 'alertType'")
    }

    "error if an invalid status is given" in {
      val config = Map(
        "dnsChangeNotices" -> List(
          Map(
            "status" -> "Invalid",
            "alertType" -> "veryBad",
            "text" -> "Invalid status value"
          )
        )
      )

      Configuration
        .from(config)
        .get[DnsChangeNotices]("dnsChangeNotices") must
        throwA[ConfigException.BadValue]
    }
  }
}
