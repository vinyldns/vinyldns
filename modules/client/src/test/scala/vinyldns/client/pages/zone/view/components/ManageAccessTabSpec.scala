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
import vinyldns.client.router.Page

class ManageAccessTabSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]
  val initialGroups = generateGroupResponses(1)
  val initialGroupList = GroupListResponse(initialGroups.toList, 100)
  val initialZone = generateZoneResponses(1, initialGroups(0)).head

  trait Fixture {
    val mockHttp = mock[Http]

    (mockHttp.getLoggedInUser _)
      .expects()
      .anyNumberOfTimes()
      .returns(testUser)

    val props = ManageAccessTab.Props(
      initialZone,
      initialGroupList,
      mockHttp,
      mockRouter,
      generateNoOpHandler[Unit]
    )
  }

  "ManageAccessTab" should {
    "should not show SHARED message if zone is not shared" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(ManageAccessTab(props)) { c =>
        c.props.zone.shared shouldBe false
        c.outerHtmlScrubbed() shouldNot include("SHARED")
      }
    }

    "should show SHARED message if zone is shared" in new Fixture {
      val propsWithShared = props.copy(zone = props.zone.copy(shared = true))

      ReactTestUtils.withRenderedIntoDocument(ManageAccessTab(propsWithShared)) { c =>
        c.props.zone.shared shouldBe true
        c.outerHtmlScrubbed() should include("SHARED")
      }
    }

    "should render AclTable" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(ManageAccessTab(props)) { c =>
        ReactTestUtils.findRenderedComponentWithType(c, AclTable.component)
      }
    }
  }
}
