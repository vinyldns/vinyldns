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

package vinyldns.client.pages.zone.view.components.recordmodal

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router.RouterCtl
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import japgolly.scalajs.react.test._
import vinyldns.client.SharedTestData
import vinyldns.client.http.{CreateRecordSetRoute, Http, UpdateRecordSetRoute}
import vinyldns.client.models.record.{RecordData, RecordSetChangeResponse, RecordSetCreateInfo}
import vinyldns.client.router.Page
import upickle.default._
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.core.domain.record.RecordType

import scala.language.existentials

class RecordSetModalSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]
  val zone = generateZoneResponses(1).head
  val existing = generateRecordSetResponses(1, zone.id).head
  val initialGroups = generateGroupResponses(1)
  val initialGroupList = GroupListResponse(initialGroups.toList, 100)

  class Fixture(isUpdate: Boolean = false) {
    val mockHttp = mock[Http]
    val record = if (isUpdate) Some(existing) else None

    val props = RecordSetModal.Props(
      mockHttp,
      zone,
      initialGroupList,
      generateNoOpHandler[Unit],
      generateNoOpHandler[Unit],
      generateNoOpHandler[Unit],
      record
    )
  }

  "RecordSetModal Create" should {
    "not allow submission without required fields" in new Fixture {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .never()

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(*, *, *, *)
        .never()

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")
        Simulate.submit(form)
      }
    }

    "call with confirmation when submitting" in new Fixture {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .returns(Callback.empty)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(*, *, *, *)
        .never()

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")
        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val recordData = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-address")

        Simulate.change(typ, SimEvent.Change("A"))
        Simulate.change(name, SimEvent.Change("foo"))
        Simulate.change(ttl, SimEvent.Change("500"))
        Simulate.change(recordData, SimEvent.Change("1.1.1.1"))

        Simulate.submit(form)
      }
    }

    "properly http.post a record with an owner group id" in new Fixture {
      val expectedData =
        List(RecordData(address = Some("1.1.1.1")), RecordData(address = Some("2.2.2.2")))
      val expectedRecord =
        RecordSetCreateInfo(zone.id, RecordType.A, "foo", 500, expectedData, Some(testUUID))

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(CreateRecordSetRoute(zone.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")

        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val recordData = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-address")
        val ownerGroup = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-owner-group")

        Simulate.change(name, SimEvent.Change(expectedRecord.name))
        Simulate.change(ttl, SimEvent.Change(expectedRecord.ttl.toString))
        Simulate.change(recordData, SimEvent.Change("1.1.1.1\n2.2.2.2"))
        Simulate.change(ownerGroup, SimEvent.Change(testUUID))

        Simulate.submit(form)
      }
    }

    "properly http.post a create A record" in new Fixture {
      val expectedData =
        List(RecordData(address = Some("1.1.1.1")), RecordData(address = Some("2.2.2.2")))
      val expectedRecord =
        RecordSetCreateInfo(zone.id, RecordType.A, "foo", 500, expectedData, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(CreateRecordSetRoute(zone.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")

        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val recordData = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-address")

        Simulate.change(name, SimEvent.Change(expectedRecord.name))
        Simulate.change(ttl, SimEvent.Change(expectedRecord.ttl.toString))
        Simulate.change(recordData, SimEvent.Change("1.1.1.1\n2.2.2.2"))

        Simulate.submit(form)
      }
    }

    "properly http.post a create AAAA record" in new Fixture {
      val expectedData =
        List(RecordData(address = Some("1::1")), RecordData(address = Some("2::2")))
      val expectedRecord =
        RecordSetCreateInfo(zone.id, RecordType.AAAA, "foo", 500, expectedData, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(CreateRecordSetRoute(zone.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")

        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val recordData = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-address")

        Simulate.change(name, SimEvent.Change(expectedRecord.name))
        Simulate.change(ttl, SimEvent.Change(expectedRecord.ttl.toString))
        Simulate.change(recordData, SimEvent.Change("1::1\n2::2"))

        Simulate.submit(form)
      }
    }

    "properly http.post a create CNAME record" in new Fixture {
      val expectedData =
        List(RecordData(cname = Some("foo.bar.")))
      val expectedRecord =
        RecordSetCreateInfo(zone.id, RecordType.CNAME, "foo", 500, expectedData, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(CreateRecordSetRoute(zone.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")

        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val recordData = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-cname")

        Simulate.change(name, SimEvent.Change(expectedRecord.name))
        Simulate.change(ttl, SimEvent.Change(expectedRecord.ttl.toString))
        Simulate.change(recordData, SimEvent.Change("foo.bar."))

        Simulate.submit(form)
      }
    }

    "properly http.post a create PTR record" in new Fixture {
      val expectedData =
        List(RecordData(ptrdname = Some("ptr.one.")), RecordData(ptrdname = Some("ptr.two.")))
      val expectedRecord =
        RecordSetCreateInfo(zone.id, RecordType.PTR, "foo", 500, expectedData, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(CreateRecordSetRoute(zone.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")

        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val recordData = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ptr")

        Simulate.change(name, SimEvent.Change(expectedRecord.name))
        Simulate.change(ttl, SimEvent.Change(expectedRecord.ttl.toString))
        Simulate.change(recordData, SimEvent.Change("ptr.one.\nptr.two."))

        Simulate.submit(form)
      }
    }

    "properly http.post a create NS record" in new Fixture {
      val expectedData =
        List(RecordData(nsdname = Some("ns.one.")), RecordData(nsdname = Some("ns.two.")))
      val expectedRecord =
        RecordSetCreateInfo(zone.id, RecordType.NS, "foo", 500, expectedData, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(CreateRecordSetRoute(zone.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")

        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val recordData = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ns")

        Simulate.change(name, SimEvent.Change(expectedRecord.name))
        Simulate.change(ttl, SimEvent.Change(expectedRecord.ttl.toString))
        Simulate.change(recordData, SimEvent.Change("ns.one.\nns.two."))

        Simulate.submit(form)
      }
    }

    "properly http.post a create SPF record" in new Fixture {
      val expectedData =
        List(RecordData(text = Some("spf.one.")), RecordData(text = Some("spf.two.")))
      val expectedRecord =
        RecordSetCreateInfo(zone.id, RecordType.SPF, "foo", 500, expectedData, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(CreateRecordSetRoute(zone.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")

        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val recordData = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-spf")

        Simulate.change(name, SimEvent.Change(expectedRecord.name))
        Simulate.change(ttl, SimEvent.Change(expectedRecord.ttl.toString))
        Simulate.change(recordData, SimEvent.Change("spf.one.\nspf.two."))

        Simulate.submit(form)
      }
    }

    "properly http.post a create TXT record" in new Fixture {
      val expectedData =
        List(RecordData(text = Some("txt.one.")), RecordData(text = Some("txt.two.")))
      val expectedRecord =
        RecordSetCreateInfo(zone.id, RecordType.TXT, "foo", 500, expectedData, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(CreateRecordSetRoute(zone.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")

        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")
        val recordData = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-txt")

        Simulate.change(name, SimEvent.Change(expectedRecord.name))
        Simulate.change(ttl, SimEvent.Change(expectedRecord.ttl.toString))
        Simulate.change(recordData, SimEvent.Change("txt.one.\ntxt.two."))

        Simulate.submit(form)
      }
    }

    "properly http.post a create MX record" in new Fixture {
      val expectedData =
        List(
          RecordData(preference = Some(1), exchange = Some("exchange.one.")),
          RecordData(preference = Some(2), exchange = Some("exchange.two.")))
      val expectedRecord =
        RecordSetCreateInfo(zone.id, RecordType.MX, "foo", 500, expectedData, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(CreateRecordSetRoute(zone.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")

        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")

        val addRow = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-add")
        Simulate.click(addRow)

        val preferences = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-preference")
        val exchanges = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-exchange")

        Simulate.change(name, SimEvent.Change(expectedRecord.name))
        Simulate.change(ttl, SimEvent.Change(expectedRecord.ttl.toString))

        Simulate.change(preferences(0), SimEvent.Change("1"))
        Simulate.change(exchanges(0), SimEvent.Change("exchange.one."))

        Simulate.change(preferences(1), SimEvent.Change("2"))
        Simulate.change(exchanges(1), SimEvent.Change("exchange.two."))

        Simulate.submit(form)
      }
    }

    "properly http.post a create SRV record" in new Fixture {
      val expectedData =
        List(
          RecordData(priority = Some(1), weight = Some(2), port = Some(3), target = Some("t1.")),
          RecordData(priority = Some(4), weight = Some(5), port = Some(6), target = Some("t2."))
        )
      val expectedRecord =
        RecordSetCreateInfo(zone.id, RecordType.SRV, "foo", 500, expectedData, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(CreateRecordSetRoute(zone.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")

        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")

        val addRow = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-add")
        Simulate.click(addRow)

        val priorities = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-priority")
        val weights = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-weight")
        val ports = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-port")
        val targets = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-target")

        Simulate.change(name, SimEvent.Change(expectedRecord.name))
        Simulate.change(ttl, SimEvent.Change(expectedRecord.ttl.toString))

        Simulate.change(priorities(0), SimEvent.Change("1"))
        Simulate.change(weights(0), SimEvent.Change("2"))
        Simulate.change(ports(0), SimEvent.Change("3"))
        Simulate.change(targets(0), SimEvent.Change("t1."))

        Simulate.change(priorities(1), SimEvent.Change("4"))
        Simulate.change(weights(1), SimEvent.Change("5"))
        Simulate.change(ports(1), SimEvent.Change("6"))
        Simulate.change(targets(1), SimEvent.Change("t2."))

        Simulate.submit(form)
      }
    }

    "properly http.post a create SSHFP record" in new Fixture {
      val expectedData =
        List(
          RecordData(algorithm = Some(1), `type` = Some(1), fingerprint = Some("f1")),
          RecordData(algorithm = Some(3), `type` = Some(2), fingerprint = Some("f2")))
      val expectedRecord =
        RecordSetCreateInfo(zone.id, RecordType.SSHFP, "foo", 500, expectedData, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(CreateRecordSetRoute(zone.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")

        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")

        val addRow = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-add")
        Simulate.click(addRow)

        val algorithms = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-algorithm")
        val types = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-rd-type")
        val fingerprints = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-fingerprint")

        Simulate.change(name, SimEvent.Change(expectedRecord.name))
        Simulate.change(ttl, SimEvent.Change(expectedRecord.ttl.toString))

        Simulate.change(algorithms(0), SimEvent.Change("1"))
        Simulate.change(types(0), SimEvent.Change("1"))
        Simulate.change(fingerprints(0), SimEvent.Change("f1"))

        Simulate.change(algorithms(1), SimEvent.Change("3"))
        Simulate.change(types(1), SimEvent.Change("2"))
        Simulate.change(fingerprints(1), SimEvent.Change("f2"))

        Simulate.submit(form)
      }
    }

    "properly http.post a create DS record" in new Fixture {
      val expectedData =
        List(
          RecordData(
            keytag = Some(1),
            algorithm = Some(3),
            digesttype = Some(1),
            digest = Some("ds1")),
          RecordData(
            keytag = Some(2),
            algorithm = Some(253),
            digesttype = Some(2),
            digest = Some("ds2"))
        )
      val expectedRecord =
        RecordSetCreateInfo(zone.id, RecordType.DS, "foo", 500, expectedData, None)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(CreateRecordSetRoute(zone.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")

        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val ttl = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-ttl")

        val addRow = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-add")
        Simulate.click(addRow)

        val keyTags = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-keytag")
        val algorithms = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-algorithm")
        val digestTypes = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-digesttype")
        val digests = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-digest")

        Simulate.change(name, SimEvent.Change(expectedRecord.name))
        Simulate.change(ttl, SimEvent.Change(expectedRecord.ttl.toString))

        Simulate.change(keyTags(0), SimEvent.Change("1"))
        Simulate.change(algorithms(0), SimEvent.Change("3"))
        Simulate.change(digestTypes(0), SimEvent.Change("1"))
        Simulate.change(digests(0), SimEvent.Change("ds1"))

        Simulate.change(keyTags(1), SimEvent.Change("2"))
        Simulate.change(algorithms(1), SimEvent.Change("253"))
        Simulate.change(digestTypes(1), SimEvent.Change("2"))
        Simulate.change(digests(1), SimEvent.Change("ds2"))

        Simulate.submit(form)
      }
    }
  }

  "RecordSetModal Update" should {
    "not allow submission without required fields" in new Fixture(isUpdate = true) {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .never()

      (mockHttp.post[RecordSetChangeResponse] _)
        .expects(*, *, *, *)
        .never()

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")
        val name = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        Simulate.change(name, SimEvent.Change(""))

        Simulate.submit(form)
      }
    }

    "call with confirmation when submitting" in new Fixture(isUpdate = true) {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .returns(Callback.empty)

      (mockHttp.put[RecordSetChangeResponse] _)
        .expects(*, *, *, *)
        .never()

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")
        Simulate.submit(form)
      }
    }

    "properly http.put an updated record" in new Fixture(isUpdate = true) {
      val expectedData =
        List(RecordData(cname = Some("cname.")))
      val expectedRecord = existing.copy(`type` = RecordType.CNAME, records = expectedData)

      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.put[RecordSetChangeResponse] _)
        .expects(UpdateRecordSetRoute(zone.id, existing.id), write(expectedRecord), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(RecordSetModal(props)) { c =>
        val typ = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(typ, SimEvent.Change(expectedRecord.`type`.toString))

        val recordData = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-cname")
        Simulate.change(recordData, SimEvent.Change("cname."))

        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-record-form")
        Simulate.submit(form)
      }
    }
  }
}
