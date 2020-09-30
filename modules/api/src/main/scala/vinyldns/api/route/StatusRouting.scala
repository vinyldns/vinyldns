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
import akka.http.scaladsl.server.Directives
import akka.util.Timeout
import cats.effect.IO
import fs2.concurrent.SignallingRef
import vinyldns.api.VinylDNSConfig

import scala.concurrent.duration._

case class CurrentStatus(
    processingDisabled: Boolean,
    color: String,
    keyName: String,
    version: String
)

object CurrentStatus {
  val color = VinylDNSConfig.vinyldnsConfig.getString("color")
  val vinyldnsKeyName = "vinyldns."
  val version = VinylDNSConfig.vinyldnsConfig.getString("version")
}

trait StatusRoute extends Directives {
  this: VinylDNSJsonProtocol =>
  import CurrentStatus._

  implicit val timeout = Timeout(10.seconds)

  def processingDisabled: SignallingRef[IO, Boolean]

  val statusRoute =
    (get & path("status")) {
      onSuccess(processingDisabled.get.unsafeToFuture()) { isProcessingDisabled =>
        complete(
          StatusCodes.OK,
          CurrentStatus(isProcessingDisabled, color, vinyldnsKeyName, version)
        )
      }
    } ~
      (post & path("status")) {
        parameters("processingDisabled".as[Boolean]) { isProcessingDisabled =>
          onSuccess(processingDisabled.set(isProcessingDisabled).unsafeToFuture()) {
            complete(
              StatusCodes.OK,
              CurrentStatus(isProcessingDisabled, color, vinyldnsKeyName, version)
            )
          }
        }
      }
}
