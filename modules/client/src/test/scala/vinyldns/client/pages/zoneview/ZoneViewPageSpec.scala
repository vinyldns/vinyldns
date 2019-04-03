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
import vinyldns.client.models.record.{RecordSetChangeList, RecordSetList}
import vinyldns.client.models.zone.Zone
import vinyldns.client.pages.zoneview.components.{ChangeHistoryTab, ManageRecordSetsTab}
import vinyldns.client.router._

class ZoneViewPageSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]
  val initialZone = generateZones(1).head
  val initialRecordSets = generateRecordSets(10, initialZone.id)
  val initialRecordSetList = RecordSetList(initialRecordSets.toList, 100)

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
    "get zone when mounting records tab" in new Fixture {
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

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewRecordsTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe Some(initialZone)
      }
    }

    "get zone when mounting access tab" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewAccessTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe Some(initialZone)
      }
    }

    "get zone when mounting zone edit tab" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewZoneTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe Some(initialZone)
      }
    }

    "get zone when mounting changes tab" in new Fixture {
      (mockHttp.get[RecordSetChangeList] _)
        .expects(ListRecordSetChangesRoute(initialZone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewChangesTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe Some(initialZone)
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

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewChangesTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe None
        c.outerHtmlScrubbed() should include("Loading zone...")
      }
    }

    "show correctly load manage records tab" in new Fixture {
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

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewRecordsTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        ReactTestUtils.findRenderedComponentWithType(c, ManageRecordSetsTab.component)
      }
    }

    "show correctly load change history tab" in new Fixture {
      (mockHttp.get[RecordSetChangeList] _)
        .expects(ListRecordSetChangesRoute(initialZone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewChangesTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        ReactTestUtils.findRenderedComponentWithType(c, ChangeHistoryTab.component)
      }
    }
  }
}
