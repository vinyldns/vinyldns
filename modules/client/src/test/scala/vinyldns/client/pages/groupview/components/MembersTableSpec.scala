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

package vinyldns.client.pages.groupview.components

import japgolly.scalajs.react.Callback
import org.scalatest._
import japgolly.scalajs.react.test._
import org.scalamock.scalatest.MockFactory
import vinyldns.client.SharedTestData
import vinyldns.client.http.{GetGroupMembersRoute, Http, HttpResponse, UpdateGroupRoute}
import vinyldns.client.models.membership.{Group, Id, MemberList}
import upickle.default.write
import vinyldns.client.routes.Page

import scala.language.existentials

class MembersTableSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val initialMemberList = MemberList(List(testUser, dummyUser), None, None, 100)
  val initialGroup = generateGroups(1, initialMemberList.members).head

  val emptyMembersList = MemberList(List(), None, None, 100)
  val mockRouter = MockRouterCtl[Page]()

  class Fixture(members: MemberList = initialMemberList) {
    val noOpRefreshGroup = generateNoOpHandler[Unit]
    val mockHttp = mock[Http]

    (mockHttp.getLoggedInUser _)
      .expects()
      .anyNumberOfTimes()
      .returns(testUser)

    (mockHttp.get[MemberList] _)
      .expects(GetGroupMembersRoute(initialGroup.id), *, *)
      .once()
      .onCall { (_, onSuccess, _) =>
        onSuccess.apply(mock[HttpResponse], Some(members))
      }
  }

  "MembersTable" should {
    "get members when mounting" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        c.state.memberList shouldBe Some(initialMemberList)
      }
    }

    "display no members found message if list is empty" in new Fixture(emptyMembersList) {
      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        c.state.memberList shouldBe Some(emptyMembersList)
        c.outerHtmlScrubbed() shouldBe "<p>No group members found</p>"
      }
    }

    "display loading members message if membersList is none" in {
      val noOpRefreshGroup = generateNoOpHandler[Unit]
      val mockHttp = mock[Http]

      (mockHttp.get[MemberList] _)
        .expects(GetGroupMembersRoute(initialGroup.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], None)
        }

      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        c.state.memberList shouldBe None
        c.outerHtmlScrubbed() shouldBe "<p>Loading group members...</p>"
      }
    }

    "display group members in table" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        val rows = ReactTestUtils.scryRenderedDOMComponentsWithTag(c, "tr")

        (rows should have).length(3)
        // first is headers
        val bodyRows = rows.drop(1)

        // dont know if this pattern should be followed, could have also made a multiline string with the expected html
        initialMemberList.members.zip(bodyRows).map { z =>
          val stripStart = z._2.outerHtmlScrubbed().stripPrefix("<tr>")
          val stripEnd = stripStart.stripSuffix("</tr>")
          val tds = stripEnd.split("</td>").toList
          (tds should have).length(5)
          tds(0) shouldBe s"<td>${z._1.userName}"
          tds(1) shouldBe s"<td>${z._1.lastName.get}, ${z._1.firstName.get}"
          tds(2) shouldBe s"<td>${z._1.email.get}"
          tds(3) should include(s"Toggle manager status for ${z._1.userName}")
          tds(4) should include(s"Remove ${z._1.userName} from group")
        }
      }
    }
  }

  "MembersTable delete button" should {
    "be disabled if logged in user is not in admin group" in {
      val noOpRefreshGroup = generateNoOpHandler[Unit]
      val mockHttp = mock[Http]

      (mockHttp.getLoggedInUser _)
        .expects()
        .anyNumberOfTimes()
        .returns(dummyUser)

      (mockHttp.get[MemberList] _)
        .expects(GetGroupMembersRoute(initialGroup.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialMemberList))
        }

      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        c.props.group.admins shouldNot contain(Id(mockHttp.getLoggedInUser().id))

        initialMemberList.members.map { m =>
          val deleteButton =
            ReactTestUtils.findRenderedDOMComponentWithClass(c, s"test-delete-${m.userName}")
          deleteButton.outerHtmlScrubbed() should include("disabled")
        }
      }
    }

    "not be disabled if logged in user is not in admin group but is super" in {
      val noOpRefreshGroup = generateNoOpHandler[Unit]
      val mockHttp = mock[Http]

      (mockHttp.getLoggedInUser _)
        .expects()
        .anyNumberOfTimes()
        .returns(dummyUser.copy(isSuper = true))

      (mockHttp.get[MemberList] _)
        .expects(GetGroupMembersRoute(initialGroup.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialMemberList))
        }

      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        c.props.group.admins shouldNot contain(Id(mockHttp.getLoggedInUser().id))

        initialMemberList.members.map { m =>
          val deleteButton =
            ReactTestUtils.findRenderedDOMComponentWithClass(c, s"test-delete-${m.userName}")
          deleteButton.outerHtmlScrubbed() shouldNot include("disabled")
        }
      }
    }

    "not be disabled if logged in user is in admin group" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        c.props.group.admins should contain(Id(mockHttp.getLoggedInUser().id))

        initialMemberList.members.map { m =>
          val deleteButton =
            ReactTestUtils.findRenderedDOMComponentWithClass(c, s"test-delete-${m.userName}")
          deleteButton.outerHtmlScrubbed() shouldNot include("disabled")
        }
      }
    }

    "call withConfirmation when deleting a member" in new Fixture {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .returns(Callback.empty)
      (mockHttp.put[Group] _).expects(*, *, *, *).never()

      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        val deleteButton =
          ReactTestUtils.findRenderedDOMComponentWithClass(c, s"test-delete-${testUser.userName}")
        Simulate.click(deleteButton)
      }
    }

    "update group properly when deleting a member" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        initialMemberList.members.foreach { m =>
          val expectedMembers = initialGroup.members.filter(_.id != m.id)
          val expectedAdmins = initialGroup.admins.filter(_.id != m.id)
          val expectedGroup = initialGroup.copy(members = expectedMembers, admins = expectedAdmins)

          (mockHttp.withConfirmation _)
            .expects(*, *)
            .once()
            .onCall((_, cb) => cb)

          (mockHttp.put[Group] _)
            .expects(UpdateGroupRoute(initialGroup.id), write(expectedGroup), *, *)
            .returns(Callback.empty)

          val deleteButton =
            ReactTestUtils.findRenderedDOMComponentWithClass(c, s"test-delete-${m.userName}")
          Simulate.click(deleteButton)
        }
      }
    }
  }

  "MembersTable group manager widget" should {
    "be disabled if logged in user is not in admin group" in {
      val noOpRefreshGroup = generateNoOpHandler[Unit]
      val mockHttp = mock[Http]

      (mockHttp.getLoggedInUser _)
        .expects()
        .anyNumberOfTimes()
        .returns(dummyUser)

      (mockHttp.get[MemberList] _)
        .expects(GetGroupMembersRoute(initialGroup.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialMemberList))
        }

      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        c.props.group.admins shouldNot contain(Id(mockHttp.getLoggedInUser().id))

        initialMemberList.members.map { m =>
          val widget =
            ReactTestUtils.findRenderedDOMComponentWithClass(
              c,
              s"test-manager-widget-${m.userName}")
          widget.outerHtmlScrubbed() should include("disabled")
        }
      }
    }

    "not be disabled if logged in user is not in admin group but is super" in {
      val noOpRefreshGroup = generateNoOpHandler[Unit]
      val mockHttp = mock[Http]

      (mockHttp.getLoggedInUser _)
        .expects()
        .anyNumberOfTimes()
        .returns(dummyUser.copy(isSuper = true))

      (mockHttp.get[MemberList] _)
        .expects(GetGroupMembersRoute(initialGroup.id), *, *)
        .once()
        .onCall { (_, onSuccess, _) =>
          onSuccess.apply(mock[HttpResponse], Some(initialMemberList))
        }

      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        c.props.group.admins shouldNot contain(Id(mockHttp.getLoggedInUser().id))

        initialMemberList.members.map { m =>
          val widget =
            ReactTestUtils.findRenderedDOMComponentWithClass(
              c,
              s"test-manager-widget-${m.userName}")
          widget.outerHtmlScrubbed() shouldNot include("disabled")
        }
      }
    }

    "not be disabled if logged in user is in admin group" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        c.props.group.admins should contain(Id(mockHttp.getLoggedInUser().id))

        initialMemberList.members.map { m =>
          val widget =
            ReactTestUtils.findRenderedDOMComponentWithClass(
              c,
              s"test-manager-widget-${m.userName}")
          widget.outerHtmlScrubbed() shouldNot include("disabled")
        }
      }
    }

    "call withConfirmation when toggling group manager status" in new Fixture {
      (mockHttp.withConfirmation _)
        .expects(*, *)
        .once()
        .returns(Callback.empty)
      (mockHttp.put[Group] _).expects(*, *, *, *).never()

      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        val widget =
          ReactTestUtils.findRenderedDOMComponentWithClass(
            c,
            s"test-manager-widget-${testUser.userName}")
        Simulate.change(widget, SimEvent.Change())
      }
    }

    "update group properly when toggling admin status" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        MembersTable(MembersTable.Props(initialGroup, mockHttp, noOpRefreshGroup))) { c =>
        initialMemberList.members.foreach { m =>
          val isAdmin = initialGroup.admins.contains(Id(m.id))
          val expectedAdmins =
            if (isAdmin) initialGroup.admins.filter(_.id != m.id)
            else initialGroup.admins ++ Seq(Id(m.id))

          val expectedGroup = initialGroup.copy(admins = expectedAdmins)

          (mockHttp.withConfirmation _)
            .expects(*, *)
            .once()
            .onCall((_, cb) => cb)

          (mockHttp.put[Group] _)
            .expects(UpdateGroupRoute(initialGroup.id), write(expectedGroup), *, *)
            .returns(Callback.empty)

          val widget =
            ReactTestUtils.findRenderedDOMComponentWithClass(
              c,
              s"test-manager-widget-${m.userName}")
          Simulate.change(widget, SimEvent.Change())
        }
      }
    }
  }
}
