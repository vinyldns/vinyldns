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

import org.scalatest._
import vinyldns.core.TestZoneData._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ZoneACLSpec extends AnyWordSpec with Matchers {

  "ZoneACL" should {
    "add a new ACL rule" in {
      val acl = ZoneACL()
      val result = acl.addRule(userAclRule)
      result.rules should contain(userAclRule)
    }
    "delete a non-existing ACL rule" in {
      val acl = ZoneACL(Set(userAclRule, groupAclRule))
      val result = acl.deleteRule(userAclRule)
      (result.rules should contain).only(groupAclRule)
    }
  }
}
