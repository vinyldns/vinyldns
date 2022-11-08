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

package vinyldns.core.domain.zone

import java.time.Instant
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.time.temporal.ChronoUnit

class ZoneChangeSpec extends AnyWordSpec with Matchers {

  val zoneCreate: ZoneChange = ZoneChange(
    Zone("test", "test"),
    "ok",
    ZoneChangeType.Create,
    ZoneChangeStatus.Synced,
    created = Instant.now.truncatedTo(ChronoUnit.MILLIS).minusMillis(1000)
  )

  "ZoneChange" should {
    "toString" should {
      "output a zone change properly" in {
        val result = zoneCreate.toString

        result should include("id=\"" + zoneCreate.id + "\"")
        result should include("userId=\"" + zoneCreate.userId + "\"")
        result should include("changeType=\"" + zoneCreate.changeType + "\"")
        result should include("status=\"" + zoneCreate.status + "\"")
        result should include("systemMessage=\"" + zoneCreate.systemMessage + "\"")
        result should include(zoneCreate.zone.toString)
      }
    }
  }
}
