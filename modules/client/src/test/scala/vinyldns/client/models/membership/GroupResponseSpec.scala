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

package vinyldns.client.models.membership

import org.scalatest._
import vinyldns.client.SharedTestData

class GroupResponseSpec extends WordSpec with Matchers with SharedTestData {
  "GroupResponse.canEdit" should {
    val adminListBad = List(Id("not-testuser"))
    val adminListGood = List(Id(testUser.id))

    "return true if the user is super" in {
      GroupResponse.canEdit(adminListBad, testUser.copy(isSuper = true, isSupport = false)) shouldBe true
    }

    "return true if the user is support" in {
      GroupResponse.canEdit(adminListBad, testUser.copy(isSuper = false, isSupport = true)) shouldBe true
    }

    "return true if the user is in the admin group" in {
      GroupResponse.canEdit(adminListGood, testUser.copy(isSuper = false, isSupport = false)) shouldBe true
    }

    "return false if not super, support, or group admin" in {
      GroupResponse.canEdit(adminListBad, testUser.copy(isSuper = false, isSupport = false)) shouldBe false
    }
  }
}
