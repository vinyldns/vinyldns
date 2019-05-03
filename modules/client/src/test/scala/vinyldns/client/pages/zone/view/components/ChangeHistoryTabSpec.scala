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
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import vinyldns.client.SharedTestData
import vinyldns.client.http._
import vinyldns.client.models.membership.GroupListResponse
import vinyldns.client.models.record.RecordSetChangeListResponse
import vinyldns.client.router.Page

class ChangeHistoryTabSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]
  val initialZone = generateZoneResponses(1).head
  val initialRecordSetChanges = generateRecordSetChangeResponses(10, initialZone)
  val initialRecordSetChangeList =
    RecordSetChangeListResponse(initialZone.id, initialRecordSetChanges.toList, 100)
  val initialGroups = generateGroupResponses(10)
  val initialGroupList = GroupListResponse(initialGroups.toList, 100)

  trait Fixture {
    val mockHttp = mock[Http]

    (mockHttp.get[RecordSetChangeListResponse] _)
      .expects(ListRecordSetChangesRoute(initialZone.id), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(initialRecordSetChangeList))
      }
  }

  "ChangeHistoryTab" should {
    "render the record set change table" in new Fixture {
      val props = ChangeHistoryTab.Props(initialZone, initialGroupList, mockHttp, mockRouter)
      ReactTestUtils.withRenderedIntoDocument(ChangeHistoryTab(props)) { c =>
        ReactTestUtils.findRenderedComponentWithType(c, RecordSetChangeTable.component)
      }
    }
  }
}
