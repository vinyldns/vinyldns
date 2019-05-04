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
import org.scalatest._
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import vinyldns.client.SharedTestData
import vinyldns.client.http._
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.client.models.zone.{ACLRule, Rules, ZoneResponse}
import vinyldns.client.router.Page
import upickle.default.write

import scala.language.existentials

class AclTableSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val initialAclList = List(userAclRule, groupAclRule)
  val initialGroups = generateGroupResponses(1)
  val initialGroupList = GroupListResponse(initialGroups.toList, 100)
  val initialZone =
    generateZoneResponses(1, initialGroups(0)).head.copy(acl = Rules(initialAclList))
  val mockRouter = mock[RouterCtl[Page]]

  trait Fixture {
    val mockHttp = mock[Http]
    val props = AclTable.Props(
      initialZone,
      initialGroups.toList,
      mockHttp,
      mockRouter,
      generateNoOpHandler[Unit]
    )
  }

  "AclTable" should {
    "show no rules found if ACL list is empty" in {
      val mockHttp = mock[Http]
      val zoneWithNoACl = initialZone.copy(acl = Rules(List()))
      val props = AclTable.Props(
        zoneWithNoACl,
        initialGroups.toList,
        mockHttp,
        mockRouter,
        generateNoOpHandler[Unit]
      )

      ReactTestUtils.withRenderedIntoDocument(AclTable(props)) { c =>
        c.props.zone.acl shouldBe Rules(List())
        c.outerHtmlScrubbed() should include("No rules found")
        ReactTestUtils.scryRenderedDOMComponentsWithTag(c, "table") shouldBe empty
      }
    }

    "show create acl modal when clicking create button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(AclTable(props)) { c =>
        c.state.showAclCreateModal shouldBe false
        c.state.showAclUpdateModal shouldBe false

        ReactTestUtils.scryRenderedComponentsWithType(c, AclModal.component) shouldBe empty

        val createButton =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-acl")
        Simulate.click(createButton)

        c.state.showAclCreateModal shouldBe true
        c.state.showAclUpdateModal shouldBe false

        ReactTestUtils.findRenderedComponentWithType(c, AclModal.component)
      }
    }

    "show update acl modal when clicking update button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(AclTable(props)) { c =>
        c.state.showAclCreateModal shouldBe false
        c.state.showAclUpdateModal shouldBe false

        ReactTestUtils.scryRenderedComponentsWithType(c, AclModal.component) shouldBe empty

        val updateButton =
          ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-edit")(0)
        Simulate.click(updateButton)

        c.state.showAclCreateModal shouldBe false
        c.state.showAclUpdateModal shouldBe true

        ReactTestUtils.findRenderedComponentWithType(c, AclModal.component)
      }
    }

    "close create acl modal after clicking close button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(AclTable(props)) { c =>
        c.state.showAclCreateModal shouldBe false

        val createButton =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-acl")
        Simulate.click(createButton)
        c.state.showAclCreateModal shouldBe true

        val modal = ReactTestUtils.findRenderedComponentWithType(c, AclModal.component)

        val closeButton =
          ReactTestUtils.findRenderedDOMComponentWithClass(modal, "test-close-acl")
        Simulate.click(closeButton)

        c.state.showAclCreateModal shouldBe false
        ReactTestUtils.scryRenderedComponentsWithType(c, AclModal.component) shouldBe empty
      }
    }

    "close update acl modal after clicking close button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(AclTable(props)) { c =>
        c.state.showAclUpdateModal shouldBe false

        val updateButton =
          ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-edit")(0)
        Simulate.click(updateButton)
        c.state.showAclUpdateModal shouldBe true

        val modal = ReactTestUtils.findRenderedComponentWithType(c, AclModal.component)

        val closeButton =
          ReactTestUtils.findRenderedDOMComponentWithClass(modal, "test-close-acl")
        Simulate.click(closeButton)

        c.state.showAclUpdateModal shouldBe false
        ReactTestUtils.scryRenderedComponentsWithType(c, AclModal.component) shouldBe empty
      }
    }

    "display rules in table" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(AclTable(props)) { c =>
        val table = ReactTestUtils.findRenderedDOMComponentWithTag(c, "table")
        val html = table.outerHtmlScrubbed()
        initialAclList.map { r =>
          r.displayName.map(html should include(_))
          html should include(ACLRule.toAccessLevelDisplay(r.accessLevel))
          r.recordTypes.map(t => html should include(t.toString))
          r.recordMask.map(html should include(_))
          r.description.map(html should include(_))
        }
      }
    }

    "call withConfirmation when clicking delete button" in new Fixture {
      (mockHttp.withConfirmation _).expects(*, *).once().returns(Callback.empty)
      (mockHttp.put[ZoneResponse] _).expects(UpdateZoneRoute(initialZone.id), *, *, *).never()

      ReactTestUtils.withRenderedIntoDocument(AclTable(props)) { c =>
        val deleteButton = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-delete")(0)
        Simulate.click(deleteButton)
      }
    }

    "call http.put Zone after confirming delete acl rule" in new Fixture {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once
        .onCall((_, cb) => cb)

      (mockHttp.put[ZoneResponse] _)
        .expects(
          UpdateZoneRoute(initialZone.id),
          write(initialZone.copy(acl = Rules(initialAclList.patch(0, Nil, 1)))),
          *,
          *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(AclTable(props)) { c =>
        val deleteButton = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-delete")(0)
        Simulate.click(deleteButton)
      }
    }
  }
}
