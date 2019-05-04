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

package vinyldns.client.pages.batch.list.components

import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import vinyldns.client.SharedTestData
import vinyldns.client.http.{Http, HttpResponse, ListBatchChangesRoute}
import vinyldns.client.models.batch.BatchChangeListResponse
import vinyldns.client.router.Page
import vinyldns.client.components.JsNative.toReadableTimestamp

import scala.language.existentials

class BatchChangesTableSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = MockRouterCtl[Page]()
  val initialBatchChanges = generateBatchChangeSummaryResponses(10)
  val initialBatchChangeList = BatchChangeListResponse(initialBatchChanges.toList, 100)

  class Fixture(changes: Option[BatchChangeListResponse] = Some(initialBatchChangeList)) {
    val mockHttp = mock[Http]
    val props = BatchChangesTable.Props(mockHttp, mockRouter)

    (mockHttp.get[BatchChangeListResponse] _)
      .expects(ListBatchChangesRoute(), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], changes)
      }
  }

  "BatchChangesTable" should {
    "call get batch changes endpoint when mounting" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(BatchChangesTable(props)) { c =>
        c.state.batchChangeList shouldBe Some(initialBatchChangeList)
        ReactTestUtils.findRenderedDOMComponentWithTag(c, "table")
      }
    }

    "include loading message if batch change list is None" in new Fixture(None) {
      ReactTestUtils.withRenderedIntoDocument(BatchChangesTable(props)) { c =>
        c.state.batchChangeList shouldBe None
        ReactTestUtils.scryRenderedDOMComponentsWithTag(c, "table").length shouldBe 0
        c.outerHtmlScrubbed() should include("Loading your batch changes...")
      }
    }

    "include no changes message if batch change list is empty" in
      new Fixture(Some(initialBatchChangeList.copy(batchChanges = List()))) {
        ReactTestUtils.withRenderedIntoDocument(BatchChangesTable(props)) { c =>
          c.state.batchChangeList shouldBe Some(initialBatchChangeList.copy(batchChanges = List()))
          ReactTestUtils.scryRenderedDOMComponentsWithTag(c, "table").length shouldBe 0
          c.outerHtmlScrubbed() should include("You don't have any batch changes yet")
        }
      }

    "display batch changes in table" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(BatchChangesTable(props)) { c =>
        val table = ReactTestUtils.findRenderedDOMComponentWithTag(c, "table")
        val html = table.outerHtmlScrubbed()

        initialBatchChangeList.batchChanges.map { change =>
          html should include(s"""<td>${toReadableTimestamp(change.createdTimestamp)}</td>""")
          html should include(s"""<td>${change.id}</td>""")
          html should include(s"""${change.status.toString}""")
          html should include(s"<td>${change.comments.getOrElse("")}</td>")
        }
      }
    }
  }
}
