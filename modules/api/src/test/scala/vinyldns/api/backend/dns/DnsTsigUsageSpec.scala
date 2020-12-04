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

package vinyldns.api.backend.dns

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DnsTsigUsageSpec extends AnyWordSpec with Matchers {

  "DnsTsigUsage" should {
    "convert from string" in {
      DnsTsigUsage.Values.foreach(v => DnsTsigUsage.fromString(v.humanized) shouldBe defined)
    }
    "return none from string when no match" in {
      DnsTsigUsage.fromString("foo") shouldBe empty
    }
    "parse from config" in {
      DnsTsigUsage.Values.foreach { u =>
        val config = ConfigFactory.parseString(s"""{ tsig-usage = "${u.humanized}" } """)
        DnsTsigUsage.configReader.from(config.getValue("tsig-usage")).toOption shouldBe defined
      }
    }
    "default to use all when not found" in {
      val config = ConfigFactory.parseString(s"""{ tsig-usage = "foo" } """)
      DnsTsigUsage.configReader.from(config.getValue("tsig-usage")).toOption shouldBe Some(
        DnsTsigUsage.UpdateAndTransfer
      )
    }
  }
}
