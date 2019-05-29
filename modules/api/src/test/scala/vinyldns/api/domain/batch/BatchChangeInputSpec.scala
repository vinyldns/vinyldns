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

package vinyldns.api.domain.batch

import org.scalatest.{Matchers, WordSpec}
import vinyldns.core.domain.record.RecordType._
import vinyldns.core.domain.record.{AAAAData, AData, CNAMEData}

class BatchChangeInputSpec extends WordSpec with Matchers {
  "BatchChangeInput" should {
    "ensure trailing dot on A, AAAA, and CNAME fqdn" in {
      val changeA = AddChangeInput("apex.test.com", A, 100, AData("1.1.1.1"))
      val changeAAAA = AddChangeInput("aaaa.test.com", AAAA, 3600, AAAAData("1:2:3:4:5:6:7:8"))
      val changeCname = AddChangeInput("cname.test.com", CNAME, 100, CNAMEData("testing.test.com"))
      val changeADotted = AddChangeInput("adot.test.com.", A, 100, AData("1.1.1.1"))
      val changeAAAADotted =
        AddChangeInput("aaaadot.test.com.", AAAA, 3600, AAAAData("1:2:3:4:5:6:7:8"))
      val changeCnameDotted =
        AddChangeInput("cnamedot.test.com.", CNAME, 100, CNAMEData("testing.test.com."))

      val input = BatchChangeInput(
        None,
        List(changeA, changeAAAA, changeCname, changeADotted, changeAAAADotted, changeCnameDotted),
        false)

      input.changes(0).inputName shouldBe "apex.test.com."
      input.changes(1).inputName shouldBe "aaaa.test.com."
      input.changes(2).inputName shouldBe "cname.test.com."
      input.changes(3).inputName shouldBe "adot.test.com."
      input.changes(4).inputName shouldBe "aaaadot.test.com."
      input.changes(5).inputName shouldBe "cnamedot.test.com."
    }
  }
}
