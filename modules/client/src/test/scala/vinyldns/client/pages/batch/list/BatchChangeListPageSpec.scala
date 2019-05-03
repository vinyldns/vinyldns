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

package vinyldns.client.pages.batch.list

import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import vinyldns.client.SharedTestData
import vinyldns.client.http.{Http, HttpResponse, ListBatchChangesRoute}
import vinyldns.client.models.batch.BatchChangeListResponse
import vinyldns.client.pages.batch.list.components.BatchChangesTable
import vinyldns.client.router.{Page, ToBatchChangeListPage}

import scala.language.existentials

class BatchChangeListPageSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = MockRouterCtl[Page]()

  trait Fixture {
    val mockHttp = mock[Http]
    val batchChangeList = BatchChangeListResponse(List(), 100)

    (mockHttp.get[BatchChangeListResponse] _)
      .expects(ListBatchChangesRoute(), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(batchChangeList))
      }
  }

  "BatchChangeListPage" should {
    "include batch change table" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeListPage(ToBatchChangeListPage, mockRouter, mockHttp)) { c =>
        ReactTestUtils.findRenderedComponentWithType(c, BatchChangesTable.component)
      }
    }

    "call get batch changes endpoint when refresh button is clicked" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeListPage(ToBatchChangeListPage, mockRouter, mockHttp)) { c =>
        (mockHttp.get[BatchChangeListResponse] _)
          .expects(ListBatchChangesRoute(), *, *)
          .once()
          .onCall { (_, onSuccess, _) =>
            onSuccess.apply(mock[HttpResponse], Some(batchChangeList))
          }

        val button =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-refresh-batch-changes")
        Simulate.click(button)
      }
    }
  }
}
