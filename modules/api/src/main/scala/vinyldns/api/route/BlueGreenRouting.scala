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

package vinyldns.api.route

import akka.http.scaladsl.server.{Directives, Route}

/**
  * Used for deployment, determines the current color of this node / cluster
  *
  * When we deploy, we will set the color in the config file based on the group of the server (blue or green).
  *
  * We will have 2 back-ends in HA Proxy; one for the blue servers and another for the green servers.
  * When we release an update, we will first query HA Proxy to determine the "active" color.  Then,
  * we will deploy the upgrade to the "other" / non-active color servers.
  */
trait BlueGreenRoute extends Directives {

  def colorRoute(nextColor: String): Route =
    (get & path("color")) {
      complete(nextColor)
    }
}
