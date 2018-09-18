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

package controllers

import org.junit.runner.RunWith
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.test.Helpers._
import play.api.test._
@RunWith(classOf[JUnitRunner])
class FrontendControllerSpec extends Specification with Mockito with TestApplicationData {

  "FrontendController" should {
    "send 404 on a bad request" in new WithApplication(app) {
      route(app, FakeRequest(GET, "/boum")) must beSome.which(status(_) == NOT_FOUND)
    }

    "Get for '/'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the index page when the user is logged in" in new WithApplication(app) {
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/")
            .withSession("username" -> username)).get
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain(s"Are you sure you want to log out")
      }
    }

    "Get for '/index'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/index")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the zone page when the user is logged in" in new WithApplication(app) {
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/index")
            .withSession("username" -> username)).get
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Are you sure you want to log out")
        contentAsString(result) must contain("Zones | VinylDNS")
      }
    }

    "Get for '/groups'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/groups")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the groups view page when the user is logged in" in new WithApplication(app) {
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/groups")
            .withSession("username" -> username)).get
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Groups | VinylDNS")
      }
    }

    "Get for '/groups/id'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/groups/some-id")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the groups view page when the user is logged in" in new WithApplication(app) {
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/groups/some-id")
            .withSession("username" -> username)).get
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Group | VinylDNS")
      }
    }

    "Get for '/zones'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/zones")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the zone view page when the user is logged in" in new WithApplication(app) {
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/zones")
            .withSession("username" -> username)).get
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Zones | VinylDNS")
      }
    }

    "Get for '/zones/id'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/zones/some-id")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the groups view page when the user is logged in" in new WithApplication(app) {
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/zones/some-id")
            .withSession("username" -> username)).get
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Zone | VinylDNS")
      }
    }

    "Get for login" should {
      "render the login page when the user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/login")).get
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Username")
        contentAsString(result) must contain("Password")
      }
      "redirect to the index page when the user is logged in" in new WithApplication(app) {
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/login")
            .withSession(("username", username))).get
        status(result) must beEqualTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/index")
      }
    }

    "Get for logout" should {
      "redirect to the login page" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/logout")).get

        status(result) must beEqualTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "clear the session cookie" in new WithApplication(app) {
        // TODO: cookie behavior is radically different in play 2.6, so we cannot look for a Set-Cookie header
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/logout")
            .withSession(("username", username))).get
        headers(result) must contain("Location" -> "/login")
      }
    }

    "Get for '/batchchanges'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/zones")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the batch changes view page when the user is logged in" in new WithApplication(app) {
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/batchchanges")
            .withSession("username" -> username)).get
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Batch Changes | VinylDNS")
      }
    }

    "Get for '/batchchanges/id'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/batchchanges/some-id")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the batch change view page when the user is logged in" in new WithApplication(app) {
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/batchchanges/some-id")
            .withSession("username" -> username)).get
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("Batch Change | VinylDNS")
      }
    }

    "Get for '/batchchanges/new'" should {
      "redirect to the login page when a user is not logged in" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/batchchanges/new")).get
        status(result) must equalTo(SEE_OTHER)
        headers(result) must contain("Location" -> "/login")
      }
      "render the batch change view page when the user is logged in" in new WithApplication(app) {
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/batchchanges/new")
            .withSession("username" -> username)).get
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("New Batch Change | VinylDNS")
      }
    }

    "CustomLinks" should {
      "be displayed on login screen if login screen flag is true" in new WithApplication(app) {
        val result = route(app, FakeRequest(GET, "/login")).get
        contentType(result) must beSome.which(_ == "text/html")
        status(result) must equalTo(OK)
        contentAsString(result) must contain("test link login")
        contentAsString(result) must not(contain("test link sidebar"))
      }

      "be displayed on the logged-in view if sidebar flag is true" in new WithApplication(app) {
        val username = "LoggedInUser"
        val result = route(
          app,
          FakeRequest(GET, "/zones")
            .withSession("username" -> username)).get
        status(result) must beEqualTo(OK)
        contentType(result) must beSome.which(_ == "text/html")
        contentAsString(result) must contain("test link sidebar")
        contentAsString(result) must not(contain("test link login"))
      }
    }
  }
}
