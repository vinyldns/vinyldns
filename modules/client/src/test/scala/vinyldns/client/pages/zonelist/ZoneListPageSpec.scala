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

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import vinyldns.client.SharedTestData
import vinyldns.client.http.{Http, HttpResponse, ListGroupsRoute, ListZonesRoute}
import vinyldns.client.models.membership.GroupList
import vinyldns.client.models.zone.ZoneList
import vinyldns.client.pages.zonelist.components.ZoneModal
import vinyldns.client.router.{Page, ToZoneListPage}

import scala.language.existentials

class ZoneListPageSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = MockRouterCtl[Page]()

  trait Fixture {
    val mockHttp = mock[Http]
    val groupList = GroupList(List(), 100)
    val zoneList = ZoneList(List(), 100)

    (mockHttp.get[GroupList] _)
      .expects(ListGroupsRoute(), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(groupList))
      }

    (mockHttp.get[ZoneList] _)
      .expects(ListZonesRoute(), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(zoneList))
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

    "call http.get with nameFilter when someone uses search bar" in new Fixture {
      (mockHttp.get[ZoneList] _)
        .expects(ListZonesRoute(nameFilter = Some("filter")), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(ZoneListPage(ToZoneListPage, mockRouter, mockHttp)) {
        c =>
          val input = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-nameFilter")
          Simulate.change(input, SimEvent.Change("filter"))

          val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-search-form")
          Simulate.submit(form)
      }
    }

    "reset pagination info when using search button" in {
      val mockHttp = mock[Http]
      val groupList = GroupList(List(), 100)
      val zoneList = ZoneList(generateZones(1).toList, 100)
      val zoneListWithNext = zoneList.copy(nextId = Some("next"))

      (mockHttp.get[GroupList] _)
        .expects(ListGroupsRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(groupList))
        }

      (mockHttp.get[ZoneList] _)
        .expects(ListZonesRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(zoneListWithNext))
        }

      ReactTestUtils.withRenderedIntoDocument(ZoneListPage(ToZoneListPage, mockRouter, mockHttp)) {
        c =>
          (mockHttp.get[ZoneList] _)
            .expects(ListZonesRoute(startFrom = Some("next")), *, *)
            .once()
            .onCall { (_, onSuccess, _) =>
              onSuccess.apply(mock[HttpResponse], Some(zoneListWithNext))
            }

          val next = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-next-page")
          next.outerHtmlScrubbed() should include("Page 2")
          Simulate.click(next)

          next.outerHtmlScrubbed() should include("Page 3")

          (mockHttp.get[ZoneList] _)
            .expects(ListZonesRoute(), *, *)
            .once()
            .onCall { (_, onSuccess, _) =>
              onSuccess.apply(mock[HttpResponse], Some(zoneListWithNext))
            }

          val search = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-search-form")
          Simulate.submit(search)

          next.outerHtmlScrubbed() should include("Page 2")
      }
    }

    "call http.get with nameFilter when someone uses refresh button" in new Fixture {
      (mockHttp.get[ZoneList] _)
        .expects(ListZonesRoute(nameFilter = Some("filter")), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(ZoneListPage(ToZoneListPage, mockRouter, mockHttp)) {
        c =>
          val input = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-nameFilter")
          Simulate.change(input, SimEvent.Change("filter"))

          val refreshButton =
            ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-refresh-zones")
          Simulate.click(refreshButton)
      }
    }

    "reset pagination info when using refresh button" in {
      val mockHttp = mock[Http]
      val groupList = GroupList(List(), 100)
      val zoneList = ZoneList(generateZones(1).toList, 100)
      val zoneListWithNext = zoneList.copy(nextId = Some("next"))

      (mockHttp.get[GroupList] _)
        .expects(ListGroupsRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(groupList))
        }

      (mockHttp.get[ZoneList] _)
        .expects(ListZonesRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(zoneListWithNext))
        }

      ReactTestUtils.withRenderedIntoDocument(ZoneListPage(ToZoneListPage, mockRouter, mockHttp)) {
        c =>
          (mockHttp.get[ZoneList] _)
            .expects(ListZonesRoute(startFrom = Some("next")), *, *)
            .once()
            .onCall { (_, onSuccess, _) =>
              onSuccess.apply(mock[HttpResponse], Some(zoneListWithNext))
            }

          val next = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-next-page")
          next.outerHtmlScrubbed() should include("Page 2")
          Simulate.click(next)

          next.outerHtmlScrubbed() should include("Page 3")

          (mockHttp.get[ZoneList] _)
            .expects(ListZonesRoute(), *, *)
            .once()
            .onCall { (_, onSuccess, _) =>
              onSuccess.apply(mock[HttpResponse], Some(zoneListWithNext))
            }

          val refreshButton =
            ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-refresh-zones")
          Simulate.click(refreshButton)

          next.outerHtmlScrubbed() should include("Page 2")
      }
    }
  }
}
