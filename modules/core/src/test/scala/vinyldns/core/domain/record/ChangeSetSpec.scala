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

package vinyldns.core.domain.record

import org.scalatest.{Matchers, WordSpec}

class ChangeSetSpec extends WordSpec with Matchers {

  "ChangeSet" should {
    "convert status fromInt properly" in {
      ChangeSetStatus.fromInt(ChangeSetStatus.Pending.intValue) shouldBe ChangeSetStatus.Pending
      ChangeSetStatus.fromInt(ChangeSetStatus.Processing.intValue) shouldBe ChangeSetStatus.Processing
      ChangeSetStatus.fromInt(ChangeSetStatus.Complete.intValue) shouldBe ChangeSetStatus.Complete
      ChangeSetStatus.fromInt(ChangeSetStatus.Applied.intValue) shouldBe ChangeSetStatus.Applied
    }
  }
}
