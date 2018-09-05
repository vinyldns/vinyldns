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

import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.TestZoneData._

class ZoneSpec extends WordSpec with Matchers {

  "Zone" should {
    "toString should output a zone properly" in {
      val result = zoneActive.toString

      result should include("id=\"" + zoneActive.id + "\"")
      result should include("name=\"" + zoneActive.name + "\"")
      result should include("connection=\"" + zoneActive.connection + "\"")
      result should include("account=\"" + zoneActive.account + "\"")
      result should include("status=\"" + zoneActive.status + "\"")
      result should include("shared=\"" + zoneActive.shared + "\"")
      result should include("reverse=\"" + zoneActive.isReverse + "\"")
    }
    "add an ACL rule" in {
      val result = zoneActive.addACLRule(userAclRule)

      result.acl.rules should contain(userAclRule)
    }
    "remove an ACL rule" in {
      val zone1 = zoneActive.addACLRule(userAclRule)
      val zone2 = zone1.addACLRule(groupAclRule)
      val result = zone2.deleteACLRule(groupAclRule)

      (result.acl.rules should contain).only(userAclRule)
    }
  }
}
