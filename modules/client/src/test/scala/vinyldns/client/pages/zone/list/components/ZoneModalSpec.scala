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

package vinyldns.client.pages.zone.list.components

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import vinyldns.client.SharedTestData
import vinyldns.client.http.{CreateZoneRoute, Http, UpdateZoneRoute}
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.client.models.zone.{ZoneConnection, ZoneCreateInfo, ZoneResponse}
import upickle.default.write

import scala.language.existentials

class ZoneModalSpec extends WordSpec with Matchers with MockFactory with SharedTestData {

  trait Fixture {
    val mockHttp = mock[Http]
    val groupList = GroupListResponse(generateGroupResponses(2).toList, 100)
    val props =
      ZoneModal.Props(mockHttp, generateNoOpHandler[Unit], generateNoOpHandler[Unit], groupList)
  }

  "ZoneModal Create" should {
    "not call withConfirmation if submitted without required fields" in new Fixture {
      (mockHttp.withConfirmation _).expects(*, *).never()
      (mockHttp.post _).expects(*, *, *, *).never()

      ReactTestUtils.withRenderedIntoDocument(ZoneModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-zone-form")
        Simulate.submit(form)
      }
    }

    "call withConfirmation if submitting with required fields" in new Fixture {
      (mockHttp.withConfirmation _).expects(*, *).once().returns(Callback.empty)
      (mockHttp.post _).expects(*, *, *, *).never()

      ReactTestUtils.withRenderedIntoDocument(ZoneModal(props)) { c =>
        val nameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val emailField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-email")
        val adminField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-group-admin")

        Simulate.change(nameField, SimEvent.Change("test-zone."))
        Simulate.change(emailField, SimEvent.Change("test@email.com"))
        Simulate.change(adminField, SimEvent.Change(groupList.groups(0).name))

        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-zone-form")
        Simulate.submit(form)
      }
    }

    "call http.post if submitting with required fields and confirming" in new Fixture {
      val expectedZone = ZoneCreateInfo(
        "test-zone.",
        "test@email.com",
        groupList.groups(0).id,
        Some(groupList.groups(0).name),
        false,
        None,
        None)

      (mockHttp.withConfirmation _).expects(*, *).once().onCall((_, cb) => cb)
      (mockHttp.post[ZoneResponse] _)
        .expects(CreateZoneRoute, write(expectedZone), *, *)
        .never()
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(ZoneModal(props)) { c =>
        val nameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val emailField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-email")
        val adminField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-group-admin")

        Simulate.change(nameField, SimEvent.Change("test-zone."))
        Simulate.change(emailField, SimEvent.Change("test@email.com"))
        Simulate.change(adminField, SimEvent.Change(groupList.groups(0).name))

        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-zone-form")
        Simulate.submit(form)
      }
    }

    "not allow user to submit unless adminGroupId is uuid" in new Fixture {
      (mockHttp.withConfirmation _).expects(*, *).never()
      (mockHttp.post _).expects(*, *, *, *).never()

      ReactTestUtils.withRenderedIntoDocument(ZoneModal(props)) { c =>
        val nameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val emailField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-email")
        val adminField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-group-admin")

        Simulate.change(nameField, SimEvent.Change("test-zone."))
        Simulate.change(emailField, SimEvent.Change("test@email.com"))
        Simulate.change(adminField, SimEvent.Change("not-uuid"))

        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-zone-form")
        Simulate.submit(form)
      }
    }

    "make request with shared toggled on" in new Fixture {
      val expectedZone =
        ZoneCreateInfo(
          "test-zone.",
          "test@email.com",
          groupList.groups(0).id,
          Some(groupList.groups(0).name),
          true,
          None,
          None)

      (mockHttp.withConfirmation _).expects(*, *).once().onCall((_, cb) => cb)
      (mockHttp.post[ZoneResponse] _)
        .expects(CreateZoneRoute, write(expectedZone), *, *)
        .never()
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(ZoneModal(props)) { c =>
        val sharedToggle =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-shared")
        Simulate.change(sharedToggle, SimEvent.Change())

        val nameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val emailField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-email")
        val adminField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-group-admin")

        Simulate.change(nameField, SimEvent.Change("test-zone."))
        Simulate.change(emailField, SimEvent.Change("test@email.com"))
        Simulate.change(adminField, SimEvent.Change(groupList.groups(0).name))

        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-zone-form")
        Simulate.submit(form)
      }
    }

    "make request with customServer toggled on" in new Fixture {
      val expectedZone =
        ZoneCreateInfo(
          "test-zone.",
          "test@email.com",
          groupList.groups(0).id,
          Some(groupList.groups(0).name),
          false,
          Some(ZoneConnection("name", "name", "key", "1.1.1.1")),
          None)

      (mockHttp.withConfirmation _).expects(*, *).once().onCall((_, cb) => cb)
      (mockHttp.post[ZoneResponse] _)
        .expects(CreateZoneRoute, write(expectedZone), *, *)
        .never()
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(ZoneModal(props)) { c =>
        val customServerToggle =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-custom-server")
        Simulate.change(customServerToggle, SimEvent.Change())

        val nameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val emailField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-email")
        val adminField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-group-admin")
        val customKeyName =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-connection-key-name")
        val customKey = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-connection-key")
        val customServer =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-connection-server")

        Simulate.change(nameField, SimEvent.Change("test-zone."))
        Simulate.change(emailField, SimEvent.Change("test@email.com"))
        Simulate.change(adminField, SimEvent.Change(groupList.groups(0).name))
        Simulate.change(customKeyName, SimEvent.Change("name"))
        Simulate.change(customKey, SimEvent.Change("key"))
        Simulate.change(customServer, SimEvent.Change("1.1.1.1"))

        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-zone-form")
        Simulate.submit(form)
      }
    }

    "make request with customTransfer toggled on" in new Fixture {
      val expectedZone =
        ZoneCreateInfo(
          "test-zone.",
          "test@email.com",
          groupList.groups(0).id,
          Some(groupList.groups(0).name),
          false,
          None,
          transferConnection = Some(ZoneConnection("name", "name", "key", "1.1.1.1"))
        )

      (mockHttp.withConfirmation _).expects(*, *).once().onCall((_, cb) => cb)
      (mockHttp.post[ZoneResponse] _)
        .expects(CreateZoneRoute, write(expectedZone), *, *)
        .never()
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(ZoneModal(props)) { c =>
        val customServerToggle =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-custom-transfer")
        Simulate.change(customServerToggle, SimEvent.Change())

        val nameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val emailField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-email")
        val adminField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-group-admin")
        val customKeyName =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-connection-key-name")
        val customKey = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-connection-key")
        val customServer =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-connection-server")

        Simulate.change(nameField, SimEvent.Change("test-zone."))
        Simulate.change(emailField, SimEvent.Change("test@email.com"))
        Simulate.change(adminField, SimEvent.Change(groupList.groups(0).name))
        Simulate.change(customKeyName, SimEvent.Change("name"))
        Simulate.change(customKey, SimEvent.Change("key"))
        Simulate.change(customServer, SimEvent.Change("1.1.1.1"))

        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-zone-form")
        Simulate.submit(form)
      }
    }

    "make request with customConnection and customTransfer toggled on" in new Fixture {
      val expectedZone =
        ZoneCreateInfo(
          "test-zone.",
          "test@email.com",
          groupList.groups(0).id,
          Some(groupList.groups(0).name),
          false,
          connection = Some(ZoneConnection("name", "name", "key", "server")),
          transferConnection = Some(ZoneConnection("tname", "tname", "tkey", "tserver"))
        )

      (mockHttp.withConfirmation _).expects(*, *).once().onCall((_, cb) => cb)
      (mockHttp.post[ZoneResponse] _)
        .expects(CreateZoneRoute, write(expectedZone), *, *)
        .never()
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(ZoneModal(props)) { c =>
        val customServerToggle =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-custom-server")
        Simulate.change(customServerToggle, SimEvent.Change())

        val customTransferServerToggle =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-custom-transfer")
        Simulate.change(customTransferServerToggle, SimEvent.Change())

        val nameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val emailField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-email")
        val adminField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-group-admin")

        val customKeyName =
          ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-connection-key-name")(0)
        val customKey =
          ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-connection-key")(0)
        val customServer =
          ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-connection-server")(0)

        val customTransferKeyName =
          ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-connection-key-name")(1)
        val customTransferKey =
          ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-connection-key")(1)
        val customTransferServer =
          ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-connection-server")(1)

        Simulate.change(nameField, SimEvent.Change("test-zone."))
        Simulate.change(emailField, SimEvent.Change("test@email.com"))
        Simulate.change(adminField, SimEvent.Change(groupList.groups(0).name))

        Simulate.change(customKeyName, SimEvent.Change("name"))
        Simulate.change(customKey, SimEvent.Change("key"))
        Simulate.change(customServer, SimEvent.Change("server"))

        Simulate.change(customTransferKeyName, SimEvent.Change("tname"))
        Simulate.change(customTransferKey, SimEvent.Change("tkey"))
        Simulate.change(customTransferServer, SimEvent.Change("tserver"))

        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-zone-form")
        Simulate.submit(form)
      }
    }
  }

