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

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router.RouterCtl
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import japgolly.scalajs.react.test._
import vinyldns.client.SharedTestData
import vinyldns.client.http.{Http, HttpResponse, LookupUserRoute, UpdateZoneRoute}
import vinyldns.client.router.Page
import vinyldns.client.models.zone.ACLRule.AclType
import vinyldns.client.models.zone.{ACLRule, Rules, ZoneResponse}
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.zone.AccessLevel
import upickle.default._
import vinyldns.client.models.membership.UserResponse

import scala.language.existentials

class AclModalSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val initialAclList = List(userAclRule, groupAclRule)
  val initialGroups = generateGroupResponses(10).toList
  val initialZone =
    generateZoneResponses(1, initialGroups(0)).head.copy(acl = Rules(initialAclList))
  val mockRouter = mock[RouterCtl[Page]]

  class Fixture(isUpdate: Boolean = false) {
    val mockHttp = mock[Http]
    val existing = if (isUpdate) Some(userAclRule, 1) else None

    (mockHttp.getLoggedInUser _)
      .expects()
      .anyNumberOfTimes()
      .returns(testUser)

    val props = AclModal.Props(
      initialZone,
      mockHttp,
      initialGroups,
      generateNoOpHandler[Unit],
      generateNoOpHandler[Unit],
      existing
    )
  }

  "AclModal Create" should {
    "not allow submission without required fields" in new Fixture {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .never()

      (mockHttp.put[ZoneResponse] _)
        .expects(*, *, *, *)
        .never()

      ReactTestUtils.withRenderedIntoDocument(AclModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-acl-form")
        Simulate.submit(form)
      }
    }

    "call with confirmation when submitting" in new Fixture {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .returns(Callback.empty)

      (mockHttp.put[ZoneResponse] _)
        .expects(*, *, *, *)
        .never()

      val expectedRule = ACLRule(
        accessLevel = AccessLevel.Write,
        recordTypes = Seq(RecordType.A),
        description = Some("desc"),
        groupId = Some(initialGroups(0).id),
        recordMask = Some("mask*"),
        displayName = Some(initialGroups(0).name)
      )

      ReactTestUtils.withRenderedIntoDocument(AclModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-acl-form")
        val aclType = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(aclType, SimEvent.Change(AclType.Group.toString))

        val groupName = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-group-name")
        val accessLevel = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-accesslevel")
        val mask = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-recordmask")
        val desc = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-description")
        val recordTypes = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-recordtypes")

        Simulate.change(groupName, SimEvent.Change(expectedRule.displayName.get))
        Simulate.change(accessLevel, SimEvent.Change(expectedRule.accessLevel.toString))
        Simulate.change(mask, SimEvent.Change(expectedRule.recordMask.get))
        Simulate.change(desc, SimEvent.Change(expectedRule.description.get))
        expectedRule.recordTypes.foreach(t =>
          Simulate.change(recordTypes, SimEvent.Change(t.toString)))

        Simulate.submit(form)
      }
    }

    "properly submit create acl group rule" in new Fixture {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      val expectedRule = ACLRule(
        accessLevel = AccessLevel.Write,
        recordTypes = Seq(RecordType.A),
        description = Some("desc"),
        groupId = Some(initialGroups(0).id),
        recordMask = Some("mask*"),
        displayName = Some(initialGroups(0).name)
      )

      val updatedZone = initialZone.copy(acl = Rules(expectedRule +: initialZone.acl.rules))

      (mockHttp.put[ZoneResponse] _)
        .expects(UpdateZoneRoute(initialZone.id), write(updatedZone), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(AclModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-acl-form")
        val aclType = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(aclType, SimEvent.Change(AclType.Group.toString))

        val groupName = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-group-name")
        val accessLevel = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-accesslevel")
        val mask = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-recordmask")
        val desc = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-description")
        val recordTypes = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-recordtypes")

        Simulate.change(groupName, SimEvent.Change(expectedRule.displayName.get))
        Simulate.change(accessLevel, SimEvent.Change(expectedRule.accessLevel.toString))
        Simulate.change(mask, SimEvent.Change(expectedRule.recordMask.get))
        Simulate.change(desc, SimEvent.Change(expectedRule.description.get))
        expectedRule.recordTypes.foreach(t =>
          Simulate.change(recordTypes, SimEvent.Change(t.toString)))

        Simulate.submit(form)
      }
    }

    "properly submit create acl user rule" in new Fixture {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      val expectedRule = ACLRule(
        accessLevel = AccessLevel.Write,
        recordTypes = Seq(RecordType.A),
        description = Some("desc"),
        userId = Some(testUUID),
        userName = Some(testUser.userName),
        recordMask = Some("mask*")
      )

      val updatedZone = initialZone.copy(acl = Rules(expectedRule +: initialZone.acl.rules))

      val successResponse = HttpResponse(200, "dope", write(testUser.copy(id = testUUID)))
      (mockHttp.get[UserResponse] _)
        .expects(LookupUserRoute(expectedRule.userName.get), *, *)
        .once()
        .onCall((_, s, _) =>
          s.apply(successResponse, Some(read[UserResponse](successResponse.responseText))))

      (mockHttp.put[ZoneResponse] _)
        .expects(UpdateZoneRoute(initialZone.id), write(updatedZone), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(AclModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-acl-form")
        val aclType = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(aclType, SimEvent.Change(AclType.User.toString))

        val userName = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-username")
        val accessLevel = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-accesslevel")
        val mask = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-recordmask")
        val desc = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-description")
        val recordTypes = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-recordtypes")

        Simulate.change(userName, SimEvent.Change(expectedRule.userName.get))
        Simulate.change(accessLevel, SimEvent.Change(expectedRule.accessLevel.toString))
        Simulate.change(mask, SimEvent.Change(expectedRule.recordMask.get))
        Simulate.change(desc, SimEvent.Change(expectedRule.description.get))
        expectedRule.recordTypes.foreach(t =>
          Simulate.change(recordTypes, SimEvent.Change(t.toString)))

        Simulate.submit(form)
      }
    }
  }

  "AclModal Update" should {
    "properly submit update acl group rule" in new Fixture(isUpdate = true) {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      val expectedRule = existing.get._1.copy(
        accessLevel = AccessLevel.Write,
        recordTypes = Seq(RecordType.A),
        description = Some("desc"),
        groupId = Some(initialGroups(0).id),
        recordMask = Some("mask*"),
        userId = None,
        userName = None,
        displayName = Some(initialGroups(0).name)
      )

      val updatedZone =
        initialZone.copy(acl = Rules(initialZone.acl.rules.patch(1, List(expectedRule), 1)))

      (mockHttp.put[ZoneResponse] _)
        .expects(UpdateZoneRoute(initialZone.id), write(updatedZone), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(AclModal(props)) { c =>
        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-acl-form")
        val aclType = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-type")
        Simulate.change(aclType, SimEvent.Change(AclType.Group.toString))

        val groupName = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-group-name")
        val accessLevel = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-accesslevel")
        val mask = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-recordmask")
        val desc = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-description")
        val recordTypes = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-recordtypes")

        Simulate.change(groupName, SimEvent.Change(expectedRule.displayName.get))
        Simulate.change(accessLevel, SimEvent.Change(expectedRule.accessLevel.toString))
        Simulate.change(mask, SimEvent.Change(expectedRule.recordMask.get))
        Simulate.change(desc, SimEvent.Change(expectedRule.description.get))

        val clearTypes = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-clear")
        Simulate.click(clearTypes)
        expectedRule.recordTypes.foreach(t =>
          Simulate.change(recordTypes, SimEvent.Change(t.toString)))

        Simulate.submit(form)
      }
    }
  }
}
