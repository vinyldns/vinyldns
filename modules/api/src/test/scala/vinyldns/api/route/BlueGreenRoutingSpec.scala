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

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BlueGreenRoutingSpec
    extends AnyWordSpec
    with ScalatestRouteTest
    with BlueGreenRoute
    with Matchers {

  def actorRefFactory: ActorSystem = system

  "GET color" should {
    "return blue" in {
      Get("/color") ~> colorRoute("blue") ~> check {
        response.status shouldBe StatusCodes.OK

        // set in the application.conf in src/test/resources
        responseAs[String] shouldBe "blue"
      }
    }
  }
}
