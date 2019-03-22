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

package vinyldns.client.pages.zonelist.components

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router.RouterCtl
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import japgolly.scalajs.react.test._
import vinyldns.client.SharedTestData
import vinyldns.client.http.{DeleteZoneRoute, Http, HttpResponse, ListZonesRoute}
import vinyldns.client.models.zone.{Zone, ZoneList}
import vinyldns.client.routes.AppRouter.Page

import scala.language.existentials

class ZonesTableSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]
  val initialZoneList = ZoneList(generateZones(10).toList, 100)

  trait Fixture {
    val mockHttp = mock[Http]

    (mockHttp.get[ZoneList] _)
      .expects(ListZonesRoute(), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(initialZoneList))
      }
  }

  "ZonesTable" should {
    "get zones when mounting" in new Fixture {
      val props = ZonesTable.Props(mockHttp, mockRouter)

      ReactTestUtils.withRenderedIntoDocument(ZonesTable(props)) { c =>
        c.state.zonesList shouldBe Some(initialZoneList)
      }
    }

    "display loading message when zone list is none" in {
      val mockHttp = mock[Http]

      (mockHttp.get[ZoneList] _)
        .expects(ListZonesRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      val props = ZonesTable.Props(mockHttp, mockRouter)

      ReactTestUtils.withRenderedIntoDocument(ZonesTable(props)) { c =>
        c.outerHtmlScrubbed() shouldBe "<div><p>Loading your zones...</p></div>"
      }
    }

    "display no zones message when zone list is empty" in {
      val mockHttp = mock[Http]

      (mockHttp.get[ZoneList] _)
        .expects(ListZonesRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(ZoneList(List(), 100)))
        }

      val props = ZonesTable.Props(mockHttp, mockRouter)

      ReactTestUtils.withRenderedIntoDocument(ZonesTable(props)) { c =>
        c.outerHtmlScrubbed() shouldBe "<div><p>You don't have any zones yet</p></div>"
      }
    }

    "display zones in table" in new Fixture {
      val props = ZonesTable.Props(mockHttp, mockRouter)

      ReactTestUtils.withRenderedIntoDocument(ZonesTable(props)) { c =>
        val table = ReactTestUtils.findRenderedDOMComponentWithTag(c, "table")
        val html = table.outerHtmlScrubbed()

        initialZoneList.zones.map { zone =>
          html should include(s"""<td>${zone.name}</td>""")
          html should include(s"""<td>${zone.email}</td>""")
          html should include(s"""${zone.adminGroupName}""")
          html should include("<td>Private</td>")
        }
      }
    }

    "call withConfirmation when clicking abandon button" in new Fixture {
      val props = ZonesTable.Props(mockHttp, mockRouter)

      (mockHttp.withConfirmation _).expects(*, *).once().returns(Callback.empty)
      (mockHttp.delete[Zone] _).expects(*, *, *).never()

      ReactTestUtils.withRenderedIntoDocument(ZonesTable(props)) { c =>
        val delete = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-abandon")(0)
        Simulate.click(delete)
      }
    }

    "call http.delete when clicking delete button and confirming" in new Fixture {
      val props = ZonesTable.Props(mockHttp, mockRouter)

      (mockHttp.withConfirmation _).expects(*, *).repeat(10 to 10).onCall((_, cb) => cb)

      initialZoneList.zones.map { z =>
        (mockHttp
          .delete[Zone] _).expects(DeleteZoneRoute(z.id), *, *).once().returns(Callback.empty)
      }

      ReactTestUtils.withRenderedIntoDocument(ZonesTable(props)) { c =>
        val deleteButtons = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-abandon")
        (deleteButtons should have).length(10)
        deleteButtons.foreach(Simulate.click(_))
      }
    }
  }
}
