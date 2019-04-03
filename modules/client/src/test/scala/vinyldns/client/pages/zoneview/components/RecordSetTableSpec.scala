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

package vinyldns.client.pages.zoneview.components

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router.RouterCtl
import org.scalatest._
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import vinyldns.client.SharedTestData
import vinyldns.client.http._
import vinyldns.client.models.Pagination
import vinyldns.client.models.record.{RecordSet, RecordSetChange, RecordSetList}
import vinyldns.client.pages.zoneview.components.recordmodal.RecordSetModal
import vinyldns.client.router.Page

import scala.language.existentials

class RecordSetTableSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]
  val zone = generateZones(1).head
  val initialRecordSets = generateRecordSets(10, zone.id)

  class Fixture(withNext: Boolean = false) {
    val mockHttp = mock[Http]
    val props = RecordSetTable.Props(zone, mockHttp, mockRouter, generateNoOpHandler[Unit])
    val initialRecordSetList =
      if (withNext)
        RecordSetList(initialRecordSets.toList, 100, nextId = Some("next"))
      else
        RecordSetList(initialRecordSets.toList, 100)

    (mockHttp.get[RecordSetList] _)
      .expects(ListRecordSetsRoute(zone.id), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(initialRecordSetList))
      }
  }

  "RecordSetTable" should {
    "get record sets when mounting" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        c.state.recordSetList shouldBe Some(initialRecordSetList)
      }
    }

    "display loading message when record set list is none" in {
      val mockHttp = mock[Http]
      val props = RecordSetTable.Props(zone, mockHttp, mockRouter, generateNoOpHandler[Unit])

      (mockHttp.get[RecordSetList] _)
        .expects(ListRecordSetsRoute(zone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        c.state.recordSetList shouldBe None
        val panelBody = ReactTestUtils.findRenderedDOMComponentWithClass(c, "panel-body")
        panelBody.outerHtmlScrubbed() shouldBe
          """<div class="panel-body"><p>Loading records...</p></div>"""
      }
    }

    "display no records message when record set list is empty" in {
      val mockHttp = mock[Http]
      val props = RecordSetTable.Props(zone, mockHttp, mockRouter, generateNoOpHandler[Unit])

      (mockHttp.get[RecordSetList] _)
        .expects(ListRecordSetsRoute(zone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(RecordSetList(List(), 100)))
        }

      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        c.state.recordSetList shouldBe Some(RecordSetList(List(), 100))
        val panelBody = ReactTestUtils.findRenderedDOMComponentWithClass(c, "panel-body")
        panelBody.outerHtmlScrubbed() shouldBe
          """<div class="panel-body"><p>No DNS records found</p></div>"""
      }
    }

    "show create recordset modal when clicking create button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        c.state.showCreateRecordModal shouldBe false
        c.state.showUpdateRecordModal shouldBe false

        ReactTestUtils.scryRenderedComponentsWithType(c, RecordSetModal.component) shouldBe empty

        val createButton =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-recordset")
        Simulate.click(createButton)

        c.state.showCreateRecordModal shouldBe true
        c.state.showUpdateRecordModal shouldBe false

        ReactTestUtils.findRenderedComponentWithType(c, RecordSetModal.component)
      }
    }

    "show update recordset modal when clicking update button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        c.state.showUpdateRecordModal shouldBe false
        c.state.showCreateRecordModal shouldBe false

        ReactTestUtils.scryRenderedComponentsWithType(c, RecordSetModal.component) shouldBe empty

        val updateButton = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-edit")(0)
        Simulate.click(updateButton)

        c.state.showUpdateRecordModal shouldBe true
        c.state.showCreateRecordModal shouldBe false

        ReactTestUtils.findRenderedComponentWithType(c, RecordSetModal.component)
      }
    }

    "close create recordset modal after clicking close button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        c.state.showCreateRecordModal shouldBe false

        val createButton =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-recordset")
        Simulate.click(createButton)
        c.state.showCreateRecordModal shouldBe true

        val modal = ReactTestUtils.findRenderedComponentWithType(c, RecordSetModal.component)

        val closeButton =
          ReactTestUtils.findRenderedDOMComponentWithClass(modal, "test-close-recordset")
        Simulate.click(closeButton)

        c.state.showCreateRecordModal shouldBe false
        ReactTestUtils.scryRenderedComponentsWithType(c, RecordSetModal.component) shouldBe empty
      }
    }

    "close update recordset modal after clicking close button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        c.state.showUpdateRecordModal shouldBe false

        ReactTestUtils.scryRenderedComponentsWithType(c, RecordSetModal.component) shouldBe empty

        val updateButton = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-edit")(0)
        Simulate.click(updateButton)

        c.state.showUpdateRecordModal shouldBe true

        val modal = ReactTestUtils.findRenderedComponentWithType(c, RecordSetModal.component)

        val closeButton =
          ReactTestUtils.findRenderedDOMComponentWithClass(modal, "test-close-recordset")
        Simulate.click(closeButton)

        c.state.showUpdateRecordModal shouldBe false
        ReactTestUtils.scryRenderedComponentsWithType(c, RecordSetModal.component) shouldBe empty
      }
    }

    "call http.get with recordNameFilter when someone uses search bar" in new Fixture {
      (mockHttp.get[RecordSetList] _)
        .expects(ListRecordSetsRoute(zone.id, nameFilter = Some("filter")), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        val input = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-nameFilter")
        Simulate.change(input, SimEvent.Change("filter"))

        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-search-form")
        Simulate.submit(form)
      }
    }

    "reset pagination info when someone uses search button" in new Fixture(withNext = true) {
      (mockHttp.get[RecordSetList] _)
        .expects(ListRecordSetsRoute(zone.id, startFrom = Some("next")), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialRecordSetList))
        }

      (mockHttp.get[RecordSetList] _)
        .expects(ListRecordSetsRoute(zone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialRecordSetList))
        }

      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        c.state.pagination shouldBe Pagination()

        val nextButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-next-page")
        Simulate.click(nextButton)

        c.state.pagination shouldBe Pagination(List(None), 2)

        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-search-form")
        Simulate.submit(form)

        c.state.pagination shouldBe Pagination()
      }
    }

    "reset pagination info when someone uses refresh button" in new Fixture(withNext = true) {
      (mockHttp.get[RecordSetList] _)
        .expects(ListRecordSetsRoute(zone.id, startFrom = Some("next")), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialRecordSetList))
        }

      (mockHttp.get[RecordSetList] _)
        .expects(ListRecordSetsRoute(zone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialRecordSetList))
        }

      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        c.state.pagination shouldBe Pagination()

        val nextButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-next-page")
        Simulate.click(nextButton)

        c.state.pagination shouldBe Pagination(List(None), 2)

        val refresh = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-refresh-recordsets")
        Simulate.click(refresh)

        c.state.pagination shouldBe Pagination()
      }
    }

    "display records in table" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        val table = ReactTestUtils.findRenderedDOMComponentWithTag(c, "table")
        val html = table.outerHtmlScrubbed()
        initialRecordSetList.recordSets.map { r =>
          html should include(r.name)
          html should include(r.ttl.toString)
          html should include(r.`type`.toString)
          val recordData =
            ReactTestUtils.renderIntoDocument(r.recordDataDisplay).outerHtmlScrubbed()
          html should include(recordData)
        }
      }
    }

    "call withConfirmation when clicking delete button" in new Fixture {
      (mockHttp.withConfirmation _).expects(*, *).once().returns(Callback.empty)
      (mockHttp.delete[RecordSet] _).expects(*, *, *).never()

      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        val deleteButton = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-delete")(0)
        Simulate.click(deleteButton)
      }
    }

    "call http.delete after confirming delete" in new Fixture {
      (mockHttp.withConfirmation _).expects(*, *).repeat(10 to 10).onCall((_, cb) => cb)

      initialRecordSetList.recordSets.map { r =>
        (mockHttp.delete[RecordSetChange] _)
          .expects(DeleteRecordSetRoute(zone.id, r.id), *, *)
          .once()
          .returns(Callback.empty)
      }

      ReactTestUtils.withRenderedIntoDocument(RecordSetTable(props)) { c =>
        val deleteButtons = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-delete")
        (deleteButtons should have).length(10)
        deleteButtons.foreach(Simulate.click(_))
      }
    }
  }
}
