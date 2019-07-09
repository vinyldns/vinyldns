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
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import vinyldns.api.domain.zone.ZoneServiceAlgebra

class GenericRoutingSpec
    extends WordSpec
    with ScalatestRouteTest
    with ZoneRoute
    with VinylDNSJsonProtocol
    with VinylDNSDirectives
    with MockitoSugar
    with Matchers {

  val vinylDNSAuthenticator: VinylDNSAuthenticator = mock[VinylDNSAuthenticator]
  val zoneService: ZoneServiceAlgebra = mock[ZoneServiceAlgebra]

  "GET" should {
    "return 405 MethodNotAllowed if route doesn't exist" in {
      Get("/no-existo") ~> Route.seal(zoneRoute) ~> check {
        response.status shouldBe StatusCodes.MethodNotAllowed
      }
    }
  }
}
