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

package vinyldns.client.pages.batch.create

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import vinyldns.client.SharedTestData
import vinyldns.client.http.{CreateBatchChangeRoute, Http, HttpResponse, ListGroupsRoute}
import vinyldns.client.models.batch.{BatchChangeCreateInfo, SingleChangeCreateInfo}
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.client.models.record.RecordData
import vinyldns.client.router.{Page, ToBatchChangeCreatePage}
import upickle.default.write

import scala.language.existentials

class BatchChangeCreatePageSpec
    extends WordSpec
    with Matchers
    with MockFactory
    with SharedTestData {
  val mockRouter = MockRouterCtl[Page]()
  val groups = generateGroups(5)
  val groupList = GroupListResponse(groups.toList, 100)

  class Fixture(gl: Option[GroupListResponse] = Some(groupList)) {
    val mockHttp = mock[Http]
    (mockHttp.get[GroupListResponse] _)
      .expects(ListGroupsRoute(), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], gl)
      }
  }

  "BatchChangeCreatePage" should {
    "call listGroups when mounting" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        c.state.groupList shouldBe Some(groupList)
        c.outerHtmlScrubbed() shouldNot include("Loading...")
      }
    }

    "show loading message when listGroups is None" in new Fixture(None) {
      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        c.state.groupList shouldBe None
        c.outerHtmlScrubbed() should include("Loading...")
      }
    }

    "add a batch change row when clicking Add Row button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val rows = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-single-change-row")
        rows.length shouldBe 1

        val addButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-add-button")
        Simulate.click(addButton)

        val newRows = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-single-change-row")
        newRows.length shouldBe 2
      }
    }

    "delete a batch change row when clicking Delete button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val addButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-add-button")
        Simulate.click(addButton)

        val rows = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-single-change-row")
        rows.length shouldBe 2

        val deleteButton =
          ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-delete-button")(0)
        Simulate.click(deleteButton)

        val newRows = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-single-change-row")
        newRows.length shouldBe 1
      }
    }

    "disable submit button when Owner Group is not found" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        submitButton.outerHtmlScrubbed() shouldNot include("disabled")

        val ownerGroupNameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-owner-group-name")
        Simulate.change(ownerGroupNameField, SimEvent.Change("no-existo"))

        val html = c.outerHtmlScrubbed()
        html should include("You are not in a group named 'no-existo'")

        val submitButtonBad =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        submitButtonBad.outerHtmlScrubbed() should include("disabled")
      }
    }

    "not disable submit button when Owner Group is found" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        submitButton.outerHtmlScrubbed() shouldNot include("disabled")

        val ownerGroupNameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-owner-group-name")
        Simulate.change(ownerGroupNameField, SimEvent.Change(groups(2).name))

        val html = c.outerHtmlScrubbed()
        html should include(s"Group ID: '${groups(2).id}'")

        val submitButtonBad =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        submitButtonBad.outerHtmlScrubbed() shouldNot include("disabled")
      }
    }

    "call withConfirmation when submitting" in new Fixture {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .returns(Callback.empty)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(*, *, *, *)
        .never()

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val ttlField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("Add"))
        Simulate.change(recordTypeField, SimEvent.Change("A"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))
        Simulate.change(ttlField, SimEvent.Change("300"))
        Simulate.change(dataField, SimEvent.Change("1.1.1.1"))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post a change with a description" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.vinyldns.",
            `type` = "A",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(address = Some("1.1.1.1")))
          )
        )
      val expectedBatchChange =
        BatchChangeCreateInfo(changes = expectedChanges, Some("some comments"), None, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val ttlField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("Add"))
        Simulate.change(recordTypeField, SimEvent.Change("A"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))
        Simulate.change(ttlField, SimEvent.Change("300"))
        Simulate.change(dataField, SimEvent.Change("1.1.1.1"))

        val commentsField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-comments")
        Simulate.change(commentsField, SimEvent.Change("some comments"))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post a change with an Owner Group" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.vinyldns.",
            `type` = "A",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(address = Some("1.1.1.1")))
          )
        )
      val expectedBatchChange =
        BatchChangeCreateInfo(
          changes = expectedChanges,
          None,
          Some(groups.head.id),
          Some(groups.head.name))

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val ttlField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("Add"))
        Simulate.change(recordTypeField, SimEvent.Change("A"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))
        Simulate.change(ttlField, SimEvent.Change("300"))
        Simulate.change(dataField, SimEvent.Change("1.1.1.1"))

        val ownerGroupNameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-owner-group-name")
        Simulate.change(ownerGroupNameField, SimEvent.Change(groups.head.name))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post an A Add batch change" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.vinyldns.",
            `type` = "A",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(address = Some("1.1.1.1")))
          )
        )
      val expectedBatchChange = BatchChangeCreateInfo(changes = expectedChanges, None, None, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val ttlField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("Add"))
        Simulate.change(recordTypeField, SimEvent.Change("A"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))
        Simulate.change(ttlField, SimEvent.Change("300"))
        Simulate.change(dataField, SimEvent.Change("1.1.1.1"))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post an A DeleteRecordSet batch change" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.vinyldns.",
            `type` = "A",
            changeType = "DeleteRecordSet",
            ttl = Some(300),
            record = Some(RecordData())
          )
        )
      val expectedBatchChange = BatchChangeCreateInfo(changes = expectedChanges, None, None, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")

        Simulate.change(changeTypeField, SimEvent.Change("DeleteRecordSet"))
        Simulate.change(recordTypeField, SimEvent.Change("A"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post an AAAA Add batch change" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.vinyldns.",
            `type` = "AAAA",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(address = Some("1::1")))
          )
        )
      val expectedBatchChange = BatchChangeCreateInfo(changes = expectedChanges, None, None, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val ttlField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("Add"))
        Simulate.change(recordTypeField, SimEvent.Change("AAAA"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))
        Simulate.change(ttlField, SimEvent.Change("300"))
        Simulate.change(dataField, SimEvent.Change("1::1"))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post a CNAME Add batch change" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.vinyldns.",
            `type` = "CNAME",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(cname = Some("target.")))
          )
        )
      val expectedBatchChange = BatchChangeCreateInfo(changes = expectedChanges, None, None, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val ttlField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("Add"))
        Simulate.change(recordTypeField, SimEvent.Change("CNAME"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))
        Simulate.change(ttlField, SimEvent.Change("300"))
        Simulate.change(dataField, SimEvent.Change("target."))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post a PTR Add batch change" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "1.1.1.1",
            `type` = "PTR",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(ptrdname = Some("target.")))
          )
        )
      val expectedBatchChange = BatchChangeCreateInfo(changes = expectedChanges, None, None, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val ttlField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("Add"))
        Simulate.change(recordTypeField, SimEvent.Change("PTR"))
        Simulate.change(inputNameField, SimEvent.Change("1.1.1.1"))
        Simulate.change(ttlField, SimEvent.Change("300"))
        Simulate.change(dataField, SimEvent.Change("target."))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post a TXT Add batch change" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.vinyldns.",
            `type` = "TXT",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(text = Some("text")))
          )
        )
      val expectedBatchChange = BatchChangeCreateInfo(changes = expectedChanges, None, None, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val ttlField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("Add"))
        Simulate.change(recordTypeField, SimEvent.Change("TXT"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))
        Simulate.change(ttlField, SimEvent.Change("300"))
        Simulate.change(dataField, SimEvent.Change("text"))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post an MX Add batch change" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.vinyldns.",
            `type` = "MX",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(preference = Some(1), exchange = Some("target.")))
          )
        )
      val expectedBatchChange = BatchChangeCreateInfo(changes = expectedChanges, None, None, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val ttlField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")

        Simulate.change(changeTypeField, SimEvent.Change("Add"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))
        Simulate.change(ttlField, SimEvent.Change("300"))

        Simulate.change(recordTypeField, SimEvent.Change("MX"))
        val preferenceField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-preference")
        val exchangeField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-exchange")

        Simulate.change(preferenceField, SimEvent.Change("1"))
        Simulate.change(exchangeField, SimEvent.Change("target."))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post an A+PTR Add batch change" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.vinyldns.",
            `type` = "A",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(address = Some("1.1.1.1")))
          ),
          SingleChangeCreateInfo(
            inputName = "1.1.1.1",
            `type` = "PTR",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(ptrdname = Some("foo.vinyldns.")))
          )
        )
      val expectedBatchChange = BatchChangeCreateInfo(changes = expectedChanges, None, None, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val ttlField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("Add"))
        Simulate.change(recordTypeField, SimEvent.Change("A+PTR"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))
        Simulate.change(ttlField, SimEvent.Change("300"))
        Simulate.change(dataField, SimEvent.Change("1.1.1.1"))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post an A+PTR DeleteRecordSet batch change" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.vinyldns.",
            `type` = "A",
            changeType = "DeleteRecordSet",
            ttl = Some(300),
            record = Some(RecordData(address = Some("1.1.1.1")))
          ),
          SingleChangeCreateInfo(
            inputName = "1.1.1.1",
            `type` = "PTR",
            changeType = "DeleteRecordSet",
            ttl = Some(300),
            record = Some(RecordData(ptrdname = Some("foo.vinyldns.")))
          )
        )
      val expectedBatchChange = BatchChangeCreateInfo(changes = expectedChanges, None, None, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("DeleteRecordSet"))
        Simulate.change(recordTypeField, SimEvent.Change("A+PTR"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))
        Simulate.change(dataField, SimEvent.Change("1.1.1.1"))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post an AAAA+PTR Add batch change" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.vinyldns.",
            `type` = "AAAA",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(address = Some("1::1")))
          ),
          SingleChangeCreateInfo(
            inputName = "1::1",
            `type` = "PTR",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(ptrdname = Some("foo.vinyldns.")))
          )
        )
      val expectedBatchChange = BatchChangeCreateInfo(changes = expectedChanges, None, None, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val ttlField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("Add"))
        Simulate.change(recordTypeField, SimEvent.Change("AAAA+PTR"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))
        Simulate.change(ttlField, SimEvent.Change("300"))
        Simulate.change(dataField, SimEvent.Change("1::1"))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly http.post an AAAA+PTR DeleteRecordSet batch change" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.vinyldns.",
            `type` = "AAAA",
            changeType = "DeleteRecordSet",
            ttl = Some(300),
            record = Some(RecordData(address = Some("1::1")))
          ),
          SingleChangeCreateInfo(
            inputName = "1::1",
            `type` = "PTR",
            changeType = "DeleteRecordSet",
            ttl = Some(300),
            record = Some(RecordData(ptrdname = Some("foo.vinyldns.")))
          )
        )
      val expectedBatchChange = BatchChangeCreateInfo(changes = expectedChanges, None, None, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("DeleteRecordSet"))
        Simulate.change(recordTypeField, SimEvent.Change("AAAA+PTR"))
        Simulate.change(inputNameField, SimEvent.Change("foo.vinyldns."))
        Simulate.change(dataField, SimEvent.Change("1::1"))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)
      }
    }

    "properly display error from a submission" in new Fixture {
      val expectedChanges =
        List(
          SingleChangeCreateInfo(
            inputName = "foo.no-existo.",
            `type` = "A",
            changeType = "Add",
            ttl = Some(300),
            record = Some(RecordData(address = Some("1.1.1.1")))
          )
        )
      val expectedBatchChange = BatchChangeCreateInfo(changes = expectedChanges, None, None, None)

      val withErrors = List(
        expectedChanges.head.copy(errors = Some(List("zone discovery failed for foo.no-existo."))))
      val response = write(withErrors)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[BatchChangeCreateInfo] _)
        .expects(CreateBatchChangeRoute, write(expectedBatchChange), *, *)
        .once()
        .onCall((_, _, _, f) => f.apply(HttpResponse(400, "Bad Request", response)))

      (mockHttp.toNotification _)
        .expects(*, *, *, *)
        .once()
        .returns(None)

      ReactTestUtils.withRenderedIntoDocument(
        BatchChangeCreatePage(ToBatchChangeCreatePage, mockRouter, mockHttp)) { c =>
        c.outerHtmlScrubbed() shouldNot include("changeError")
        c.outerHtmlScrubbed() shouldNot include("zone discovery failed for foo.no-existo.")

        val changeTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-change-type")
        val recordTypeField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-type")
        val inputNameField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-input-name")
        val ttlField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val dataField = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-data")

        Simulate.change(changeTypeField, SimEvent.Change("Add"))
        Simulate.change(recordTypeField, SimEvent.Change("A"))
        Simulate.change(inputNameField, SimEvent.Change("foo.no-existo."))
        Simulate.change(ttlField, SimEvent.Change("300"))
        Simulate.change(dataField, SimEvent.Change("1.1.1.1"))

        val submitButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-submit-button")
        Simulate.submit(submitButton)

        c.outerHtmlScrubbed() should include("changeError")
        c.outerHtmlScrubbed() should include("zone discovery failed for foo.no-existo.")
      }
    }
  }
}
