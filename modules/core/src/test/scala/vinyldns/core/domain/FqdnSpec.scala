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

package vinyldns.core.domain

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FqdnSpec extends AnyWordSpec with Matchers {

  "Fqdn" should {
    "merge when record name matches zone name" in {
      Fqdn.merge("test.com", "test.com") shouldBe Fqdn("test.com.")
    }
    "merge when record name does not match zone name" in {
      Fqdn.merge("www.", "test.com") shouldBe Fqdn("www.test.com.")
    }
    "merge properly when record name is not absolute" in {
      Fqdn.merge("www", "test.com.") shouldBe Fqdn("www.test.com.")
    }
    "merge properly when zone name and record name are not absolute" in {
      Fqdn.merge("www", "test.com") shouldBe Fqdn("www.test.com.")
    }
    "merge properly for dotted hosts" in {
      Fqdn.merge("www.foo", "test.com") shouldBe Fqdn("www.foo.test.com.")
    }
    "merge properly when the record name is already an fqdn" in {
      Fqdn.merge("www.test.com", "test.com") shouldBe Fqdn("www.test.com")
    }
  }
}
