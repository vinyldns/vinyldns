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

package vinyldns.client

import org.scalatest._
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import vinyldns.client.http.{Http, HttpResponse, ListGroupsRoute}
import vinyldns.client.models.membership.{Group, GroupList}
import vinyldns.client.routes.AppRouter.{Page, ToGroupListPage}
import vinyldns.client.pages.grouplist.GroupListPage

class ReactAppSpec extends WordSpec with Matchers with MockFactory {
  "test" should {
    "do something" in {
      val groupList =
        GroupList(List(Group("targetnameplease", "email", id = Some("id"))), Some(100))
      val router = MockRouterCtl[Page]()
      val http = mock[Http]

      (http.get[GroupList] _)
        .expects(ListGroupsRoute, *, *)
        .once
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(groupList))
        }

      ReactTestUtils.withRenderedIntoDocument(GroupListPage(ToGroupListPage, router, http)) { c =>
        c.outerHtmlScrubbed() should include("targetnameplease")
      }
    }
  }
}
