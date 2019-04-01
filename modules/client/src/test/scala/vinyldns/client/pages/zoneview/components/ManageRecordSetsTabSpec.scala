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

package vinyldns.client.pages.zoneview.components

import japgolly.scalajs.react.extra.router.RouterCtl
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import japgolly.scalajs.react.test._
import vinyldns.client.SharedTestData
import vinyldns.client.http._
import vinyldns.client.models.record.RecordSetList
import vinyldns.client.router.Page

class ManageRecordSetsTabSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]
  val initialZone = generateZones(1).head
  val initialRecordSets = generateRecordSets(10, initialZone.id)
  val initialRecordSetList = RecordSetList(initialRecordSets.toList, 100)

  trait Fixture {
    val mockHttp = mock[Http]

    (mockHttp.get[RecordSetList] _)
      .expects(ListRecordSetsRoute(initialZone.id), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(initialRecordSetList))
      }
  }

  "ManageRecordSetsTab" should {
    "render the record set table" in new Fixture {
      val props = ManageRecordSetsTab.Props(initialZone, mockHttp, mockRouter)
      ReactTestUtils.withRenderedIntoDocument(ManageRecordSetsTab(props)) { c =>
        ReactTestUtils.findRenderedComponentWithType(c, RecordSetTable.component)
      }
    }
  }
}
