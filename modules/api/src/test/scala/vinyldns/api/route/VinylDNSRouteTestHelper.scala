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
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, extractUnmatchedPath}
import akka.http.scaladsl.server.{MalformedRequestContentRejection, RejectionHandler}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.json4s.MappingException

trait VinylDNSRouteTestHelper { this: ScalatestRouteTest =>
  import scala.concurrent.duration._
  import akka.http.scaladsl.testkit.RouteTestTimeout

  implicit val testTimeout = RouteTestTimeout(10.seconds)

  implicit def validationRejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case MalformedRequestContentRejection(msg, MappingException(_, _)) =>
          complete(
            HttpResponse(
              status = StatusCodes.BadRequest,
              entity = HttpEntity(ContentTypes.`application/json`, msg)
            )
          )
      }
      .handleNotFound {
        extractUnmatchedPath { p =>
          complete((StatusCodes.NotFound, s"The requested path [$p] does not exist."))
        }
      }
      .result()
}
