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
import vinyldns.client.pages.home.HomePage
import vinyldns.client.routes.AppRouter.{Page, ToHomePage}

class ReactAppSpec extends WordSpec with Matchers {
  "test" should {
    "do something" in {
      val displayName =
        ReactTestUtils.withRenderedIntoDocument(HomePage(ToHomePage, MockRouterCtl[Page]())) { c =>
          c.displayName
        }
      displayName shouldBe "HomePage"
    }
  }
}
