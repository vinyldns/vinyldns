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

package vinyldns.client.grouplist

import org.scalatest._
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import vinyldns.client.http.{Http, HttpResponse, ListGroupsRoute}
import vinyldns.client.models.Id
import vinyldns.client.models.membership.{Group, GroupList}
import vinyldns.client.routes.AppRouter.{Page, ToGroupListPage}
import vinyldns.client.pages.grouplist.GroupListPage
import vinyldns.client.pages.grouplist.components.CreateGroupModal

import scala.language.existentials

class GroupListPageSpec extends WordSpec with Matchers with MockFactory {
  def generateGroups(numGroups: Int): Seq[Group] =
    for {
      i <- 0 until numGroups
      members = Seq(Id("id-i"))
    } yield Group(s"name-$i", s"email-$i@test.com", s"id-$i", s"created-$i", members, members)

  var mockHttp: Http = _
  val mockRouter = MockRouterCtl[Page]()

  val initialGroupList = GroupList(generateGroups(1).toList, Some(100))

  def beforeMount(): Unit = {
    mockHttp = mock[Http]
    (mockHttp.get[GroupList] _)
      .expects(ListGroupsRoute, *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(initialGroupList))
      }
  }

  "GroupListPage" should {
    "get groups when mounting" in {
      beforeMount()

      ReactTestUtils.withRenderedIntoDocument(GroupListPage(ToGroupListPage, mockRouter, mockHttp)) {
        c =>
          c.state.groupsList shouldBe Some(initialGroupList)
      }
    }

    "update groups when hitting refresh button" in {
      beforeMount()
      val updatedGroupsList = GroupList(generateGroups(2).toList, Some(100))

      ReactTestUtils.withRenderedIntoDocument(GroupListPage(ToGroupListPage, mockRouter, mockHttp)) {
        c =>
          c.state.groupsList shouldBe Some(initialGroupList)

          (mockHttp.get[GroupList] _)
            .expects(ListGroupsRoute, *, *)
            .once()
            .onCall { (_, onSuccess, _) =>
              onSuccess.apply(mock[HttpResponse], Some(updatedGroupsList))
            }

          val refreshButton =
            ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-refresh-groups")
          Simulate.click(refreshButton)

          c.state.groupsList shouldBe Some(updatedGroupsList)
      }
    }

    "show create group modal when clicking create group button" in {
      beforeMount()

      ReactTestUtils.withRenderedIntoDocument(GroupListPage(ToGroupListPage, mockRouter, mockHttp)) {
        c =>
          c.state.showCreateGroup shouldBe false
          ReactTestUtils.scryRenderedComponentsWithType(c, CreateGroupModal.component) shouldBe empty

          val createButton =
            ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-create-group")
          Simulate.click(createButton)

          c.state.showCreateGroup shouldBe true
          ReactTestUtils.findRenderedComponentWithType(c, CreateGroupModal.component)
      }
    }

    "close create group modal after clicking close button" in {
      beforeMount()

      ReactTestUtils.withRenderedIntoDocument(GroupListPage(ToGroupListPage, mockRouter, mockHttp)) {
        c =>
          c.modState(_.copy(showCreateGroup = true))
          val modal = ReactTestUtils.findRenderedComponentWithType(c, CreateGroupModal.component)

          val closeButton =
            ReactTestUtils.findRenderedDOMComponentWithClass(modal, "test-close-create-group")
          Simulate.click(closeButton)

          c.state.showCreateGroup shouldBe false
          ReactTestUtils.scryRenderedComponentsWithType(c, CreateGroupModal.component) shouldBe empty
      }
    }
  }
}
