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

package models

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Configuration

class CustomLinksSpec extends Specification with Mockito {
  "CustomLinks" should {
    "load link from config" in {
      val linkOne = CustomLink(false, true, "title 1", "href 1", "icon 1")
      val config = Map(
        "links" -> List(
          Map(
            "displayOnSidebar" -> linkOne.displayOnSidebar,
            "displayOnLoginScreen" -> linkOne.displayOnLoginScreen,
            "title" -> linkOne.title,
            "href" -> linkOne.href,
            "icon" -> linkOne.icon
          )
        )
      )
      val customLinks = CustomLinks(Configuration.from(config))
      customLinks.links must beEqualTo(List[CustomLink](linkOne))
    }

    "load multiple links from config" in {
      val linkOne = CustomLink(false, true, "title 1", "href 1", "icon 1")
      val linkTwo = CustomLink(true, false, "title 2", "href 2", "icon 2")
      val config = Map(
        "links" -> List(
          Map(
            "displayOnSidebar" -> linkOne.displayOnSidebar,
            "displayOnLoginScreen" -> linkOne.displayOnLoginScreen,
            "title" -> linkOne.title,
            "href" -> linkOne.href,
            "icon" -> linkOne.icon
          ),
          Map(
            "displayOnSidebar" -> linkTwo.displayOnSidebar,
            "displayOnLoginScreen" -> linkTwo.displayOnLoginScreen,
            "title" -> linkTwo.title,
            "href" -> linkTwo.href,
            "icon" -> linkTwo.icon
          )
        )
      )
      val customLinks = CustomLinks(Configuration.from(config))
      customLinks.links must beEqualTo(List[CustomLink](linkOne, linkTwo))
    }

    "load empty list if no links in config" in {
      val customLinks = CustomLinks(Configuration.from(Map()))
      customLinks.links must beEqualTo(List[CustomLink]())
    }
  }
}
