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

package vinyldns.client.grouplist.components

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router.RouterCtl
import org.scalatest._
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import vinyldns.client.grouplist.SharedTestData
import vinyldns.client.http.{DeleteGroupRoute, Http}
import vinyldns.client.pages.grouplist.components.GroupsTable
import vinyldns.client.models.Notification
import vinyldns.client.models.membership.{Group, GroupList}
import vinyldns.client.routes.AppRouter.Page

import scala.language.existentials

class GroupsTableSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  trait Fixture {
    val mockHttp = mock[Http]
    val mockRouter = mock[RouterCtl[Page]]
  }

  "GroupsTable" should {
    "display loading message when group list is none" in new Fixture {
      val groupsList = None
      val props =
        GroupsTable.Props(
          mockHttp,
          groupsList,
          generateNoOpHandler[Option[Notification]],
          generateNoOpHandler[Unit],
          mockRouter)

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        c.outerHtmlScrubbed() shouldBe "<div><p>Loading your groups...</p></div>"
      }
    }

    "display no groups message if groups list is empty" in new Fixture {
      val groupsList = Some(GroupList(List(), Some(100)))
      val props =
        GroupsTable.Props(
          mockHttp,
          groupsList,
          generateNoOpHandler[Option[Notification]],
          generateNoOpHandler[Unit],
          mockRouter)

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        c.outerHtmlScrubbed() shouldBe "<div><p>You don't have any groups yet</p></div>"
      }
    }

    "display groups in table" in new Fixture {
      val groups = generateGroups(10).toList
      val groupsList = Some(GroupList(groups, Some(100)))

      val props =
        GroupsTable.Props(
          mockHttp,
          groupsList,
          generateNoOpHandler[Option[Notification]],
          generateNoOpHandler[Unit],
          mockRouter)

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        val table = ReactTestUtils.findRenderedDOMComponentWithTag(c, "table")
        val html = table.outerHtmlScrubbed()
        groups.map { group =>
          html should include(s"<td>${group.name}</td>")
          html should include(s"<td>${group.email}</td>")
          html should include(s"<td>${group.description.getOrElse("")}</td>")
        }
      }
    }

    "call withConfirmation when clicking delete button" in new Fixture {
      val groups = generateGroups(1).toList
      val groupsList = Some(GroupList(groups, Some(100)))

      val props =
        GroupsTable.Props(
          mockHttp,
          groupsList,
          generateNoOpHandler[Option[Notification]],
          generateNoOpHandler[Unit],
          mockRouter)

      (mockHttp.withConfirmation _).expects(*, *).once().returns(Callback.empty)
      (mockHttp.delete[Group] _).expects(*, *, *).never()

      ReactTestUtils.withRenderedIntoDocument(GroupsTable(props)) { c =>
        val deleteButton = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-delete")
        Simulate.click(deleteButton)
      }
    }

    "call http.delete when clicking delete button and confirming" in new Fixture {
      val groups = generateGroups(10).toList
      val groupsList = Some(GroupList(groups, Some(100)))

      val props =
        GroupsTable.Props(
          mockHttp,
          groupsList,
          generateNoOpHandler[Option[Notification]],
          generateNoOpHandler[Unit],
          mockRouter)

      (mockHttp.withConfirmation _).expects(*, *).repeat(10 to 10).onCall((_, cb) => cb)

      groups.map { g =>
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
  }
}
