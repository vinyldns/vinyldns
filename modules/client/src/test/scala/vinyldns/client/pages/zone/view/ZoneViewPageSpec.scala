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

package vinyldns.client.pages.zone.view

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router.RouterCtl
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import japgolly.scalajs.react.test._
import vinyldns.client.SharedTestData
import vinyldns.client.http._
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.client.models.record.{RecordSetChangeListResponse, RecordSetListResponse}
import vinyldns.client.models.zone.ZoneResponse
import vinyldns.client.pages.zone.view.components.{
  ChangeHistoryTab,
  ManageAccessTab,
  ManageRecordSetsTab,
  ManageZoneTab
}
import vinyldns.client.router._

import scala.language.existentials

class ZoneViewPageSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]
  val initialGroups = generateGroupResponses(10)
  val initialGroupList = GroupListResponse(initialGroups.toList, 100)
  val initialZone = generateZoneResponses(1, initialGroups(0)).head
  val initialRecordSets = generateRecordSetResponses(10, initialZone.id)
  val initialRecordSetList = RecordSetListResponse(initialRecordSets.toList, 100)
  val backendIds = List("backend-id")

  trait Fixture {
    val mockHttp = mock[Http]

    (mockHttp.get[ZoneResponse] _)
      .expects(GetZoneRoute(initialZone.id), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(initialZone))
      }

    (mockHttp.get[GroupListResponse] _)
      .expects(ListGroupsRoute(), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(initialGroupList))
      }

    (mockHttp.get[List[String]] _)
      .expects(GetBackendIdsRoute, *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(backendIds))
      }
  }

  "ZoneViewPage" should {
    "get zone, record set changes, records, groups, and backendids when mounting records tab" in new Fixture {
      (mockHttp.get[RecordSetChangeListResponse] _)
        .expects(ListRecordSetChangesRoute(initialZone.id, 5), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      (mockHttp.get[RecordSetListResponse] _)
        .expects(ListRecordSetsRoute(initialZone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialRecordSetList))
        }

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewRecordsTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe Some(initialZone)
        c.state.groupList shouldBe Some(initialGroupList)
        c.state.backendIds shouldBe Some(backendIds)
        ReactTestUtils.findRenderedComponentWithType(c, ManageRecordSetsTab.component)
      }
    }

    "get zone, groups, and backendids when mounting access tab" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewAccessTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe Some(initialZone)
        c.state.groupList shouldBe Some(initialGroupList)
        c.state.backendIds shouldBe Some(backendIds)
        ReactTestUtils.findRenderedComponentWithType(c, ManageAccessTab.component)
      }
    }

    "get zone, groups, and backendids when mounting manage zone tab" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewZoneTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe Some(initialZone)
        c.state.groupList shouldBe Some(initialGroupList)
        c.state.backendIds shouldBe Some(backendIds)
        ReactTestUtils.findRenderedComponentWithType(c, ManageZoneTab.component)
      }
    }

    "get zone, record set changes, groups, and backendids when mounting changes tab" in new Fixture {
      (mockHttp.get[RecordSetChangeListResponse] _)
        .expects(ListRecordSetChangesRoute(initialZone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewChangesTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe Some(initialZone)
        c.state.groupList shouldBe Some(initialGroupList)
        c.state.backendIds shouldBe Some(backendIds)
        ReactTestUtils.findRenderedComponentWithType(c, ChangeHistoryTab.component)
      }
    }

    "show loading message if zone is none" in {
      val mockHttp = mock[Http]

      (mockHttp.get[ZoneResponse] _)
        .expects(GetZoneRoute(initialZone.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      (mockHttp.get[GroupListResponse] _)
        .expects(ListGroupsRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialGroupList))
        }

      (mockHttp.get[List[String]] _)
        .expects(GetBackendIdsRoute, *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(backendIds))
        }

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewChangesTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        c.state.zone shouldBe None
        c.outerHtmlScrubbed() should include("Loading...")
      }
    }

    "Http.post a Zone Sync when clicking sync button and confirming" in new Fixture {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .onCall((_, cb) => cb)

      (mockHttp.post[ZoneResponse] _)
        .expects(SyncZoneRoute(initialZone.id), *, *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        ZoneViewPage(ToZoneViewZoneTab(initialZone.id), mockRouter, mockHttp)
      ) { c =>
        val syncButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-sync")
        Simulate.click(syncButton)
      }
    }
  }
}
