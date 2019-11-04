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
import play.api.libs.json.Json

class DnsChangeNoticesSpec extends Specification with Mockito {
  "DnsChangeNotices" should {
    "load notices from config" in {
      val config = Map(
        "dnsChangeNotices" -> List(
          Map(
            "status" -> "Cancelled",
            "alertType" -> "info",
            "text" -> "All done"
          ),
          Map(
            "status" -> "Invalid",
            "alertType" -> "info",
            "text" -> "All done",
            "href" -> "http://example.com",
            "hrefText" -> "See more."
          ),
          Map(
            "status" -> "Cancelled",
            "alertType" -> "info",
            "text" -> "All done",
            "href" -> "http://example.com"
          ),
          Map(
            "status" -> "Cancelled",
            "alertType" -> "bad",
            "text" -> "All done",
            "href" -> "http://example.com",
            "hrefText" -> "See more."
          )
        )
      )

      val configFormatted = Json.toJson(
        List(
          Map(
            "status" -> "Cancelled",
            "alertType" -> "info",
            "text" -> "All done",
            "href" -> "",
            "hrefText" -> ""
          ),
          Map(
            "status" -> "Unknown",
            "alertType" -> "info",
            "text" -> "All done",
            "href" -> "http://example.com",
            "hrefText" -> "See more."
          ),
          Map(
            "status" -> "Cancelled",
            "alertType" -> "info",
            "text" -> "All done",
            "href" -> "",
            "hrefText" -> ""
          ),
          Map(
            "status" -> "Cancelled",
            "alertType" -> "info",
            "text" -> "All done",
            "href" -> "http://example.com",
            "hrefText" -> "See more."
          )
        ))

      val dnsChangeNotices = Configuration.from(config).get[DnsChangeNotices]("dnsChangeNotices")
      dnsChangeNotices.notices must beEqualTo(configFormatted)
    }

    "error if an invalid dnsChangeNotices value is in the config" in {
      Configuration
        .from(Map("dnsChangeNotices" -> "invalid"))
        .get[DnsChangeNotices]("dnsChangeNotices") must
        throwA[ConfigException]("dnsChangeNotices has type STRING rather than LIST")
    }

    "error if no dnsChangeNotices is in the config" in {
      Configuration
        .from(Map())
        .get[DnsChangeNotices]("dnsChangeNotices") must
        throwA[ConfigException]("No configuration setting found for key 'dnsChangeNotices'")
    }
  }
}
