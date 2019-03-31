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

package vinyldns.client.pages.credentials

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router.RouterCtl
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import japgolly.scalajs.react.test._
import vinyldns.client.SharedTestData
import vinyldns.client.http.{Http, RegenerateCredentialsRoute}
import vinyldns.client.router.{Page, ToApiCredentialsPage}

import scala.language.existentials

class ApiCredentialsPageSpec extends WordSpec with Matchers with MockFactory with SharedTestData {
  val mockRouter = mock[RouterCtl[Page]]

  trait Fixture {
    val mockHttp = mock[Http]

    (mockHttp.getLoggedInUser _).expects().once().returns(testUser)
  }

  "ApiCredentialsPage" should {
    "have link to download credentials" in new Fixture {
      ReactTestUtils.withRenderedIntoDocument(
        ApiCredentialsPage(ToApiCredentialsPage, mockRouter, mockHttp)) { c =>
        c.outerHtmlScrubbed() should
          include(
            s"""<a href="/download-creds-file/${testUser.userName}-vinyldns-credentials.csv">""")
      }
    }

    "call withConfirmation when clicking regenerate credentials button" in new Fixture {
      (mockHttp.withConfirmation _).expects(*, *).once().returns(Callback.empty)
      (mockHttp.post[Unit] _).expects(*, *, *, *).never()

      ReactTestUtils.withRenderedIntoDocument(
        ApiCredentialsPage(ToApiCredentialsPage, mockRouter, mockHttp)) { c =>
        val button = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-regenerate")
        Simulate.click(button)
      }
    }

    "call http.post when clicking regenerate credentials button and confirming" in new Fixture {
      (mockHttp.withConfirmation _).expects(*, *).once().onCall((_, cb) => cb)
      (mockHttp
        .post[Unit] _).expects(RegenerateCredentialsRoute, "", *, *).once().returns(Callback.empty)

      ReactTestUtils.withRenderedIntoDocument(
        ApiCredentialsPage(ToApiCredentialsPage, mockRouter, mockHttp)) { c =>
        val button = ReactTestUtils.findRenderedDOMComponentWithClass(c, "test-regenerate")
        Simulate.click(button)
      }
    }
  }
}
