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

package vinyldns.client.pages.zone.view.components

import japgolly.scalajs.react.extra.router.RouterCtl
import org.scalatest._
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import vinyldns.client.SharedTestData
import vinyldns.client.http._
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.client.models.record.RecordSetChangeListResponse
import vinyldns.client.router.Page
import vinyldns.client.components.JsNative.toReadableTimestamp

import scala.language.existentials

class RecordSetChangeTableSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]
  val zone = generateZoneResponses(1).head
  val initialRecordSetChanges = generateRecordSetChangeResponses(10, zone)
  val initialGroups = generateGroupResponses(1)
  val initialGroupList = GroupListResponse(initialGroups.toList, 100)

  class Fixture(withNext: Boolean = false) {
    val mockHttp = mock[Http]
    val props = RecordSetChangeTable.Props(zone, initialGroupList, mockHttp, mockRouter)
    val initialRecordSetChangeList =
      if (withNext)
        RecordSetChangeListResponse(
          zone.id,
          initialRecordSetChanges.toList,
          100,
          nextId = Some("next"))
      else
        RecordSetChangeListResponse(zone.id, initialRecordSetChanges.toList, 100)

    (mockHttp.get[RecordSetChangeListResponse] _)
      .expects(ListRecordSetChangesRoute(zone.id), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(initialRecordSetChangeList))
      }
  }

  "RecordSetChangeTable" should {
    "get record set changes when mounting" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(RecordSetChangeTable(props)) { c =>
        c.state.recordSetChangeList shouldBe Some(initialRecordSetChangeList)
      }
    }

    "display loading message when record set change list is none" in {
      val mockHttp = mock[Http]
      val props = RecordSetChangeTable.Props(zone, initialGroupList, mockHttp, mockRouter)

      (mockHttp.get[RecordSetChangeListResponse] _)
        .expects(ListRecordSetChangesRoute(zone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      ReactTestUtils.withRenderedIntoDocument(RecordSetChangeTable(props)) { c =>
        c.state.recordSetChangeList shouldBe None
        val panelBody = ReactTestUtils.findRenderedDOMComponentWithClass(c, "panel-body")
        panelBody.outerHtmlScrubbed() shouldBe
          """<div class="panel-body"><p>Loading change history...</p></div>"""
      }
    }

    "display no changes message when record set change list is empty" in {
      val mockHttp = mock[Http]
      val props = RecordSetChangeTable.Props(zone, initialGroupList, mockHttp, mockRouter)

      (mockHttp.get[RecordSetChangeListResponse] _)
        .expects(ListRecordSetChangesRoute(zone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(
            mock[HttpResponse],
            Some(RecordSetChangeListResponse(zone.id, List(), 100)))
        }

      ReactTestUtils.withRenderedIntoDocument(RecordSetChangeTable(props)) { c =>
        c.state.recordSetChangeList shouldBe Some(RecordSetChangeListResponse(zone.id, List(), 100))
        val panelBody = ReactTestUtils.findRenderedDOMComponentWithClass(c, "panel-body")
        panelBody.outerHtmlScrubbed() shouldBe
          """<div class="panel-body"><p>No changes found</p></div>"""
      }
    }

    "call http.get when clicking refresh button" in new Fixture {
      (mockHttp.get[RecordSetChangeListResponse] _)
        .expects(ListRecordSetChangesRoute(zone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      ReactTestUtils.withRenderedIntoDocument(RecordSetChangeTable(props)) { c =>
        val button =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-refresh-recordsetchanges")
        Simulate.click(button)
      }
    }

    "display changes in table" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(RecordSetChangeTable(props)) { c =>
        val table = ReactTestUtils.findRenderedDOMComponentWithTag(c, "table")
        val html = table.outerHtmlScrubbed()
        initialRecordSetChangeList.recordSetChanges.map { r =>
          html should include(toReadableTimestamp(r.created))
          html should include(r.recordSet.name)
          html should include(r.recordSet.`type`.toString)
          html should include(r.changeType.toString)
          html should include(r.userName)
          html should include(r.status.toString)
          html should include(r.systemMessage.getOrElse(""))
        }
      }
    }
  }
}
