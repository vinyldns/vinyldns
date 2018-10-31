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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout

import scala.concurrent.duration._

trait PingRoute extends Directives {

  val pingRoute: Route = (get & path("ping")) {
    complete("PONG")
  }
}

trait HealthCheckRoute extends Directives {

  val healthService: HealthService

  implicit val healthCheckTimeout = Timeout(5.seconds)

  // perform a query against, fail with an ok if we can get zones from the zone manager
  val healthCheckRoute =
    (get & path("health")) {
      onSuccess(healthService.checkHealth().unsafeToFuture()) {
        case Right(_) =>
          complete(StatusCodes.OK)
        case Left(e) => failWith(e)
      }
    }

}
