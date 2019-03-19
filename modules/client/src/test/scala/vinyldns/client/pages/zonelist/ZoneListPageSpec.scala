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

package vinyldns.client.pages.zonelist

import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import vinyldns.client.SharedTestData
import vinyldns.client.http.{Http, HttpResponse, ListGroupsRoute}
import vinyldns.client.models.membership.GroupList
import vinyldns.client.pages.zonelist.components.ZoneModal
import vinyldns.client.routes.AppRouter.{Page, ToZoneListPage}

import scala.language.existentials

class ZoneListPageSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = MockRouterCtl[Page]()

  trait Fixture {
    val mockHttp = mock[Http]
    val groupList = GroupList(List(), Some(100))

    (mockHttp.get[GroupList] _)
      .expects(ListGroupsRoute(), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(groupList))
      }
  }

  "ZoneListPage" should {
    "show connect to zone modal when clicking connect to zone" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(ZoneListPage(ToZoneListPage, mockRouter, mockHttp)) {
        c =>
          c.state.showCreateZone shouldBe false
          ReactTestUtils.scryRenderedComponentsWithType(c, ZoneModal.component) shouldBe empty

          val createButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-zone")
          Simulate.click(createButton)

          c.state.showCreateZone shouldBe true
          ReactTestUtils.findRenderedComponentWithType(c, ZoneModal.component)
      }
    }

    "should close connect to zone modal when clicking close button" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(ZoneListPage(ToZoneListPage, mockRouter, mockHttp)) {
        c =>
          c.state.showCreateZone shouldBe false
          ReactTestUtils.scryRenderedComponentsWithType(c, ZoneModal.component) shouldBe empty
          val createButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-zone")
          Simulate.click(createButton)

          c.state.showCreateZone shouldBe true
          ReactTestUtils.findRenderedComponentWithType(c, ZoneModal.component)

          val closeButton =
            ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-close-create-zone")
          Simulate.click(closeButton)
          c.state.showCreateZone shouldBe false
          ReactTestUtils.scryRenderedComponentsWithType(c, ZoneModal.component) shouldBe empty
      }
    }
  }
}
