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

package vinyldns.client.pages.grouplist.components

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router.RouterCtl
import org.scalatest._
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import vinyldns.client.SharedTestData
import vinyldns.client.http.{DeleteGroupRoute, Http, HttpResponse, ListGroupsRoute}
import vinyldns.client.models.Notification
import vinyldns.client.models.membership.{Group, GroupList}
import vinyldns.client.routes.AppRouter.Page

import scala.language.existentials

class GroupsTableSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]
  val initialGroupList = GroupList(generateGroups(10).toList, Some(100))

  trait Fixture {
    val mockHttp = mock[Http]

    (mockHttp.get[GroupList] _)
      .expects(ListGroupsRoute(), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(initialGroupList))
      }
  }

  "GroupsTable" should {
    "get groups when mounting" in new Fixture {
      val props =
        GroupsTable.Props(mockHttp, generateNoOpHandler[Option[Notification]], mockRouter)

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        c.state.groupsList shouldBe Some(initialGroupList)
      }
    }

    "update groups when hitting refresh button" in new Fixture {
      val updatedGroupsList = GroupList(generateGroups(2).toList, Some(100))
      val props =
        GroupsTable.Props(mockHttp, generateNoOpHandler[Option[Notification]], mockRouter)

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        c.state.groupsList shouldBe Some(initialGroupList)

        (mockHttp.get[GroupList] _)
          .expects(ListGroupsRoute(), *, *)
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

    "call http.get with groupNameFilter when someone uses search button" in new Fixture {
      val props =
        GroupsTable.Props(mockHttp, generateNoOpHandler[Option[Notification]], mockRouter)

      (mockHttp.get[GroupList] _)
        .expects(ListGroupsRoute(Some("filter")), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        val input = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-groupNameFilter")
        Simulate.change(input, SimEvent.Change("filter"))

        c.state.groupNameFilter shouldBe Some("filter")

        val form = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-search-form")
        Simulate.submit(form)
      }
    }

    "call http.get with groupNameFilter when someone uses refresh button" in new Fixture {
      val props =
        GroupsTable.Props(mockHttp, generateNoOpHandler[Option[Notification]], mockRouter)

      (mockHttp.get[GroupList] _)
        .expects(ListGroupsRoute(Some("filter")), *, *)
        .once()
        .returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        val input = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-groupNameFilter")
        Simulate.change(input, SimEvent.Change("filter"))

        c.state.groupNameFilter shouldBe Some("filter")

        val refresh = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-refresh-groups")
        Simulate.click(refresh)
      }
    }

    "display loading message when group list is none" in {
      val mockHttp = mock[Http]

      (mockHttp.get[GroupList] _)
        .expects(ListGroupsRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      val props =
        GroupsTable.Props(mockHttp, generateNoOpHandler[Option[Notification]], mockRouter)

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        c.outerHtmlScrubbed() shouldBe "<div><p>Loading your groups...</p></div>"
      }
    }

    "display no groups message if groups list is empty" in {
      val mockHttp = mock[Http]

      (mockHttp.get[GroupList] _)
        .expects(ListGroupsRoute(), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(GroupList(List(), Some(100))))
        }

      val props =
        GroupsTable.Props(mockHttp, generateNoOpHandler[Option[Notification]], mockRouter)

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        c.outerHtmlScrubbed() shouldBe "<div><p>You don't have any groups yet</p></div>"
      }
    }

    "display groups in table" in new Fixture {
      val props =
        GroupsTable.Props(mockHttp, generateNoOpHandler[Option[Notification]], mockRouter)

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        val table = ReactTestUtils.findRenderedDOMComponentWithTag(c, "table")
        val html = table.outerHtmlScrubbed()
        initialGroupList.groups.map { group =>
          html should include(s"""<td class="col-md-3">${group.name}</td>""")
          html should include(s"""<td class="col-md-3">${group.email}</td>""")
          html should include(s"""<td class="col-md-3">${group.description.getOrElse("")}</td>""")
        }
      }
    }

    "call withConfirmation when clicking delete button" in new Fixture {
      val props =
        GroupsTable.Props(mockHttp, generateNoOpHandler[Option[Notification]], mockRouter)

      (mockHttp.withConfirmation _).expects(*, *).once().returns(Callback.empty)
      (mockHttp.delete[Group] _).expects(*, *, *).never()

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        val deleteButton = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-delete")(0)
        Simulate.click(deleteButton)
      }
    }

    "call http.delete when clicking delete button and confirming" in new Fixture {
      val props =
        GroupsTable.Props(mockHttp, generateNoOpHandler[Option[Notification]], mockRouter)

      (mockHttp.withConfirmation _).expects(*, *).repeat(10 to 10).onCall((_, cb) => cb)

      initialGroupList.groups.map { g =>
        (mockHttp.delete[Group] _)
          .expects(DeleteGroupRoute(g.id), *, *)
          .once()
          .returns(Callback.empty)
      }

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        val deleteButtons = ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-delete")
        (deleteButtons should have).length(10)
        deleteButtons.foreach(Simulate.click(_))
      }
    }

    "show update group modal when clicking update group button" in new Fixture {
      val props =
        GroupsTable.Props(mockHttp, generateNoOpHandler[Option[Notification]], mockRouter)

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        c.state.showUpdateGroup shouldBe false
        ReactTestUtils.scryRenderedComponentsWithType(c, GroupModal.component) shouldBe empty

        val editButton =
          ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-edit")(0)
        Simulate.click(editButton)

        c.state.showUpdateGroup shouldBe true
        ReactTestUtils.findRenderedComponentWithType(c, GroupModal.component)
      }
    }

    "close update group modal after clicking close button" in new Fixture {
      val props =
        GroupsTable.Props(mockHttp, generateNoOpHandler[Option[Notification]], mockRouter)

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        c.state.showUpdateGroup shouldBe false
        val editButton =
          ReactTestUtils.scryRenderedDOMComponentsWithClass(c, "test-edit")(0)
        Simulate.click(editButton)
        c.state.showUpdateGroup shouldBe true

        val modal = ReactTestUtils.findRenderedComponentWithType(c, GroupModal.component)

        val closeButton =
          ReactTestUtils.findRenderedDOMComponentWithClass(modal, "test-close-create-group")
        Simulate.click(closeButton)

        c.state.showUpdateGroup shouldBe false
        ReactTestUtils.scryRenderedComponentsWithType(c, GroupModal.component) shouldBe empty
      }
    }
  }
}