  "ZoneModal Update" should {
    "correctly http.put a zone update" in new Fixture {
      val existing = generateZoneResponses(1, groupList.groups(0)).head
      val expected = existing.copy(
        shared = true,
        connection = Some(ZoneConnection("name", "name", "key", "server")),
        transferConnection = Some(ZoneConnection("tname", "tname", "tkey", "tserver"))
      )

      (mockHttp.withConfirmation _).expects(*, *).once().onCall((_, cb) => cb)
      (mockHttp.put[ZoneResponse] _)
        .expects(UpdateZoneRoute(expected.id), write(expected), *, *)
        .never()
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(ZoneModal(props.copy(existing = Some(existing)))) {
        c =>
          c.state.zone shouldBe existing

          val sharedToggle =
            ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-shared")
          Simulate.change(sharedToggle, SimEvent.Change())

          val customServerToggle =
            ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-custom-server")
          Simulate.change(customServerToggle, SimEvent.Change())

          val customTransferServerToggle =
            ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-custom-transfer")
          Simulate.change(customTransferServerToggle, SimEvent.Change())

          val customKeyName =
            ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-connection-key-name")(0)
          val customKey =
            ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-connection-key")(0)
          val customServer =
            ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-connection-server")(0)

          val customTransferKeyName =
            ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-connection-key-name")(1)
          val customTransferKey =
            ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-connection-key")(1)
          val customTransferServer =
            ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-connection-server")(1)

          Simulate.change(customKeyName, SimEvent.Change("name"))
          Simulate.change(customKey, SimEvent.Change("key"))
          Simulate.change(customServer, SimEvent.Change("server"))

          Simulate.change(customTransferKeyName, SimEvent.Change("tname"))
          Simulate.change(customTransferKey, SimEvent.Change("tkey"))
          Simulate.change(customTransferServer, SimEvent.Change("tserver"))

          val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-zone-form")
          Simulate.submit(form)
      }
    }
  }
}
