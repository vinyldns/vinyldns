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

package vinyldns.client.pages.batch.view

import org.scalamock.scalatest.MockFactory
import org.scalatest._
import vinyldns.client.SharedTestData
import japgolly.scalajs.react.test._
import vinyldns.client.http.{GetBatchChangeRoute, Http, HttpResponse}
import vinyldns.client.models.batch.BatchChangeResponse
import vinyldns.client.router.{Page, ToBatchChangeViewPage}
import vinyldns.core.domain.batch.BatchChangeStatus

import scala.language.existentials

class BatchChangeViewPageSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val singleChanges = generateSingleChangeResponses(10)
  val batchChange = BatchChangeResponse(
    testUser.id,
    testUser.userName,
    "created-timestamp",
    singleChanges.toList,
    BatchChangeStatus.Complete,
    testUUID,
    Some("comments")
  )
  val mockRouter = MockRouterCtl[Page]()

  class Fixture(resp: Option[BatchChangeResponse] = Some(batchChange)) {
    val mockHttp = mock[Http]

    (mockHttp.get[BatchChangeResponse] _)
      .expects(GetBatchChangeRoute(testUUID), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], resp)
      }
  }

  "BatchChangeViewPage" should {
    "get batch change when mounting" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeViewPage(ToBatchChangeViewPage(testUUID), mockRouter, mockHttp)) { c =>
        c.state.batchChange shouldBe Some(batchChange)
        c.outerHtmlScrubbed() shouldNot include("Loading...")
      }
    }

    "show loading message when batch change is None" in new Fixture(None) {
      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeViewPage(ToBatchChangeViewPage(testUUID), mockRouter, mockHttp)) { c =>
        c.state.batchChange shouldBe None
        c.outerHtmlScrubbed() should include("Loading...")
      }
    }

    "re get batch change when clicking refresh button" in new Fixture {
      (mockHttp.get[BatchChangeResponse] _)
        .expects(GetBatchChangeRoute(testUUID), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(
            mock[HttpResponse],
            Some(batchChange.copy(status = BatchChangeStatus.Failed)))
        }

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeViewPage(ToBatchChangeViewPage(testUUID), mockRouter, mockHttp)) { c =>
        c.state.batchChange shouldBe Some(batchChange)
        c.state.batchChange.get.status shouldBe BatchChangeStatus.Complete

        val refreshButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-refresh")
        Simulate.click(refreshButton)

        c.state.batchChange.get.status shouldBe BatchChangeStatus.Failed
      }
    }

    "display single changes in table" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeViewPage(ToBatchChangeViewPage(testUUID), mockRouter, mockHttp)) { c =>
        val rows = ReactTestUtils.scryRenderedDOMComponentsWithTag(c, "tr").drop(1) // drop header
        rows.length shouldBe singleChanges.length

        rows.zip(singleChanges).map {
          case (tr, change) =>
            val html = tr.outerHtmlScrubbed()
            html should include(s"<td>${change.changeType}</td>")
            html should include(s"<td>${change.inputName}</td>")
            html should include(s"<td>${change.recordName}</td>")
            html should include(s"<td>${change.zoneName}</td>")
            html should include(s"<td>${change.`type`.toString}</td>")
            html should include("1.1.1.1")
            html should include(change.status.toString)
            html should include(s"<td>${change.systemMessage.getOrElse("")}</td>")
        }
      }
    }

    "filter changes from search bar" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeViewPage(ToBatchChangeViewPage(testUUID), mockRouter, mockHttp)) { c =>
        val change = singleChanges(2)
        val filterField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-filter")
        Simulate.change(filterField, SimEvent.Change(change.inputName))

        val rows = ReactTestUtils.scryRenderedDOMComponentsWithTag(c, "tr").drop(1) // drop header
        rows.length shouldBe 1

        val html = rows.head.outerHtmlScrubbed()
        html should include(s"<td>${change.changeType}</td>")
        html should include(s"<td>${change.inputName}</td>")
        html should include(s"<td>${change.recordName}</td>")
        html should include(s"<td>${change.zoneName}</td>")
        html should include(s"<td>${change.`type`.toString}</td>")
        html should include("1.1.1.1")
        html should include(change.status.toString)
        html should include(s"<td>${change.systemMessage.getOrElse("")}</td>")
      }
    }
  }
}
