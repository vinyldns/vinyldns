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

package vinyldns.client.pages.group.list.components

import org.scalatest._
import japgolly.scalajs.react.test._
import japgolly.scalajs.react._
import org.scalamock.scalatest.MockFactory
import vinyldns.client.http.{CreateGroupRoute, Http, UpdateGroupRoute}
import vinyldns.client.models.membership.{GroupCreateInfo, GroupResponse, Id}
import upickle.default.write
import vinyldns.client.SharedTestData

import scala.language.existentials

class GroupModalSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  "GroupModal create" should {
    "be a create modal if no existing group" in {
      val mockHttp = mock[Http]

      val props =
        GroupModal.Props(mockHttp, generateNoOpHandler[Unit], generateNoOpHandler[Unit])

      ReactTestUtils.withRenderedIntoDocument(GroupModal(props)) { c =>
        c.state.isUpdate shouldBe false
        c.outerHtmlScrubbed() should include("Create Group")
        c.outerHtmlScrubbed() shouldNot include("Update Group")
      }
    }

    "not call withConfirmation if submitted without required fields" in {
      val mockHttp = mock[Http]

      (mockHttp.withConfirmation _).expects(*, *).never()
      (mockHttp.post _).expects(*, *, *, *).never()

      val props =
        GroupModal.Props(mockHttp, generateNoOpHandler[Unit], generateNoOpHandler[Unit])

      ReactTestUtils.withRenderedIntoDocument(GroupModal(props)) { c =>
        val form =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-group-form")
        Simulate.submit(form)
      }
    }

    "call withConfirmation if submitting with required fields" in {
      val mockHttp = mock[Http]

      (mockHttp.withConfirmation _).expects(*, *).once().returns(Callback.empty)
      (mockHttp.post _).expects(*, *, *, *).never()

      val props =
        GroupModal.Props(mockHttp, generateNoOpHandler[Unit], generateNoOpHandler[Unit])

      ReactTestUtils.withRenderedIntoDocument(GroupModal(props)) { c =>
        val nameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val emailField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-email")
        val form =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-group-form")

        Simulate.change(nameField, SimEvent.Change("test-name"))
        Simulate.change(emailField, SimEvent.Change("test@test.com"))
        Simulate.submit(form)
      }
    }

    "call http.post if submitting with required fields and confirming" in {
      val mockHttp = mock[Http]

      val testGroup =
        GroupCreateInfo("test-name", "test@email.com", Seq(Id(testUser.id)), Seq(Id(testUser.id)))

      (mockHttp.getLoggedInUser _).expects().once().returns(testUser)
      (mockHttp.withConfirmation _).expects(*, *).once().onCall((_, cb) => cb)
      (mockHttp.post[GroupResponse] _)
        .expects(CreateGroupRoute, write(testGroup), *, *)
        .once()
        .returns(Callback.empty)

      val props =
        GroupModal.Props(mockHttp, generateNoOpHandler[Unit], generateNoOpHandler[Unit])

      ReactTestUtils.withRenderedIntoDocument(GroupModal(props)) { c =>
        val nameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val emailField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-email")
        val form =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-group-form")

        Simulate.change(nameField, SimEvent.Change(testGroup.name))
        Simulate.change(emailField, SimEvent.Change(testGroup.email))
        Simulate.submit(form)
      }
    }
  }

  "GroupModal update" should {
    "be an update modal if existing group" in {
      val mockHttp = mock[Http]
      val existing = generateGroupResponses(1).head

      val props =
        GroupModal.Props(
          mockHttp,
          generateNoOpHandler[Unit],
          generateNoOpHandler[Unit],
          Some(existing))

      ReactTestUtils.withRenderedIntoDocument(GroupModal(props)) { c =>
        c.state.isUpdate shouldBe true
        c.outerHtmlScrubbed() shouldNot include("Create Group")
        c.outerHtmlScrubbed() should include(s"Update Group ${existing.id}")
      }
    }

    "not call withConfirmation if submitted without required fields" in {
      val mockHttp = mock[Http]
      val existing = generateGroupResponses(1).head

      (mockHttp.withConfirmation _).expects(*, *).never()
      (mockHttp.put _).expects(*, *, *, *).never()

      val props =
        GroupModal.Props(
          mockHttp,
          generateNoOpHandler[Unit],
          generateNoOpHandler[Unit],
          Some(existing))

      ReactTestUtils.withRenderedIntoDocument(GroupModal(props)) { c =>
        // clear fields since its an update
        val nameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val emailField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-email")
        val descriptionField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-description")
        val form =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-group-form")

        Simulate.change(nameField, SimEvent.Change(""))
        Simulate.change(emailField, SimEvent.Change(""))
        Simulate.change(descriptionField, SimEvent.Change(""))

        Simulate.submit(form)
      }
    }

    "call withConfirmation if submitting with required fields" in {
      val mockHttp = mock[Http]
      val existing = generateGroupResponses(1).head

      (mockHttp.withConfirmation _).expects(*, *).once().returns(Callback.empty)
      (mockHttp.put _).expects(*, *, *, *).never()

      val props =
        GroupModal.Props(
          mockHttp,
          generateNoOpHandler[Unit],
          generateNoOpHandler[Unit],
          Some(existing))

      ReactTestUtils.withRenderedIntoDocument(GroupModal(props)) { c =>
        val form =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-group-form")

        Simulate.submit(form)
      }
    }

    "call http.put if submitting with required fields and confirming" in {
      val mockHttp = mock[Http]
      val existing = generateGroupResponses(1).head

      (mockHttp.withConfirmation _).expects(*, *).once().onCall((_, cb) => cb)
      (mockHttp.put[GroupResponse] _)
        .expects(UpdateGroupRoute(existing.id), write(existing), *, *)
        .once()
        .returns(Callback.empty)

      val props =
        GroupModal.Props(
          mockHttp,
          generateNoOpHandler[Unit],
          generateNoOpHandler[Unit],
          Some(existing))

      ReactTestUtils.withRenderedIntoDocument(GroupModal(props)) { c =>
        val form =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-group-form")

        Simulate.submit(form)
      }
    }

    "submit properly updated group" in {
      val mockHttp = mock[Http]
      val existing = generateGroupResponses(1).head
      val updated = existing.copy(
        name = "new-name",
        email = "new-email@test.com",
        description = Some("new desc"))

      (mockHttp.withConfirmation _).expects(*, *).once().onCall((_, cb) => cb)
      (mockHttp.put[GroupResponse] _)
        .expects(UpdateGroupRoute(updated.id), write(updated), *, *)
        .once()
        .returns(Callback.empty)

      val props =
        GroupModal.Props(
          mockHttp,
          generateNoOpHandler[Unit],
          generateNoOpHandler[Unit],
          Some(existing))

      ReactTestUtils.withRenderedIntoDocument(GroupModal(props)) { c =>
        val form =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-group-form")

        val nameField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-name")
        val emailField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-email")
        val descriptionField =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-description")

        Simulate.change(nameField, SimEvent.Change(updated.name))
        Simulate.change(emailField, SimEvent.Change(updated.email))
        Simulate.change(descriptionField, SimEvent.Change(updated.description.get))
        Simulate.submit(form)
      }
    }
  }
}
