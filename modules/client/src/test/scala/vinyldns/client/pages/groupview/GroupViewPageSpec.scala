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

package vinyldns.client.pages.groupview

import org.scalatest._
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import vinyldns.client.SharedTestData
import vinyldns.client.http.{GetGroupMembersRoute, GetGroupRoute, Http, HttpResponse}
import vinyldns.client.models.membership.{Group, MemberList}
import vinyldns.client.routes.AppRouter.{Page, ToGroupViewPage}
import vinyldns.client.pages.groupview.components.{MembersTable, NewMemberForm}

class GroupViewPageSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val initialGroup = generateGroups(1).head
  val initialMemberList = MemberList(List(testUser), None, None, 100)
  val mockRouter = MockRouterCtl[Page]()

  class Fixture(group: Group = initialGroup) {
    val mockHttp = mock[Http]

    (mockHttp.get[Group] _)
      .expects(GetGroupRoute(group.id), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(group))
      }

    (mockHttp.getLoggedInUser _)
      .expects()
      .anyNumberOfTimes()
      .returns(testUser)

    (mockHttp.get[MemberList] _)
      .expects(GetGroupMembersRoute(group.id), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(initialMemberList))
      }
  }

  "GroupViewPage" should {
    "get group when mounting" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        GroupViewPage(ToGroupViewPage(initialGroup.id), mockRouter, mockHttp)) { c =>
        c.state.group shouldBe Some(initialGroup)
      }
    }

    "include MembersTable component" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        GroupViewPage(ToGroupViewPage(initialGroup.id), mockRouter, mockHttp)) { c =>
        c.outerHtmlScrubbed() should include("table")
        ReactTestUtils.findRenderedComponentWithType(c, MembersTable.component)
      }
    }

    "include NewMemberForm component" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        GroupViewPage(ToGroupViewPage(initialGroup.id), mockRouter, mockHttp)) { c =>
        c.outerHtmlScrubbed() should include("Add Member")
        ReactTestUtils.findRenderedComponentWithType(c, NewMemberForm.component)
      }
    }

    "display loading message when group is None" in {
      val mockHttp = mock[Http]

      (mockHttp.get[Group] _)
        .expects(GetGroupRoute(initialGroup.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      ReactTestUtils.withRenderedIntoDocument(
        GroupViewPage(ToGroupViewPage(initialGroup.id), mockRouter, mockHttp)) { c =>
        c.state.group shouldBe None
        val html = c.outerHtmlScrubbed()
        html should include("Loading group...")
        html shouldNot include("page-title")
        html shouldNot include("table")
      }
    }

    "display group header without description" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        GroupViewPage(ToGroupViewPage(initialGroup.id), mockRouter, mockHttp)) { c =>
        val html = c.outerHtmlScrubbed()
        html should include(s"Group ${initialGroup.name}")
        html should include(s"<h5>Id: ${initialGroup.id}</h5>")
        html should include(s"<h5>Email: ${initialGroup.email}</h5>")
        html shouldNot include("Description")
      }
    }

    "display group header with description" in
      new Fixture(initialGroup.copy(description = Some("awesome group"))) {
        ReactTestUtils.withRenderedIntoDocument(
          GroupViewPage(ToGroupViewPage(initialGroup.id), mockRouter, mockHttp)) { c =>
          val html = c.outerHtmlScrubbed()
          html should include(s"Group ${initialGroup.name}")
          html should include(s"<h5>Id: ${initialGroup.id}</h5>")
          html should include(s"<h5>Email: ${initialGroup.email}</h5>")
          html should include("<h5>Description: awesome group</h5>")
        }
      }
  }
}
