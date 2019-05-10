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
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import japgolly.scalajs.react.test._
import vinyldns.client.SharedTestData
import vinyldns.client.http._
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.client.pages.zone.list.components.ZoneModal
import vinyldns.client.router.Page
import vinyldns.client.components.JsNative.toReadableTimestamp

import scala.language.existentials

class ManageZoneTabSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]
  val initialGroups = generateGroupResponses(1)
  val initialGroupList = GroupListResponse(initialGroups.toList, 100)
  val initialZone = generateZoneResponses(1, initialGroups(0)).head
  val backendIds = List("backend-id")

  trait Fixture {
    val mockHttp = mock[Http]

    val props = ManageZoneTab.Props(
      initialZone,
      initialGroupList,
      backendIds,
      mockHttp,
      mockRouter,
      generateNoOpHandler[Unit],
      true
    )
  }

  "ManageZoneTab" should {
    "should render Zone details" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(ManageZoneTab(props)) { c =>
        val html = c.outerHtmlScrubbed()
        html should include(s"<td>${initialZone.email}</td>")
        html should include("<td>Private</td>")
        html should include(s"<td>${toReadableTimestamp(initialZone.created)}</td>")
        html should include(s"<td>${toReadableTimestamp(initialZone.updated)}</td>")
        html should include(s"<td>${toReadableTimestamp(initialZone.latestSync)}</td>")
        html should include("<td>backend-id</td>")
        html should include("<td>default</td>")
      }
    }

    "show update zone modal when clicking update button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(ManageZoneTab(props)) { c =>
        c.state.showUpdateZone shouldBe false

        ReactTestUtils.scryRenderedComponentsWithType(c, ZoneModal.component) shouldBe empty

        val updateButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-update")
        Simulate.click(updateButton)

        c.state.showUpdateZone shouldBe true

        ReactTestUtils.findRenderedComponentWithType(c, ZoneModal.component)
      }
    }

    "close update zone modal when clicking close button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(ManageZoneTab(props)) { c =>
        c.state.showUpdateZone shouldBe false

        ReactTestUtils.scryRenderedComponentsWithType(c, ZoneModal.component) shouldBe empty

        val updateButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-update")
        Simulate.click(updateButton)

        c.state.showUpdateZone shouldBe true

        ReactTestUtils.findRenderedComponentWithType(c, ZoneModal.component)

        val closeButton =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-close-create-zone")
        Simulate.click(closeButton)

        c.state.showUpdateZone shouldBe false
        ReactTestUtils.scryRenderedComponentsWithType(c, ZoneModal.component) shouldBe empty
      }
    }
  }
}
