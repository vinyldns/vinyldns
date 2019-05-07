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

package vinyldns.client.pages.group.view.components

import japgolly.scalajs.react.Callback
import org.scalatest._
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import vinyldns.client.http.{Http, HttpResponse, LookupUserRoute, UpdateGroupRoute}
import vinyldns.client.SharedTestData
import vinyldns.client.models.membership.{GroupResponse, Id, UserResponse}
import upickle.default.write

import scala.language.existentials

class NewMemberFormSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val group = generateGroupResponses(1).head

  "NewMemberForm" should {
    "call lookup user when submitted with username" in {
      val mockHttp = mock[Http]

      (mockHttp
        .get[UserResponse] _)
        .expects(LookupUserRoute("dummyUser"), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        NewMemberForm(NewMemberForm.Props(mockHttp, group, generateNoOpHandler[Unit]))) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithTag(c, "form")
        val input = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-new-member-username")
        Simulate.change(input, SimEvent.Change("dummyUser"))
        Simulate.submit(form)
      }
    }

    "do not call withConfirmation when lookup user fails" in {
      val mockHttp = mock[Http]
      val failedResponse = HttpResponse(500, "service error", "failed lookup")

      (mockHttp
        .get[UserResponse] _)
        .expects(LookupUserRoute("dummyUser"), *, *)
        .once()
        .onCall((_, _, f) => f.apply(failedResponse))

      (mockHttp.toNotification _).expects(*, *, *, *).once().returns(None)

      ReactTestUtils.withRenderedIntoDocument(
        NewMemberForm(NewMemberForm.Props(mockHttp, group, generateNoOpHandler[Unit]))) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithTag(c, "form")
        val input = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-new-member-username")
        Simulate.change(input, SimEvent.Change("dummyUser"))
        Simulate.submit(form)
      }
    }

    "call withConfirmation when lookup user succeeds" in {
      val mockHttp = mock[Http]
      val successResponse = HttpResponse(200, "dope", write(dummyUser))

      (mockHttp
        .get[UserResponse] _)
        .expects(LookupUserRoute("dummyUser"), *, *)
        .once()
        .onCall((_, s, _) => s.apply(successResponse, Some(dummyUser)))

      (mockHttp.withConfirmation _).expects(*, *).once().returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        NewMemberForm(NewMemberForm.Props(mockHttp, group, generateNoOpHandler[Unit]))) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithTag(c, "form")
        val input = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-new-member-username")
        Simulate.change(input, SimEvent.Change("dummyUser"))
        Simulate.submit(form)
      }
    }

    "call http.post with correctly updated group when not adding a manager" in {
      val mockHttp = mock[Http]
      val successResponse = HttpResponse(200, "dope", write(dummyUser))
      val expectedMembers = group.members ++ Seq(Id(dummyUser.id))
      val expectedGroup = group.copy(members = expectedMembers)

      (mockHttp
        .get[UserResponse] _)
        .expects(LookupUserRoute("dummyUser"), *, *)
        .once()
        .onCall((_, s, _) => s.apply(successResponse, Some(dummyUser)))

      (mockHttp.withConfirmation _).expects(*, *).once().onCall((_, cb) => cb)

      (mockHttp
        .put[GroupResponse] _)
        .expects(UpdateGroupRoute(group.id), write(expectedGroup), *, *)
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        NewMemberForm(NewMemberForm.Props(mockHttp, group, generateNoOpHandler[Unit]))) { c =>
        c.props.group.members shouldNot contain(Id(dummyUser.id))

        val form = ReactTestUtils.findRenderedDOMComponentWithTag(c, "form")
        val input = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-new-member-username")
        Simulate.change(input, SimEvent.Change("dummyUser"))
        Simulate.submit(form)
      }
    }

    "call http.post with correctly updated group when adding a manager" in {
      val mockHttp = mock[Http]
      val successResponse = HttpResponse(200, "dope", write(dummyUser))
      val expectedMembers = group.members ++ Seq(Id(dummyUser.id))
      val expectedGroup = group.copy(members = expectedMembers, admins = expectedMembers)

      (mockHttp
        .get[UserResponse] _)
        .expects(LookupUserRoute("dummyUser"), *, *)
        .once()
        .onCall((_, s, _) => s.apply(successResponse, Some(dummyUser)))

      (mockHttp.withConfirmation _).expects(*, *).once().onCall((_, cb) => cb)

      (mockHttp
        .put[GroupResponse] _)
        .expects(UpdateGroupRoute(group.id), write(expectedGroup), *, *)
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        NewMemberForm(NewMemberForm.Props(mockHttp, group, generateNoOpHandler[Unit]))) { c =>
        c.props.group.members shouldNot contain(Id(dummyUser.id))
        c.props.group.admins shouldNot contain(Id(dummyUser.id))

        val form = ReactTestUtils.findRenderedDOMComponentWithTag(c, "form")
        val input = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-new-member-username")
        val managerCheckbox =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-new-member-manager")
        Simulate.change(input, SimEvent.Change("dummyUser"))
        Simulate.change(managerCheckbox, SimEvent.Change())
        Simulate.submit(form)
      }
    }
  }
}
