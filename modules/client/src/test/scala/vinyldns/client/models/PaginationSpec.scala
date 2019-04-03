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

package vinyldns.client.models

import org.scalatest.{Matchers, WordSpec}

class PaginationSpec extends WordSpec with Matchers {
  "Pagination.next" should {
    "return proper next page info when starting at page 1" in {
      val page1 = Pagination()
      val next = page1.next(Some("2"))

      next.startFroms shouldBe List(Some("2"))
      next.pageNumber shouldBe 2
      next.popped shouldBe None
    }

    "return proper next page info when starting at arbitrary page" in {
      val somePage = Pagination(List(Some("3"), Some("2"), Some("1"), None), 4, None)
      val next = somePage.next(Some("4"))

      next.startFroms shouldBe Some("4") :: somePage.startFroms
      next.pageNumber shouldBe 5
      next.popped shouldBe None
    }
  }

  "Pagination.previous" should {
    "return proper previous when going to page 1" in {
      val page2 = Pagination(List(None), 2)
      val page1 = page2.previous()

      page1.startFroms shouldBe List()
      page1.pageNumber shouldBe 1
      page1.popped shouldBe None
    }

    "return proper previous when going to arbitrary page" in {
      val somePage = Pagination(List(Some("3"), Some("2"), Some("1"), None), 4, None)
      val previous = somePage.previous()

      previous.startFroms shouldBe List(Some("2"), Some("1"), None)
      previous.pageNumber shouldBe 3
      previous.popped shouldBe Some("3")
    }
  }
}
