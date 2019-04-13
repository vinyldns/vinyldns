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

package vinyldns.client.pages.zoneview

import japgolly.scalajs.react.extra.router.RouterCtl
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import japgolly.scalajs.react.test._
import vinyldns.client.SharedTestData
import vinyldns.client.http._
import vinyldns.client.models.membership.GroupList
import vinyldns.client.models.record.{RecordSetChangeList, RecordSetList}
import vinyldns.client.models.zone.Zone
import vinyldns.client.pages.zoneview.components.{
  ChangeHistoryTab,
  ManageAccessTab,
  ManageRecordSetsTab,
  ManageZoneTab
}
import vinyldns.client.router._

class ZoneViewPageSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]
  val initialZone = generateZones(1).head
  val initialRecordSets = generateRecordSets(10, initialZone.id)
  val initialRecordSetList = RecordSetList(initialRecordSets.toList, 100)
  val initialGroups = generateGroups(10)
  val initialGroupList = GroupList(initialGroups.toList, 100)

  trait Fixture {
    val mockHttp = mock[Http]

    (mockHttp.get[Zone] _)
      .expects(GetZoneRoute(initialZone.id), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(initialZone))
      }
  }

  "ZoneViewPage" should {
    "get zone, record set changes, records, and groups when mounting records tab" in new Fixture {
      (mockHttp.get[RecordSetChangeList] _)
        .expects(ListRecordSetChangesRoute(initialZone.id, 5), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      (mockHttp.get[RecordSetList] _)
        .expects(ListRecordSetsRoute(initialZone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialRecordSetList))
        }

      (mockHttp.get[GroupList] _)
        .expects(ListGroupsRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialGroupList))
        }

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewRecordsTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe Some(initialZone)
        ReactTestUtils.findRenderedComponentWithType(c, ManageRecordSetsTab.component)
      }
    }

    "get zone and groups when mounting access tab" in new Fixture {
      (mockHttp.get[GroupList] _)
        .expects(ListGroupsRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialGroupList))
        }

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewAccessTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe Some(initialZone)
        ReactTestUtils.findRenderedComponentWithType(c, ManageAccessTab.component)
      }
    }

    "get zone and groups when mounting manage zone tab" in new Fixture {
      (mockHttp.get[GroupList] _)
        .expects(ListGroupsRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialGroupList))
        }

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewZoneTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe Some(initialZone)
        ReactTestUtils.findRenderedComponentWithType(c, ManageZoneTab.component)
      }
    }

    "get zone, record set changes, and groups when mounting changes tab" in new Fixture {
      (mockHttp.get[RecordSetChangeList] _)
        .expects(ListRecordSetChangesRoute(initialZone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      (mockHttp.get[GroupList] _)
        .expects(ListGroupsRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialGroupList))
        }

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewChangesTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe Some(initialZone)
        ReactTestUtils.findRenderedComponentWithType(c, ChangeHistoryTab.component)
      }
    }

    "show loading message if zone is none" in {
      val mockHttp = mock[Http]

      (mockHttp.get[Zone] _)
        .expects(GetZoneRoute(initialZone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      (mockHttp.get[GroupList] _)
        .expects(ListGroupsRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialGroupList))
        }

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewChangesTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe None
        c.outerHtmlScrubbed() should include("Loading...")
      }
    }

    "show loading message if zone is none" in new Fixture {
      (mockHttp.get[GroupList] _)
        .expects(ListGroupsRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewChangesTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe None
        c.outerHtmlScrubbed() should include("Loading...")
      }
    }
  }
}
