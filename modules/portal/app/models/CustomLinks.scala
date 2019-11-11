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

import com.typesafe.config.Config
import play.api.{ConfigLoader, Configuration}

import scala.collection.JavaConverters._

case class CustomLinks(private val config: Configuration) {
  val links: List[CustomLink] =
    config.getOptional[List[CustomLink]]("links").getOrElse(List[CustomLink]())

  implicit def customLinksLoader: ConfigLoader[List[CustomLink]] =
    new ConfigLoader[List[CustomLink]] {
      def load(config: Config, path: String): List[CustomLink] = {
        val links = config.getConfigList(path).asScala.map { linkConfig =>
          val displayOnSidebar = linkConfig.getBoolean("displayOnSidebar")
          val displayOnLoginScreen = linkConfig.getBoolean("displayOnLoginScreen")
          val title = linkConfig.getString("title")
          val href = linkConfig.getString("href")
          val icon = linkConfig.getString("icon")
          CustomLink(displayOnSidebar, displayOnLoginScreen, title, href, icon)
        }
        links.toList
      }
    }
}

case class CustomLink(
    displayOnSidebar: Boolean,
    displayOnLoginScreen: Boolean,
    title: String,
    href: String,
    icon: String
)
